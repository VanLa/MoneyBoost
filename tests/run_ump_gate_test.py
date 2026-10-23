from pathlib import Path
import tempfile, subprocess
from zipfile import ZipFile
root = Path(__file__).resolve().parent.parent
java_home = Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home')
android_jar = Path.home()/'Library/Android/sdk/platforms/android-36/android.jar'
p = Path(tempfile.mkdtemp(prefix='moneyboost-ump-test-'))
(p/'sdk.jar').write_bytes(ZipFile(root/'frpianosdk/build/outputs/aar/frpianosdk-release.aar').read('classes.jar'))
cache=Path.home()/'.gradle/caches/modules-2/files-2.1'
for label, pattern in [('ump','com.google.android.ump/user-messaging-platform/4.0.0/**/*.aar'),
                       ('gma','com.google.android.libraries.ads.mobile.sdk/ads-mobile-sdk/1.6.0/**/*.aar')]:
    (p/(label+'.jar')).write_bytes(ZipFile(next(cache.glob(pattern))).read('classes.jar'))
stubs={
'android/os/Bundle.java':'package android.os; public class Bundle extends java.util.HashMap<String,Object> { public Bundle(){} public Bundle(Bundle b){super(b);} public void putString(String k,String v){put(k,v);} public void putLong(String k,long v){put(k,v);} public String getString(String k){return (String)get(k);} public long getLong(String k){return ((Number)get(k)).longValue();} }',
'android/os/SystemClock.java':'package android.os; public class SystemClock { public static long elapsedRealtime(){return 1000;} }',

'android/os/Looper.java':'package android.os; public class Looper { static final Looper MAIN=new Looper();public static Looper getMainLooper(){return MAIN;} public static Looper myLooper(){return getMainLooper();} }',
'android/os/Handler.java':'package android.os; public class Handler { public Handler(Looper l){} public boolean post(Runnable r){r.run();return true;} public boolean postDelayed(Runnable r,long d){return true;} public void removeCallbacks(Runnable r){} }',
'android/util/Log.java':'package android.util; public class Log { public static int i(String t,String m){return 0;} public static int w(String t,String m){return 0;} public static int w(String t,String m,Throwable e){return 0;} public static int e(String t,String m,Throwable e){throw new AssertionError(m,e);} }',
'com/joyboost/moneyboost/MoneyBoostFirebaseManager.java':'package com.joyboost.moneyboost; public class MoneyBoostFirebaseManager { static final MoneyBoostFirebaseManager VALUE=new MoneyBoostFirebaseManager(); public static MoneyBoostFirebaseManager instance(){return VALUE;} public void MoneyBoostApplyConsentFromUmp(boolean eligible,boolean personalized) {if(personalized)throw new AssertionError("inferred personalization");} public void MoneyBoostLogFirebaseEvent(String n,String p){} public static final java.util.List<String> events=new java.util.ArrayList<>(); public static final java.util.List<android.os.Bundle> payloads=new java.util.ArrayList<>(); public static boolean fail; public void MoneyBoostLogDiagnostic(String n,android.os.Bundle p){if(fail)throw new IllegalStateException("telemetry offline");events.add(n);payloads.add(p);} }'
}
for name,src in stubs.items():
 f=p/name;f.parent.mkdir(parents=True,exist_ok=True);f.write_text(src)

cp=':'.join(str(x) for x in [p/'sdk.jar',p/'ump.jar',p/'gma.jar',android_jar])
subprocess.run([str(java_home/'bin/javac'),'-cp',cp,'-d',str(p/'classes'),
    *[str(p/name) for name in stubs],str(root/'tests/MoneyBoostUmpGateTest.java'), str(root/'tests/MoneyBoostAdDiagnosticsTest.java')],check=True)
subprocess.run([str(java_home/'bin/java'),'-cp',str(p/'classes')+':'+cp,
    'com.joyboost.moneyboost.MoneyBoostUmpGateTest'],check=True)

subprocess.run([str(java_home/'bin/java'),'-cp',str(p/'classes')+':'+cp,
    'com.joyboost.moneyboost.MoneyBoostAdDiagnosticsTest'],check=True)
