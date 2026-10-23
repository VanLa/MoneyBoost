"""Exercise the real Native adapter with deterministic Android/GMA transport shims."""
from pathlib import Path
import tempfile, subprocess
root=Path(__file__).resolve().parent.parent
src=root/'frpianosdk/src/main/java/com/joyboost/moneyboost'
out=Path(tempfile.mkdtemp(prefix='moneyboost-native-preload-'))
j=Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin')
stubs={
'android/graphics/drawable/Drawable.java':'package android.graphics.drawable;public class Drawable{}',
'android/graphics/drawable/LayerDrawable.java':'package android.graphics.drawable;public class LayerDrawable extends Drawable{public LayerDrawable(Drawable[] layers){}public void setLayerInset(int index,int l,int t,int r,int b){}}',
'android/os/SystemClock.java': 'package android.os; public class SystemClock { public static long now; public static long elapsedRealtime(){return now;} }',
'android/os/Bundle.java': 'package android.os; public class Bundle extends java.util.HashMap<String,Object>{public void putString(String k,String v){put(k,v);} }',
'android/util/Log.java': 'package android.util; public class Log { public static int w(String t,String m,Throwable e){return 0;} }',
'android/util/DisplayMetrics.java':'package android.util; public class DisplayMetrics{public float density=1;} ',
'android/content/res/Resources.java':'package android.content.res; public class Resources{public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();}}',
'android/R.java':'package android; public class R {public static class id{public static int content=1;}}',
'android/app/Activity.java':'package android.app; public class Activity { public android.widget.FrameLayout root=new android.widget.FrameLayout(this); public <T> T findViewById(int id){return (T)root;} public android.content.res.Resources getResources(){return new android.content.res.Resources();} public void runOnUiThread(Runnable r){r.run();} }',
'android/graphics/Color.java':'package android.graphics; public class Color{public static int WHITE=1,DKGRAY=2;public static int rgb(int r,int g,int b){return 0;}}',
'android/graphics/Rect.java':'package android.graphics; public class Rect{public int left,top,right,bottom;public void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}',
'android/graphics/drawable/GradientDrawable.java':'package android.graphics.drawable; public class GradientDrawable extends Drawable{public enum Orientation{TOP_BOTTOM,TL_BR}public GradientDrawable(){}public GradientDrawable(Orientation o,int[] colors){}public void setColor(int x){} public void setCornerRadius(float x){}public void setStroke(int a,int b){}}',
'android/view/Gravity.java':'package android.view;public class Gravity{public static int TOP=1,LEFT=2,CENTER_VERTICAL=4,BOTTOM=8,RIGHT=16;}',
'android/view/ViewTreeObserver.java':'package android.view; public class ViewTreeObserver{public interface OnGlobalLayoutListener{void onGlobalLayout();}public void addOnGlobalLayoutListener(OnGlobalLayoutListener l){}public void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){}}',
'android/view/View.java':'''package android.view; public class View {public static int GONE=8,VISIBLE=0; public android.graphics.Rect clip;public void setClipBounds(android.graphics.Rect r){clip=r;}public float tx,ty;public float getTranslationX(){return tx;}public float getTranslationY(){return ty;}public void setTranslationX(float v){tx=v;}public void setTranslationY(float v){ty=v;}public int visibility;public boolean isShown(){return visibility==VISIBLE;}public void getLocationOnScreen(int[] at){at[0]=0;at[1]=0;} public ViewGroup parent;public ViewGroup.LayoutParams params;public void setVisibility(int v){visibility=v;}public int getWidth(){return 390;}public int getHeight(){return 844;}public ViewGroup.LayoutParams getLayoutParams(){return params;}public void setLayoutParams(ViewGroup.LayoutParams p){params=p;}public Object getParent(){return parent;}public void setBackground(Object x){}public ViewTreeObserver getViewTreeObserver(){return new ViewTreeObserver();}}''',
'android/view/ViewGroup.java':'''package android.view;public class ViewGroup extends View {public static class LayoutParams{public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}public void addView(View v,LayoutParams p){v.parent=this;v.params=p;}public void removeView(View v){v.parent=null;}}''',
'android/view/MotionEvent.java':'package android.view;public class MotionEvent{public static final int ACTION_DOWN=0;final float x,y;public MotionEvent(float x,float y){this.x=x;this.y=y;}public int getActionMasked(){return 0;}public float getX(){return x;}public float getY(){return y;}}',
'android/widget/FrameLayout.java':'package android.widget; public class FrameLayout extends android.view.ViewGroup{public FrameLayout(android.app.Activity a){}public boolean dispatchTouchEvent(android.view.MotionEvent e){return true;}public static class LayoutParams extends android.view.ViewGroup.LayoutParams{public int gravity,leftMargin,topMargin;public LayoutParams(int w,int h){super(w,h);}}}',
'android/widget/LinearLayout.java':'package android.widget;public class LinearLayout extends android.view.ViewGroup {public static int VERTICAL=1;public LinearLayout(android.app.Activity a){}public void setOrientation(int x){}public void setGravity(int x){}public void setPadding(int a,int b,int c,int d){}public void addView(android.view.View v){}public static class LayoutParams extends android.view.ViewGroup.LayoutParams{public LayoutParams(int w,int h){super(w,h);}public LayoutParams(int w,int h,float n){super(w,h);}}}',
'android/widget/TextView.java':'package android.widget; public class TextView extends android.view.View{public TextView(android.app.Activity a){}public void setText(String x){}public void setPadding(int a,int b,int c,int d){}public void setTextSize(float x){}public void setTextColor(int x){}}',
'android/widget/Button.java':'package android.widget;public class Button extends TextView{public Button(android.app.Activity a){super(a);}public void setAllCaps(boolean b){}}',
'android/widget/ImageView.java':'package android.widget;public class ImageView extends android.view.View{public ImageView(android.app.Activity a){}public enum ScaleType{FIT_CENTER}public void setImageDrawable(Object x){}}',
'com/joyboost/moneyboost/MoneyBoostMediationManager.java':'''package com.joyboost.moneyboost;public class MoneyBoostMediationManager {static final MoneyBoostMediationManager m=new MoneyBoostMediationManager();public static boolean allowed=true;public static MoneyBoostMediationManager getInstance(){return m;}int[] MoneyBoostGetNativeTileColors(){return new int[]{1,2,3};}MoneyBoostNativeStyle MoneyBoostGetNativeStyle(){return new MoneyBoostNativeStyle();}boolean MoneyBoostHasCollapsibleRequest(){return false;}boolean MoneyBoostShouldHideNativeForCollapsible(String p){return false;}boolean MoneyBoostCanShowNativeAds(){return allowed;}boolean MoneyBoostCanLoadAds(){return allowed;}int MoneyBoostGetNativeBottomReservePx(){return 80;}void MoneyBoostBringBannersToFront(){}}''',
'com/joyboost/moneyboost/MoneyBoostAdDiagnostics.java':'''package com.joyboost.moneyboost;public class MoneyBoostAdDiagnostics{static final java.util.List<String> events=new java.util.ArrayList<>();static final java.util.List<android.os.Bundle> params=new java.util.ArrayList<>();static void load(String a,String b,String c,Object d){}static String shortText(String s){return s;}static void record(String n,android.os.Bundle b){events.add(n);params.add(b);}}''',
'com/joyboost/moneyboost/MoneyBoostFirebaseManager.java':'package com.joyboost.moneyboost;public class MoneyBoostFirebaseManager{static int paid;static MoneyBoostFirebaseManager instance(){return new MoneyBoostFirebaseManager();}void MoneyBoostLogFirebaseRevenue(double n,String t,String s,String u){paid++;}}'
}
g='com/google/android/libraries/ads/mobile/sdk/'
for name,body in {
'LoadAdError':'public class LoadAdError{}',
'PreloadConfiguration':'public class PreloadConfiguration{public final Integer size;public PreloadConfiguration(Object r,Integer n){size=n;}}',
'PreloadCallback':'public interface PreloadCallback{void onAdPreloaded(String id,ResponseInfo i);void onAdFailedToPreload(String id,LoadAdError e);void onAdsExhausted(String id);}',
'ResponseInfo':'public class ResponseInfo{public AdSourceResponseInfo getLoadedAdSourceResponseInfo(){return new AdSourceResponseInfo();}}',
'AdSourceResponseInfo':'public class AdSourceResponseInfo{public String getName(){return "AdMob";}}',
'AdValue':'public class AdValue{public long getValueMicros(){return 1000;}}'
}.items():stubs[g+'common/'+name+'.java']='package com.google.android.libraries.ads.mobile.sdk.common;'+body
n='package com.google.android.libraries.ads.mobile.sdk.nativead;'
for name,body in {
'NativeAdRequest':'public class NativeAdRequest{public static class Builder{public Builder(String u,java.util.List<NativeAd.NativeAdType> t){}public NativeAdRequest build(){return new NativeAdRequest();}}}',
'NativeAd':'public class NativeAd{public enum NativeAdType{NATIVE}public int destroyed;public NativeAdEventCallback callback;public void setAdEventCallback(NativeAdEventCallback c){callback=c;}public void destroy(){destroyed++;}public com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo getResponseInfo(){return new com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo();}public Object getMediaContent(){return null;}public String getHeadline(){return "headline";}public String getCallToAction(){return "Install";}public Icon getIcon(){return null;}public static class Icon{public Object getDrawable(){return null;}}}',
'NativeAdEventCallback':'public interface NativeAdEventCallback{default void onAdPaid(com.google.android.libraries.ads.mobile.sdk.common.AdValue v){}default void onAdImpression(){}default void onAdClicked(){}}',
'NativeAdView':'public class NativeAdView extends android.widget.FrameLayout{public NativeAdView(android.app.Activity a){super(a);}public void setHeadlineView(Object v){}public void setIconView(Object v){}public void setCallToActionView(Object v){}public void registerNativeAd(NativeAd a,MediaView m){}public void destroy(){}}',
'MediaView':'public class MediaView extends android.view.View{public MediaView(android.app.Activity a){}public void setImageScaleType(Object o){}public void setMediaContent(Object o){}}',
'NativeAdLoadResult':'public interface NativeAdLoadResult {interface NativeAdLoadSuccessResult{}class NativeAdSuccess implements NativeAdLoadSuccessResult{public NativeAd ad;public NativeAdSuccess(NativeAd a){ad=a;}public NativeAd getAd(){return ad;}}class BannerAdSuccess implements NativeAdLoadSuccessResult{public NativeAd getAd(){return new NativeAd();}}class CustomNativeAdSuccess implements NativeAdLoadSuccessResult{public NativeAd getAd(){return new NativeAd();}}}',
'NativeAdPreloader':'''public class NativeAdPreloader{public static int starts,polls,destroys;public static boolean available,fail;public static String id;public static com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback callback;public static NativeAd last;public static boolean start(String i,com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration c,com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback cb){starts++;if(fail)throw new IllegalStateException();if(c.size!=1)throw new AssertionError("buffer");id=i;callback=cb;return true;}public static boolean isAdAvailable(String i){return available;}public static NativeAdLoadResult.NativeAdLoadSuccessResult pollAd(String i){polls++;if(!available)return null;available=false;last=new NativeAd();return new NativeAdLoadResult.NativeAdSuccess(last);}public static void destroy(String i){destroys++;available=false;}}'''
}.items():stubs[g+'nativead/'+name+'.java']=n+body
stubs['com/joyboost/moneyboost/NativePreloadTest.java']='''package com.joyboost.moneyboost;
import com.google.android.libraries.ads.mobile.sdk.nativead.*;
import com.google.android.libraries.ads.mobile.sdk.common.*;
public class NativePreloadTest {
static void check(boolean v,String m){if(!v)throw new AssertionError(m);}
public static void main(String[] args)throws Exception{
 MoneyBoostNativeAdapter a=new MoneyBoostNativeAdapter(new android.app.Activity(),"home","same-test-unit");
 MoneyBoostMediationManager.allowed=false;a.load();check(NativeAdPreloader.starts==0,"consent bypass");
 MoneyBoostMediationManager.allowed=true;NativeAdPreloader.fail=true;a.load();NativeAdPreloader.fail=false;a.load();a.load();check(NativeAdPreloader.starts==2,"registration rollback or duplicate start");
 String homeId=NativeAdPreloader.id;
 NativeAdPreloader.available=true;NativeAdPreloader.callback.onAdPreloaded(homeId,new ResponseInfo());check(NativeAdPreloader.polls==0,"polled before visible");check(a.isReady(),"ready inventory");
 a.show(0,0,1,.1f);check(NativeAdPreloader.polls==0,"polled clipped slot");
 a.show(0,.2f,1,.3f);check(NativeAdPreloader.polls==1,"visible slot did not poll");NativeAd first=NativeAdPreloader.last;
 first.callback.onAdImpression();first.callback.onAdImpression();first.callback.onAdClicked();first.callback.onAdPaid(new AdValue());
 check(MoneyBoostAdDiagnostics.events.equals(java.util.List.of("native_show","native_click")),"native impression duplicate/click missing");
 check(MoneyBoostAdDiagnostics.params.get(0).get("scene").equals("native_home"),"placement missing");
 check(!MoneyBoostAdDiagnostics.params.get(0).containsKey("value"),"count event contains revenue");
 a.hide();a.show(0,.2f,1,.3f);check(NativeAdPreloader.polls==1,"hide/show replaced ad");
 NativeAdPreloader.available=true;android.os.SystemClock.now=55*60*1000L;a.show(0,.2f,1,.3f);check(first.destroyed==1&&NativeAdPreloader.polls==2,"expired displayed ad not released");
 first.callback.onAdImpression();first.callback.onAdClicked();first.callback.onAdPaid(new AdValue());check(MoneyBoostAdDiagnostics.events.size()==2&&MoneyBoostFirebaseManager.paid==1,"stale callback accepted");
 java.lang.reflect.Field viewField=MoneyBoostNativeAdapter.class.getDeclaredField("view");viewField.setAccessible(true);
 android.view.View rendered=(android.view.View)viewField.get(a);
 int pollsBeforeScroll=NativeAdPreloader.polls;
 a.show(0,.3f,1,.3f);
 check(((android.view.View)rendered.getParent()).getTranslationY()==Math.round(.3f*844),"Native did not follow scrolled slot");
 a.show(0,-.1f,1,.3f);check(rendered.visibility==android.view.View.GONE,"clipped Native left a floating overlay");
 a.show(0,.2f,1,.3f);check(rendered.visibility==android.view.View.VISIBLE&&NativeAdPreloader.polls==pollsBeforeScroll,"scroll return replaced ad or stayed hidden");
 MoneyBoostNativeStyle style=new MoneyBoostNativeStyle(0,1,2,3,4,Float.NaN,999,true,-5,999);
 check(style.cornerRadius==14&&style.buttonRadius==48&&style.headlineSize==12&&style.buttonSize==20,"style bounds/NaN invalid");
 a.applyStyle(style);check(NativeAdPreloader.polls==pollsBeforeScroll&&NativeAdPreloader.last.destroyed==0,"restyling consumed/destroyed ad");
 android.widget.FrameLayout container=new android.widget.FrameLayout(new android.app.Activity());
 a.setContainer(container);check(((android.view.View)rendered.getParent()).getParent()==container,"Android list ad not attached to scroll slot");
 a.show(0,.25f,1,.3f);check(NativeAdPreloader.polls==pollsBeforeScroll,"attaching list slot consumed another ad");
 a.setContainer(null);check(((android.view.View)rendered.getParent()).getParent()!=container,"list container did not detach");
 NativeAd second=NativeAdPreloader.last;a.destroy();a.destroy();second.callback.onAdImpression();second.callback.onAdPaid(new AdValue());check(!a.isReady()&&NativeAdPreloader.destroys==1&&second.destroyed==1,"destroy not idempotent");
 int starts=NativeAdPreloader.starts;NativeAdPreloader.callback.onAdFailedToPreload(homeId,new LoadAdError());a.load();check(NativeAdPreloader.starts==starts,"destroyed callback restarted");
 MoneyBoostNativeAdapter b=new MoneyBoostNativeAdapter(new android.app.Activity(),"album","same-test-unit");b.load();check(!homeId.equals(NativeAdPreloader.id),"home/album test-unit pool collision");
 starts=NativeAdPreloader.starts;NativeAdPreloader.callback.onAdFailedToPreload(NativeAdPreloader.id,new LoadAdError());NativeAdPreloader.callback.onAdsExhausted(NativeAdPreloader.id);b.load();check(NativeAdPreloader.starts==starts,"manual retry after no-fill");b.destroy();
 MoneyBoostNativeAdapter c=new MoneyBoostNativeAdapter(new android.app.Activity(),"action","same-test-unit");c.load();check(NativeAdPreloader.id.contains("native_action_"),"Action pool missing");NativeAdPreloader.available=true;c.show(0,.2f,1,.3f);NativeAdPreloader.last.callback.onAdImpression();check(MoneyBoostAdDiagnostics.params.get(MoneyBoostAdDiagnostics.params.size()-1).get("scene").equals("native_action"),"Action scene wrong");c.destroy();
 MoneyBoostNativeAdapter compact=new MoneyBoostNativeAdapter(new android.app.Activity(),"compact","same-test-unit",true);compact.load();NativeAdPreloader.available=true;
 compact.show(0,.2f,1,.058f);check(NativeAdPreloader.last!=null,"compact strip not acquired");
 android.view.View compactView=(android.view.View)viewField.get(compact);check(compactView!=null&&compactView.visibility==android.view.View.VISIBLE,"72dp compact strip hidden");compact.destroy();
 MoneyBoostNativeAdapter tile=new MoneyBoostNativeAdapter(new android.app.Activity(),"tile","test-unit",2);tile.load();NativeAdPreloader.available=true;
 tile.show(.1f,.2f,.31f,.20f);check(tile.isShowing(),"normal-sized grid Native hidden");tile.showClipped(.1f,-.05f,.31f,.20f,0,0,1,1);check(tile.isShowing(),"partially scrolled tile disappeared");android.widget.FrameLayout tileHost=(android.widget.FrameLayout)((android.view.View)viewField.get(tile)).getParent();
 check(!tileHost.dispatchTouchEvent(new android.view.MotionEvent(10,5)),"invisible portion intercepted header touch");
 check(tileHost.dispatchTouchEvent(new android.view.MotionEvent(10,60)),"visible portion rejected ad touch");
 tile.hide();check(!tile.isShowing(),"hidden tile still showing");tile.destroy();
 System.out.println("NATIVE PRELOADING PASS: consent, start rollback, single registration, no eager poll, clipped slot, SDK inventory, hide/restore, expiry, dedup impression, click, stale paid callback, destroy, separate pools, no manual retries.");
}}'''
for name,body in stubs.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(body)
subprocess.run([str(j/'javac'),'-d',str(out/'classes'),*[str(out/n) for n in stubs],str(src/'MoneyBoostNativeAdapter.java'),str(src/'MoneyBoostNativeGeometry.java'),str(src/'MoneyBoostNativeStyle.java')],check=True)
subprocess.run([str(j/'java'),'-cp',str(out/'classes'),'com.joyboost.moneyboost.NativePreloadTest'],check=True)
