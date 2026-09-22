# AdMob instantiates this custom event entry point from the class name configured in its UI.
-keep public class com.a4g.googlesdkadapter.AdmobCustomEventManager {
    public <init>();
}

# Unity AndroidJavaClass/Call uses literal JNI names, invisible to R8 call analysis.
-keep class com.joyboost.moneyboost.MoneyBoostMediationManager { public *; }
-keep class com.joyboost.moneyboost.MoneyBoostToolsManager { public *; }
-keep class com.joyboost.moneyboost.MoneyBoostFirebaseManager { public *; }
-keepclassmembers class com.unity3d.player.UnityPlayer {
    public static void UnitySendMessage(java.lang.String, java.lang.String, java.lang.String);
}
