from pathlib import Path
import tempfile,subprocess
p=Path(tempfile.mkdtemp(prefix='moneyboost-native-test-'))
root=Path(__file__).resolve().parent.parent
src='''package com.joyboost.moneyboost; public class NativeGeometryTest {
 static void check(boolean ok,String m){if(!ok)throw new AssertionError(m);}
 public static void main(String[] args){
 check(MoneyBoostNativeGeometry.fits(16,300,358,200,390,844,92,280,200),"safe card");
 check(!MoneyBoostNativeGeometry.fits(16,600,358,200,390,844,92,280,200),"banner collision");
 check(!MoneyBoostNativeGeometry.fits(16,300,358,199,390,844,92,280,200),"too small");
 check(!MoneyBoostNativeGeometry.fits(-1,300,358,200,390,844,92,280,200),"offscreen");
 check(!MoneyBoostNativeGeometry.fits(100,300,358,200,390,844,92,280,200),"right clipping");
 check(!MoneyBoostNativeGeometry.fits(16,300,358,200,390,844,600,280,200),"expanded banner reserve");
 check(!MoneyBoostNativeGeometry.fits(16,300,Integer.MAX_VALUE,200,390,844,92,280,200),"overflow");
 System.out.println("NATIVE GEOMETRY PASS: safe card, banner reserve, min size, screen bounds, expanded reserve, overflow.");}}
'''
(p/'NativeGeometryTest.java').write_text(src)
j=Path('/Applications/Android Studio.app/Contents/jbr/Contents/Home/bin')
subprocess.run([str(j/'javac'),'-d',str(p),str(p/'NativeGeometryTest.java'),str(root/'frpianosdk/src/main/java/com/joyboost/moneyboost/MoneyBoostNativeGeometry.java')],check=True)
subprocess.run([str(j/'java'),'-cp',str(p),'com.joyboost.moneyboost.NativeGeometryTest'],check=True)
