"""Exercise the compiled Firebase manager, replacing only Android/Firebase transport APIs."""
from pathlib import Path
from zipfile import ZipFile
import subprocess, tempfile
root = Path(__file__).resolve().parent.parent
java = Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin')
p = Path(tempfile.mkdtemp(prefix='firebase-diagnostics-test-'))
(p/'sdk.jar').write_bytes(ZipFile(root/'frpianosdk/build/outputs/aar/frpianosdk-release.aar').read('classes.jar'))
stubs = {
'android/os/Bundle.java': 'package android.os; public class Bundle extends java.util.HashMap<String,Object> { public Bundle(){} public Bundle(Bundle b){super(b);} public void putString(String k,String v){put(k,v);} public void putLong(String k,long v){put(k,v);} public void putDouble(String k,double v){put(k,v);} }',
'android/util/Pair.java': 'package android.util; public class Pair<F,S>{ public final F first; public final S second; public Pair(F f,S s){first=f;second=s;} }',
'android/util/Log.java': 'package android.util; public class Log { public static int w(String t,String m,Throwable e){return 0;} }',
'com/google/firebase/analytics/FirebaseAnalytics.java': '''package com.google.firebase.analytics;
public class FirebaseAnalytics {
 public static final java.util.List<String> sent = new java.util.ArrayList<>();
 public static final java.util.List<android.os.Bundle> payloads = new java.util.ArrayList<>();
 public void logEvent(String n, android.os.Bundle b){if(n.equals("bad"))throw new IllegalStateException("transport failed");if(n.equals("link"))throw new NoClassDefFoundError("optional transport");sent.add(n);payloads.add(b);}
}''',
'com/joyboost/moneyboost/MoneyBoostToolsManager.java': 'package com.joyboost.moneyboost; public class MoneyBoostToolsManager { public static MoneyBoostToolsManager instance(){return new MoneyBoostToolsManager();} public void MoneyBoostLogWithDebug(String t,String m){} }',
'com/joyboost/moneyboost/FirebaseDiagnosticsQueueTest.java': '''package com.joyboost.moneyboost;
import java.lang.reflect.*;
import java.util.concurrent.*;
import android.os.Bundle;
import com.google.firebase.analytics.FirebaseAnalytics;
public class FirebaseDiagnosticsQueueTest {
 static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args) throws Exception {
  MoneyBoostFirebaseManager.instance=null;
  java.util.Set<MoneyBoostFirebaseManager> instances=ConcurrentHashMap.newKeySet();
  CountDownLatch go=new CountDownLatch(1); java.util.List<Thread> workers=new java.util.ArrayList<>();
  for(int i=0;i<32;i++){Thread t=new Thread(()->{try{go.await();instances.add(MoneyBoostFirebaseManager.instance());}catch(Exception e){throw new RuntimeException(e);}});workers.add(t);t.start();}
  go.countDown(); for(Thread t:workers)t.join(); check(instances.size()==1,"singleton raced");
  MoneyBoostFirebaseManager m=MoneyBoostFirebaseManager.instance();
  Field q=m.getClass().getDeclaredField("diagnosticQueue");q.setAccessible(true);
  for(int i=0;i<70;i++)m.MoneyBoostLogDiagnostic("event"+i,new Bundle());
  java.util.ArrayDeque<?> queue=(java.util.ArrayDeque<?>)q.get(m);
  check(queue.size()==64,"queue not bounded");queue.clear();
  m.MoneyBoostLogDiagnostic("bad",new Bundle());m.MoneyBoostLogDiagnostic("link",new Bundle());m.MoneyBoostLogDiagnostic("good",new Bundle());
  Field analytics=m.getClass().getDeclaredField("MoneyBoostFirebaseAnalytics");analytics.setAccessible(true);analytics.set(m,new FirebaseAnalytics());
  Method flush=m.getClass().getDeclaredMethod("MoneyBoostFlushDiagnostics");flush.setAccessible(true);flush.invoke(m);
  check(queue.isEmpty(),"flush stuck on failed event");check(FirebaseAnalytics.sent.equals(java.util.List.of("good")),"later event lost");
  m.MoneyBoostLogDiagnostic("live",new Bundle());check(FirebaseAnalytics.sent.equals(java.util.List.of("good","live")),"live event broken");
  FirebaseAnalytics.sent.clear();FirebaseAnalytics.payloads.clear();
  final java.util.Map<String,Float> storage=new java.util.HashMap<>();
  android.content.SharedPreferences.Editor editor=(android.content.SharedPreferences.Editor)Proxy.newProxyInstance(
   FirebaseDiagnosticsQueueTest.class.getClassLoader(),new Class[]{android.content.SharedPreferences.Editor.class},(o,method,a)->{
    if(method.getName().equals("putFloat")){storage.put((String)a[0],(Float)a[1]);return o;}return null;
   });
  android.content.SharedPreferences prefs=(android.content.SharedPreferences)Proxy.newProxyInstance(
   FirebaseDiagnosticsQueueTest.class.getClassLoader(),new Class[]{android.content.SharedPreferences.class},(o,method,a)->{
    if(method.getName().equals("getFloat"))return storage.getOrDefault((String)a[0],(Float)a[1]);return null;
   });
  Field pf=m.getClass().getDeclaredField("MoneyBoost_taichiPref");pf.setAccessible(true);pf.set(m,prefs);
  Field ef=m.getClass().getDeclaredField("MoneyBoost_taichiSharedPreferencesEditor");ef.setAccessible(true);ef.set(m,editor);
  m.MoneyBoostLogFirebaseRevenue(.006,"BANNER","AdMob","test-unit");
  check(FirebaseAnalytics.sent.equals(java.util.List.of("Ad_Impression_Revenue")),"Taichi per-impression missing or standard duplicated");
  m.MoneyBoostLogFirebaseRevenue(.006,"REWARDED","Meta","test-unit");
  check(FirebaseAnalytics.sent.equals(java.util.List.of("Ad_Impression_Revenue","Ad_Impression_Revenue","Total_Avenue_001")),"Taichi threshold broken");
  check(Math.abs(((Number)FirebaseAnalytics.payloads.get(2).get("value")).doubleValue()-.012)<1e-8,"threshold revenue lost");
  check(storage.get("TaichiTroasCache")==0f,"Taichi cache not reset");
  int before=FirebaseAnalytics.sent.size();
  m.MoneyBoostLogFirebaseRevenue(Double.NaN,"BANNER","AdMob","unit");
  m.MoneyBoostLogFirebaseRevenue(-1.,"BANNER","AdMob","unit");
  m.MoneyBoostLogFirebaseRevenue(Double.POSITIVE_INFINITY,"BANNER","AdMob","unit");
  check(FirebaseAnalytics.sent.size()==before,"invalid revenue accepted");
  check(!FirebaseAnalytics.sent.contains("ad_impression"),"duplicate standard revenue");
  System.out.println("REVENUE PASS: Taichi events retained, threshold amount/reset, invalid revenue rejected, no manual ad_impression.");
  System.out.println("FIREBASE DIAGNOSTICS PASS: compiled manager singleton concurrency, queue bound, exception/linkage isolation, later and live delivery.");
 }
}'''
}
for name,src in stubs.items():
 f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(src)
android=Path.home()/'Library/Android/sdk/platforms/android-36/android.jar'
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
# Reflection resolves the manager's method signatures, including Remote Config listener interfaces.
libs=[]
for pattern in ['com.google.firebase/firebase-config/24.0.0/**/*.aar','com.google.android.gms/play-services-tasks/**/*.aar']:
 f=next(cache.glob(pattern));out=p/(f.stem+'.jar');out.write_bytes(ZipFile(f).read('classes.jar'));libs.append(out)
cp=':'.join(map(str,[p/'sdk.jar',android,*libs]))
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(p/'classes'),*[str(p/n) for n in stubs]],check=True)
subprocess.run([str(java/'java'),'-cp',str(p/'classes')+':'+cp,'com.joyboost.moneyboost.FirebaseDiagnosticsQueueTest'],check=True)
