"""Run the real adapters against deterministic Android/GMA transport shims (no live ads)."""
from pathlib import Path
import subprocess, tempfile
root=Path(__file__).resolve().parent.parent
src=root/'frpianosdk/src/main/java/com/joyboost/moneyboost'
p=Path(tempfile.mkdtemp(prefix='moneyboost-preload-'))
java=Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin')
stubs={
'android/app/Activity.java': '''package android.app; public class Activity { public static boolean defer; public static final java.util.Queue<Runnable> queue=new java.util.ArrayDeque<>(); public boolean isFinishing(){return false;} public boolean isDestroyed(){return false;} public void runOnUiThread(Runnable r){if(defer)queue.add(r);else r.run();} }''',
'android/os/Looper.java':'package android.os; public class Looper { static final Looper MAIN=new Looper(); public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return MAIN;} }',
'android/os/Handler.java':'package android.os; public class Handler { public Handler(Looper l){} public void post(Runnable r){r.run();} }',
'android/util/Log.java':'package android.util; public class Log { public static int i(String a,String b){return 0;} public static int w(String a,String b){return 0;} }',
'androidx/annotation/NonNull.java':'package androidx.annotation; public @interface NonNull {}',
'com/joyboost/moneyboost/MoneyBoostMediationManager.java': 'package com.joyboost.moneyboost; public class MoneyBoostMediationManager { public static boolean allowed=true; static final MoneyBoostMediationManager m=new MoneyBoostMediationManager(); public static MoneyBoostMediationManager getInstance(){return m;} public boolean MoneyBoostCanLoadAds(){return allowed;} }',
'com/joyboost/moneyboost/MoneyBoostToolsManager.java':'package com.joyboost.moneyboost; public class MoneyBoostToolsManager { public static MoneyBoostToolsManager instance(){return new MoneyBoostToolsManager();} public void MoneyBoostLogWithDebug(String a,String b){} }',
'com/joyboost/moneyboost/MoneyBoostAdDiagnostics.java':'package com.joyboost.moneyboost; public class MoneyBoostAdDiagnostics { public static void load(String a,String b,String c,Object d){} }',
'com/joyboost/moneyboost/MoneyBoostFirebaseManager.java':'package com.joyboost.moneyboost; public class MoneyBoostFirebaseManager { public static MoneyBoostFirebaseManager instance(){return new MoneyBoostFirebaseManager();} public void MoneyBoostLogFirebaseRevenue(Double a,String b,String c,String d){} }',
}
gma='com/google/android/libraries/ads/mobile/sdk/'
common={
'AdRequest':'public class AdRequest { public static class Builder { public Builder(String id){} public AdRequest build(){return new AdRequest();} } }',
'PreloadConfiguration':'public class PreloadConfiguration { public PreloadConfiguration(AdRequest r){} public PreloadConfiguration(AdRequest r,Integer n){} }',
'PreloadCallback':'public interface PreloadCallback { void onAdPreloaded(String id,ResponseInfo r); void onAdsExhausted(String id); void onAdFailedToPreload(String id,LoadAdError e); }',
'ResponseInfo':'public class ResponseInfo { public AdSourceResponseInfo getLoadedAdSourceResponseInfo(){return null;} public String getAdapterClassName(){return null;} }',
'AdSourceResponseInfo':'public class AdSourceResponseInfo { public String getName(){return "test";} }',
'AdValue':'public class AdValue { public long getValueMicros(){return 1000;} }',
'LoadAdError':'public class LoadAdError { public int getCode(){return 3;} public String getMessage(){return "test no fill";} }',
'FullScreenContentError':'public class FullScreenContentError { public String getMessage(){return "test show failure";} }',
}
for name,body in common.items(): stubs[gma+'common/'+name+'.java']='package com.google.android.libraries.ads.mobile.sdk.common; '+body
for pkg,ad in [('interstitial','InterstitialAd'),('rewarded','RewardedAd'),('appopen','AppOpenAd')]:
 ns='com.google.android.libraries.ads.mobile.sdk.'+pkg
 stubs[gma+pkg+'/'+ad+'EventCallback.java']='package '+ns+'''; import com.google.android.libraries.ads.mobile.sdk.common.*; public interface '''+ad+'''EventCallback {
 void onAdShowedFullScreenContent(); void onAdDismissedFullScreenContent(); void onAdFailedToShowFullScreenContent(FullScreenContentError e); void onAdClicked(); void onAdPaid(AdValue v); }'''
 show='public void show(android.app.Activity a){shows++;if(throwOnShow)throw new IllegalStateException("show transport failed");}' if pkg!='rewarded' else 'public OnUserEarnedRewardListener earned; public void show(android.app.Activity a,OnUserEarnedRewardListener l){shows++;earned=l;if(throwOnShow)throw new IllegalStateException("show transport failed");}'
 stubs[gma+pkg+'/'+ad+'.java']='package '+ns+'; import com.google.android.libraries.ads.mobile.sdk.common.*; public class '+ad+' { public static int shows; public static boolean throwOnShow; public '+ad+'EventCallback callback; public void setAdEventCallback('+ad+'EventCallback c){callback=c;} public ResponseInfo getResponseInfo(){return null;} public void destroy(){} '+show+' }'
 stubs[gma+pkg+'/'+ad+'Preloader.java']='package '+ns+'; import com.google.android.libraries.ads.mobile.sdk.common.*; public class '+ad+'Preloader { public static int starts,polls,destroys; public static boolean throwOnStart; public static boolean available; public static PreloadCallback callback; public static '+ad+' last; public static boolean start(String id,PreloadConfiguration c,PreloadCallback cb){starts++;if(throwOnStart)throw new IllegalStateException("start transport failed");callback=cb;return true;} public static boolean isAdAvailable(String id){return available;} public static '+ad+' pollAd(String id){polls++;if(!available)return null;available=false;last=new '+ad+'();return last;} public static void destroy(String id){destroys++;available=false;} }'
