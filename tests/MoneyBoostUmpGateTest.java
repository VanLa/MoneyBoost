package com.joyboost.moneyboost;

import com.google.android.ump.ConsentInformation;
import java.lang.reflect.*;
import java.util.*;

/** Exercises the compiled manager with UMP responses; JVM Android shims supply only logging/dispatch. */
public final class MoneyBoostUmpGateTest {
    private static void set(Object manager, String name, Object value) throws Exception {
        Field field = manager.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(manager, value);
    }
    private static Object invoke(Object manager, String name, Class<?>[] types, Object... values) throws Exception {
        Method method = manager.getClass().getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(manager, values);
    }
    private static ConsentInformation consent(boolean eligible, int status) {
        return (ConsentInformation) Proxy.newProxyInstance(ConsentInformation.class.getClassLoader(),
                new Class<?>[]{ConsentInformation.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "canRequestAds": return eligible;
                        case "getConsentStatus": return status;
                        case "getPrivacyOptionsRequirementStatus":
                            return ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
                        default: return null;
                    }
                });
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        MoneyBoostMediationManager manager = MoneyBoostMediationManager.getInstance();
        List<String> reasons = new ArrayList<>();
        Field listeners = manager.getClass().getDeclaredField("MoneyBoost_initListeners");
        listeners.setAccessible(true);
        @SuppressWarnings("unchecked") List<MoneyBoostMediationManager.ADInitListener> queue =
                (List<MoneyBoostMediationManager.ADInitListener>) listeners.get(manager);
        // No UMP instance, first launch without a decision, and consent still required all block GMA.
        for (ConsentInformation info : Arrays.asList(null,
                consent(false, ConsentInformation.ConsentStatus.UNKNOWN),
                consent(false, ConsentInformation.ConsentStatus.REQUIRED))) {
            set(manager, "consentInformation", info);
            set(manager, "MoneyBoost_isInitializing", true);
            queue.add(new MoneyBoostMediationManager.ADInitListener() {
                public void onAdInitSuccess() { throw new AssertionError("GMA initialized without consent"); }
                public void onAdInitFailed(String reason) { reasons.add(reason); }
            });
            // Null Activity/App ID deliberately fail if execution ever reaches GMA setup.
            invoke(manager, "MoneyBoostInitGmaSdk",
                    new Class<?>[]{android.app.Activity.class, String.class, int.class}, null, null, 0);
            check(!manager.MoneyBoostCanLoadAds(), "Adapter retries were allowed without consent");
            check(reasons.get(reasons.size()-1).contains("UMP does not allow"), "GMA setup ran before consent gate");
        }
        // UMP's retained permission remains usable after an update error; never infer it from status.
        set(manager, "consentInformation", consent(true, ConsentInformation.ConsentStatus.OBTAINED));
        invoke(manager, "MoneyBoostApplyPrivacySettings", new Class<?>[0]);
        check(manager.MoneyBoostCanLoadAds(), "Cached UMP permission was lost");
        set(manager, "consentInformation", consent(true, ConsentInformation.ConsentStatus.NOT_REQUIRED));
        invoke(manager, "MoneyBoostApplyPrivacySettings", new Class<?>[0]);
        check(manager.MoneyBoostCanLoadAds(), "Non-required region was blocked");
        set(manager, "consentInformation", consent(false, ConsentInformation.ConsentStatus.OBTAINED));
        invoke(manager, "MoneyBoostApplyPrivacySettings", new Class<?>[0]);
        check(!manager.MoneyBoostCanLoadAds(), "OBTAINED incorrectly overrode canRequestAds=false");
        check(manager.MoneyBoostIsPrivacyOptionsRequired(), "Privacy entry requirement was lost");
        System.out.println("UMP GATE PASS: missing/unknown/required block GMA; cached/not-required allow; revocation blocks retries; status is not personalization consent.");
    }
}
