package com.joyboost.moneyboost;
import android.os.Bundle;
import java.lang.reflect.*;

public final class MoneyBoostAdDiagnosticsTest {
    static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true); field.set(target, value);
    }
    static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        MoneyBoostMediationManager manager = MoneyBoostMediationManager.getInstance();
        set(manager, "MoneyBoost_initGeneration", 4);
        set(manager, "MoneyBoost_isInitializing", true);
        set(manager, "diagnosticInitStarted", 600L);
        set(manager, "diagnosticInitStage", "gma");
        Method complete = manager.getClass().getDeclaredMethod("MoneyBoostCompleteInit", int.class, boolean.class, String.class);
        complete.setAccessible(true);
        complete.invoke(manager, 3, false, "stale");
        check(MoneyBoostFirebaseManager.events.isEmpty(), "stale callback emitted a result");
        complete.invoke(manager, 4, false, "timeout");
        Bundle result = MoneyBoostFirebaseManager.payloads.get(0);
        check(MoneyBoostFirebaseManager.events.get(0).equals("ads_init_result"), "missing result event");
        check(result.getString("result").equals("failed") && result.getString("init_stage").equals("gma"), "wrong failure stage");
        check(result.getLong("elapsed_ms") == 400L && result.getLong("init_attempt") == 4L, "lost numeric types / duration");
        check(result.getLong("can_request_ads") == 0L && result.getLong("consent_status") == -1L, "missing consent state");
        complete.invoke(manager, 4, false, "duplicate");
        check(MoneyBoostFirebaseManager.events.size() == 1, "duplicate failure result");
        set(manager, "MoneyBoost_isInitializing", true);
        complete.invoke(manager, 4, true, "ready");
        check(MoneyBoostFirebaseManager.payloads.get(1).getString("result").equals("success"), "missing success");
        check(MoneyBoostAdDiagnostics.shortText("x".repeat(200)).length() == 100, "GA string limit");
        MoneyBoostAdDiagnostics.load("reward", "unit", "requested", null);
        check(MoneyBoostFirebaseManager.payloads.get(2).getString("ad_format").equals("reward"), "missing load format");
        MoneyBoostFirebaseManager.fail = true;
        MoneyBoostAdDiagnostics.record("ads_init_start", new Bundle());
        check(MoneyBoostFirebaseManager.events.size() == 3, "telemetry failure interrupted / emitted");
        System.out.println("AD DIAGNOSTICS PASS: stages, consent, numeric duration, stale/duplicate suppression, string limits, telemetry isolation.");
    }
}