stubs[gma+'rewarded/OnUserEarnedRewardListener.java']='package com.google.android.libraries.ads.mobile.sdk.rewarded; public interface OnUserEarnedRewardListener { void onUserEarnedReward(RewardItem r); }'
stubs[gma+'rewarded/RewardItem.java']='package com.google.android.libraries.ads.mobile.sdk.rewarded; public class RewardItem {}'
test='''package com.joyboost.moneyboost;
import java.lang.reflect.*;
import com.google.android.libraries.ads.mobile.sdk.common.*;
public class PreloadingTest {
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
 static int count(Class<?> p,String name)throws Exception{return p.getField(name).getInt(null);}
 public static void main(String[] args)throws Exception {'''
for adapter,pkg,ad,load,show,ready in [
 ('MoneyBoostInterstitialAdapter','interstitial','InterstitialAd','MoneyBoostLoadInterstitialAd','MoneyBoostShowInterstitialAd','MoneyBoostIsReady'),
 ('MoneyBoostRewardVideoAdapter','rewarded','RewardedAd','MoneyBoostLoadRewardVideoAd','MoneyBoostShowRewardVideoAd','MoneyBoostIsReady'),
 ('MoneyBoostSplashAdapter','appopen','AppOpenAd','MoneyBoostLoadSplashAd','MoneyBoostShowSplashAd','MoneyBoostIsReady')]:
 test+='''{
 Class<?> cls=Class.forName("com.joyboost.moneyboost.ADAPTER");Object a=cls.getConstructor().newInstance();
 cls.getField("MoneyBoost_activity").set(a,new android.app.Activity());cls.getField("MoneyBoost_ad_unit").set(a,"test-unit");
 Class<?> p=Class.forName("com.google.android.libraries.ads.mobile.sdk.PKG.ADPreloader");
 Method load=cls.getMethod("LOAD"),show=cls.getMethod("SHOW"),ready=cls.getMethod("READY");
 MoneyBoostMediationManager.allowed=false;load.invoke(a);check(count(p,"starts")==0,"load without consent");
 MoneyBoostMediationManager.allowed=true;load.invoke(a);load.invoke(a);check(count(p,"starts")==1,"preloader started twice");
 PreloadCallback cb=(PreloadCallback)p.getField("callback").get(null);cb.onAdFailedToPreload("test-unit",new LoadAdError());
 cb.onAdsExhausted("test-unit");load.invoke(a);check(count(p,"starts")==1,"restarted after no fill/exhaustion");
 check(count(p,"polls")==0,"polled before show");p.getField("available").setBoolean(null,true);
 check((Boolean)ready.invoke(a),"readiness not from SDK inventory");
 android.app.Activity.defer=true;show.invoke(a);check(count(p,"polls")==0,"polled before UI dispatch");
 MoneyBoostMediationManager.allowed=false;android.app.Activity.queue.remove().run();check(count(p,"polls")==0,"polled after revoked consent");
 MoneyBoostMediationManager.allowed=true;android.app.Activity.defer=false;show.invoke(a);check(count(p,"polls")==1,"show did not poll");
 Object ad=p.getField("last").get(null);
 '''.replace('ADAPTER',adapter).replace('PKG',pkg).replace('ADPreloader',ad+'Preloader').replace('LOAD',load).replace('SHOW',show).replace('READY',ready)
 if pkg=='rewarded':
  test+='''final int[] rewards={0}; ((MoneyBoostRewardVideoAdapter)a).MoneyBoost_rewardVideoListener=new MoneyBoostRewardVideoListener(){
 public void MoneyBoostOnRewardVideoAdDisplayed(){} public void MoneyBoostOnRewardVideoAdClosed(){} public void MoneyBoostOnRewardVideoAdFailedToShow(){} public void MoneyBoostOnRewardVideoAdClicked(){} public void MoneyBoostOnRewardVideoAdCompleted(){rewards[0]++;}};
 com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd r=(com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd)ad;
 r.earned.onUserEarnedReward(null);r.earned.onUserEarnedReward(null);check(rewards[0]==1,"reward delivered twice");
 '''
 test+='''p.getField("available").setBoolean(null,true);show.invoke(a);check(count(p,"polls")==1,"consumed second ad while showing");
 cls.getMethod("MoneyBoostOnDestroy").invoke(a);check(count(p,"destroys")==1,"inventory not destroyed");check(!(Boolean)ready.invoke(a),"stale availability after destroy");
 }
'''
for adapter,pkg,ad,load,show,listener in [
 ('MoneyBoostInterstitialAdapter','interstitial','InterstitialAd','MoneyBoostLoadInterstitialAd','MoneyBoostShowInterstitialAd','MoneyBoost_InterstitialListener'),
 ('MoneyBoostRewardVideoAdapter','rewarded','RewardedAd','MoneyBoostLoadRewardVideoAd','MoneyBoostShowRewardVideoAd','MoneyBoost_rewardVideoListener'),
 ('MoneyBoostSplashAdapter','appopen','AppOpenAd','MoneyBoostLoadSplashAd','MoneyBoostShowSplashAd','MoneyBoost_splashListener')]:
 test+='''{
 Class<?> cls=Class.forName("com.joyboost.moneyboost.ADAPTER");Object a=cls.getConstructor().newInstance();
 cls.getField("MoneyBoost_activity").set(a,new android.app.Activity());cls.getField("MoneyBoost_ad_unit").set(a,"test-unit");
 Class<?> p=Class.forName("com.google.android.libraries.ads.mobile.sdk.PKG.ADPreloader");
 Method load=cls.getMethod("LOAD"),show=cls.getMethod("SHOW");
 int baseline=count(p,"starts");p.getField("throwOnStart").setBoolean(null,true);
 try{load.invoke(a);}catch(InvocationTargetException expected){}
 p.getField("throwOnStart").setBoolean(null,false);load.invoke(a);
 check(count(p,"starts")==baseline+2,"start exception permanently blocks registration: ADAPTER");
 Field listener=cls.getField("LISTENER");final int[] terminal={0},rewards={0};
 listener.set(a,Proxy.newProxyInstance(cls.getClassLoader(),new Class[]{listener.getType()},(o,m,v)->{
  if(m.getName().endsWith("Closed")||m.getName().endsWith("FailedToShow"))terminal[0]++;
  if(m.getName().endsWith("Completed"))rewards[0]++;return null;
 }));
 Class<?> adClass=Class.forName("com.google.android.libraries.ads.mobile.sdk.PKG.AD");
 p.getField("available").setBoolean(null,true);adClass.getField("throwOnShow").setBoolean(null,true);show.invoke(a);
 Object failedAd=p.getField("last").get(null);check(terminal[0]==1,"show exception not notified");
 adClass.getField("throwOnShow").setBoolean(null,false);p.getField("available").setBoolean(null,true);show.invoke(a);
 Object callback=adClass.getField("callback").get(failedAd);
 Class<?> event=Class.forName("com.google.android.libraries.ads.mobile.sdk.PKG.ADEventCallback");
 event.getMethod("onAdDismissedFullScreenContent").invoke(callback);
 event.getMethod("onAdShowedFullScreenContent").invoke(callback);
 check(terminal[0]==1,"late callback closed twice: ADAPTER");
 check((Boolean)cls.getMethod("MoneyBoostIsShowing").invoke(a),"old callback cleared new show state");
 '''.replace('ADAPTER',adapter).replace('PKG',pkg).replace('ADPreloader',ad+'Preloader').replace('ADEventCallback',ad+'EventCallback').replace('PKG.AD','PKG.'+ad).replace('sdk.'+pkg+'.AD"','sdk.'+pkg+'.'+ad+'"').replace('LOAD',load).replace('SHOW',show).replace('LISTENER',listener)
 if pkg=='rewarded':
  test+='''((com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd)failedAd).earned.onUserEarnedReward(null);check(rewards[0]==0,"failed ad granted reward");'''
 test+='cls.getMethod("MoneyBoostOnDestroy").invoke(a);}'
test+='System.out.println("PRELOADING PASS: all 3 formats start once, SDK readiness, no custom retry/cache, consent at queued show, single in-flight ad, reward once, destroy, start-exception recovery, show-exception/late-callback isolation.");}}'
stubs['com/joyboost/moneyboost/PreloadingTest.java']=test
for name,body in stubs.items():
 f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
files=[src/name for name in ['MoneyBoostInterstitialAdapter.java','MoneyBoostRewardVideoAdapter.java','MoneyBoostSplashAdapter.java','MoneyBoostInterstitialListener.java','MoneyBoostRewardVideoListener.java','MoneyBoostSplashListener.java']]
subprocess.run([str(java/'javac'),'-d',str(p/'classes'),*[str(p/n) for n in stubs],*map(str,files)],check=True)
subprocess.run([str(java/'java'),'-cp',str(p/'classes'),'com.joyboost.moneyboost.PreloadingTest'],check=True)
