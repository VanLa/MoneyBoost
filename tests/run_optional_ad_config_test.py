"""Run the compiled SDK manager against missing/blank configurations and generic Native slots."""
from pathlib import Path
import runpy, subprocess
b = runpy.run_path(str(Path(__file__).with_name('run_ump_gate_test.py')))
p, j = b['p'], b['java_home']
cp = str(p/'classes')+':'+b['cp']
files = {
'android/content/Context.java': 'package android.content; public class Context {}',
'android/app/Activity.java': '''package android.app;
public class Activity extends android.content.Context { public <T extends android.view.View> T findViewById(int id){return null;}
 public android.content.res.Resources getResources(){return new android.content.res.Resources();} }''',
'android/content/res/Resources.java': '''package android.content.res; public class Resources {
 public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();} }''',
'android/util/DisplayMetrics.java': '''package android.util; public class DisplayMetrics {public float density=1;public int widthPixels=390,heightPixels=844;}''',
'com/joyboost/moneyboost/MoneyBoostBannerAdapter.java': '''package com.joyboost.moneyboost;
public class MoneyBoostBannerAdapter {
 public static int created; public android.app.Activity MoneyBoost_activity; public String MoneyBoost_ad_unit;public boolean visible;
 public MoneyBoostBannerAdapter(){created++;}public void MoneyBoostInitBannerAdapter(){} public void MoneyBoostOnDestroy(){visible=false;}
 public void MoneyBoostLoadBannerView(){}public void MoneyBoostLoadCollapsibleBannerView(){}
 public boolean MoneyBoostIsLoaded(){return true;} public boolean MoneyBoostIsVisible(){return visible;}
 public void MoneyBoostShowBannerView(){visible=true;}public void MoneyBoostHideBannerView(){visible=false;}
 public int MoneyBoostGetBannerReserveHeightPx(){return 80;}
}''',
'com/joyboost/moneyboost/MoneyBoostNativeAdapter.java': '''package com.joyboost.moneyboost;
final class MoneyBoostNativeAdapter {
 final String unit;static int created,syncs;boolean loaded;
 MoneyBoostNativeAdapter(android.app.Activity a,String placement,String unit){this.unit=unit;created++;}
 void load(){loaded=true;}boolean isReady(){return loaded;}void show(float x,float y,float w,float h){}
 void hide(){}void destroy(){}void sync(){syncs++;}
}''',
'com/joyboost/moneyboost/OptionalAdConfigTest.java': '''package com.joyboost.moneyboost;
import java.lang.reflect.*;import android.os.Bundle;import java.util.*;
public class OptionalAdConfigTest {
 static void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 static void set(Object m,String n,Object v)throws Exception{Field f=m.getClass().getDeclaredField(n);f.setAccessible(true);f.set(m,v);}
 static Object get(Object m,String n)throws Exception{Field f=m.getClass().getDeclaredField(n);f.setAccessible(true);return f.get(m);}
 static Object call(Object m,String n,Class<?>[] types,Object... args)throws Exception{Method f=MoneyBoostMediationManager.class.getDeclaredMethod(n,types);f.setAccessible(true);return f.invoke(m,args);}
 public static void main(String[] args)throws Exception {
 MoneyBoostMediationManager m=MoneyBoostMediationManager.getInstance();
 Bundle config=new Bundle();
 for(String value:new String[]{null,"","  ","\\t\\n"}) {
  config.putString("unit",value);
  check(call(null,"MoneyBoostReadAdUnit",new Class[]{Bundle.class,String.class},config,"unit").equals(""),"Blank not disabled");
 }
 check(call(null,"MoneyBoostReadAdUnit",new Class[]{Bundle.class,String.class},config,"missing").equals(""),"Missing not disabled");
 check(call(null,"MoneyBoostReadAdUnit",new Class[]{Bundle.class,String.class},null,"missing").equals(""),"Null metadata not disabled");
 config.putString("unit","  real-id  ");check(call(null,"MoneyBoostReadAdUnit",new Class[]{Bundle.class,String.class},config,"unit").equals("real-id"),"ID not trimmed");
 for(String value:new String[]{null,""," ; ; "}) {
  config.putString("MoneyBoost_NATIVE_ID",value);
  String[] units=(String[])call(null,"MoneyBoostReadNativeAdUnits",new Class[]{Bundle.class},config);
  for(String unit:units)check(unit.isEmpty(),"Empty Native slot enabled");
 }
 config.putString("MoneyBoost_NATIVE_ID"," first ; ; third ; ");
 String[] units=(String[])call(null,"MoneyBoostReadNativeAdUnits",new Class[]{Bundle.class},config);
 check(Arrays.equals(units,new String[]{"first","","third",""}),"Native indices shifted or whitespace retained");
 android.app.Activity activity=new android.app.Activity();
 call(m,"MoneyBoostInitAdAdapters",new Class[]{android.app.Activity.class},activity);
 check(MoneyBoostBannerAdapter.created==0&&MoneyBoostInterstitialAdapter.created==0&&MoneyBoostRewardVideoAdapter.created==0&&MoneyBoostSplashAdapter.created==0,"Disabled format constructed an adapter");
 for(String field:new String[]{"banner","inter","reward","splash"})check(get(m,"MoneyBoost_"+field+"Adapter")==null,"Disabled adapter exists");
 set(m,"MoneyBoost_interKey","inter-id");call(m,"MoneyBoostInitAdAdapters",new Class[]{android.app.Activity.class},activity);
 check(MoneyBoostInterstitialAdapter.created==1&&MoneyBoostBannerAdapter.created==0&&MoneyBoostRewardVideoAdapter.created==0&&MoneyBoostSplashAdapter.created==0,"Single enabled format affected other types");
 set(m,"MoneyBoost_interKey","");call(m,"MoneyBoostInitAdAdapters",new Class[]{android.app.Activity.class},activity);
 check(get(m,"MoneyBoost_interAdapter")==null,"Disabling format retained old adapter");
 set(m,"MoneyBoost_activity",activity);set(m,"MoneyBoost_adsPermitted",true);set(m,"MoneyBoost_isInitSuccess",true);set(m,"MoneyBoost_isAppForeground",true);set(m,"MoneyBoost_needPopAD",true);set(m,"MoneyBoost_isDestroyed",false);
 m.MoneyBoostShowCollapsibleBannerView("any_scene");check(!m.MoneyBoostHasCollapsibleRequest()&&MoneyBoostBannerAdapter.created==0,"Blank fold started a request");
 set(m,"MoneyBoost_collapsibleBannerKey","fold-id");m.MoneyBoostShowCollapsibleBannerView("any_scene");
 check(m.MoneyBoostHasCollapsibleRequest()&&m.MoneyBoostDebugIsCollapsibleShowing(),"Fold depends on ordinary Banner");
 m.MoneyBoostHideCollapsibleBannerView();check(!m.MoneyBoostHasCollapsibleRequest(),"Fold not released without Banner");
 set(m,"MoneyBoost_nativeKeys",units);
 for(int index:new int[]{-1,1,3,4})m.MoneyBoostLoadNativeAd("disabled_"+index,index);
 m.MoneyBoostLoadNativeAd(null,0);m.MoneyBoostLoadNativeAd(" ",0);
 check(MoneyBoostNativeAdapter.created==0,"Empty/out-of-range Native created a pool");
 m.MoneyBoostLoadNativeAd("shop",0);m.MoneyBoostLoadNativeAd("inventory",2);m.MoneyBoostLoadNativeAd("another_scene",0);m.MoneyBoostLoadNativeAd("shop",0);
 check(MoneyBoostNativeAdapter.created==3,"Generic scenes or shared unit registration failed");
 Map<String,MoneyBoostNativeAdapter> nativeAds=(Map<String,MoneyBoostNativeAdapter>)get(m,"MoneyBoost_nativeAds");
 check(nativeAds.get("shop").unit.equals("first")&&nativeAds.get("inventory").unit.equals("third"),"Wrong Native unit selected");
 check(m.MoneyBoostIsNativeAdReady("shop")&&!m.MoneyBoostIsNativeAdReady("disabled_1")&&!m.MoneyBoostIsNativeAdReady(null),"Native readiness incorrect");
 call(m,"MoneyBoostSyncBannerVisibility",new Class[]{});check(MoneyBoostNativeAdapter.syncs>=3,"Native sync depends on Banner");
 check(!(Boolean)call(m,"MoneyBoostCanShowColdStartSplash",new Class[]{}),"Disabled splash may wait for inventory");
 check(!(Boolean)call(m,"MoneyBoostCanShowResumeSplash",new Class[]{}),"Disabled splash may start resume flow");
 System.out.println("OPTIONAL AD CONFIG PASS: missing/null/blank/whitespace IDs, zero disabled adapters, independent fold/Native, generic Native scenes and stable semicolon indices, disabled splash flow.");
 }
}'''
}
for field,kind,init,listener in [('inter','Interstitial','Interstitial','MoneyBoost_InterstitialListener'),('reward','RewardVideo','RewardVideo','MoneyBoost_rewardVideoListener'),('splash','Splash','Splash','MoneyBoost_splashListener')]:
 listener_type={'inter':'MoneyBoostInterstitialListener','reward':'MoneyBoostRewardVideoListener','splash':'MoneyBoostSplashListener'}[field]
 files[f'com/joyboost/moneyboost/MoneyBoost{kind}Adapter.java']=f'''package com.joyboost.moneyboost;
 public class MoneyBoost{kind}Adapter {{ public static int created; public android.app.Activity MoneyBoost_activity;public String MoneyBoost_ad_unit;
 public {listener_type} {listener};public MoneyBoost{kind}Adapter(){{created++;}}
 public void MoneyBoostInit{init}Adapter(){{}}public void MoneyBoostOnDestroy(){{}}public boolean MoneyBoostIsShowing(){{return false;}} }}'''
for name,body in files.items():
 f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(body)
subprocess.run([str(j/'bin/javac'),'-cp',cp,'-d',str(p/'classes'),*[str(p/n) for n in files]],check=True)
subprocess.run([str(j/'bin/java'),'-cp',cp,'com.joyboost.moneyboost.OptionalAdConfigTest'],check=True)
