package com.joyboost.moneyboost;

import android.os.Bundle;
import android.util.Log;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

/** Diagnostic telemetry must never interrupt consent, initialization, or advertising. */
final class MoneyBoostAdDiagnostics {
    static String shortText(String value) {
        if (value == null) return "";
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    static void record(String event, Bundle params) {
        try {
            params.putLong("diag_version", 1);
            Log.i("MoneyBoostInit", event + " " + params);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogDiagnostic(event, params);
        } catch (Exception | LinkageError error) {
            Log.w("MoneyBoostInit", "Diagnostic telemetry unavailable: " + event, error);
        }
    }

    static void config(String format, String unit) {
        try {
            Bundle params = new Bundle();
            params.putString("ad_format", format);
            params.putString("ad_unit_id", shortText(unit));
            params.putString("result", unit == null || unit.trim().isEmpty() ? "missing" : "configured");
            record("ads_config_result", params);
        } catch (Exception | LinkageError error) {
            Log.w("MoneyBoostInit", "Configuration diagnostic unavailable", error);
        }
    }

    static void load(String format, String unit, String result, LoadAdError error) {
        try {
            Bundle params = new Bundle();
            params.putString("ad_format", format);
            params.putString("ad_unit_id", shortText(unit));
            params.putString("result", result);
            if (error != null) {
                params.putString("error_code", shortText(String.valueOf(error.getCode())));
                params.putString("error_message", shortText(error.getMessage()));
                // Full error / mediation response remains in logcat; GA strings are limited to 100 chars.
                Log.w("MoneyBoostAds", "Load error details: " + error);
            }
            record("ads_load_result", params);
        } catch (Exception | LinkageError errorReporting) {
            Log.w("MoneyBoostInit", "Load diagnostic unavailable", errorReporting);
        }
    }
}
