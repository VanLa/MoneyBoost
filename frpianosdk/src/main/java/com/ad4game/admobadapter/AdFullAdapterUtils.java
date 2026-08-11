package com.ad4game.admobadapter;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.mediation.MediationAdConfiguration;
import com.google.android.gms.ads.mediation.MediationConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

final class AdFullAdapterUtils {
    static final String ERROR_DOMAIN = "com.ad4game.admobadapter";
    static final int ERROR_INVALID_SERVER_PARAMETER = 101;
    static final int ERROR_RECURSIVE_REQUEST = 102;
    static final int ERROR_NO_ACTIVITY = 103;
    static final int ERROR_AD_NOT_READY = 104;

    private static final Set<String> ACTIVE_REQUESTS = ConcurrentHashMap.newKeySet();

    private AdFullAdapterUtils() {}

    @Nullable
    static String getAdUnitId(@NonNull MediationAdConfiguration configuration) {
        String value = configuration.getServerParameters().getString(
                MediationConfiguration.CUSTOM_EVENT_SERVER_PARAMETER_FIELD);
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    static boolean beginRequest(@NonNull String format, @NonNull String adUnitId) {
        return ACTIVE_REQUESTS.add(format + ':' + adUnitId);
    }

    static void endRequest(@NonNull String format, @NonNull String adUnitId) {
        ACTIVE_REQUESTS.remove(format + ':' + adUnitId);
    }

    @Nullable
    static Activity findActivity(@Nullable Context context) {
        Context current = context;
        while (current instanceof ContextWrapper) {
            if (current instanceof Activity) {
                return (Activity) current;
            }
            current = ((ContextWrapper) current).getBaseContext();
        }
        return current instanceof Activity ? (Activity) current : null;
    }

    @NonNull
    static AdError invalidParameterError() {
        return new AdError(ERROR_INVALID_SERVER_PARAMETER,
                "The custom event parameter must contain a downstream GMA ad unit ID.",
                ERROR_DOMAIN);
    }

    @NonNull
    static AdError recursiveRequestError(@NonNull String adUnitId) {
        return new AdError(ERROR_RECURSIVE_REQUEST,
                "Recursive custom event request for " + adUnitId, ERROR_DOMAIN);
    }

    @NonNull
    static AdError noActivityError() {
        return new AdError(ERROR_NO_ACTIVITY, "An Activity context is required to show this ad.",
                ERROR_DOMAIN);
    }

    @NonNull
    static AdError notReadyError() {
        return new AdError(ERROR_AD_NOT_READY, "The ad is no longer available.", ERROR_DOMAIN);
    }

    @NonNull
    static AdError fromLoadError(@NonNull LoadAdError error) {
        return new AdError(error.getCode().getValue(), error.getMessage(), ERROR_DOMAIN);
    }

    @NonNull
    static AdError fromShowError(@NonNull FullScreenContentError error) {
        return new AdError(error.getCode().getValue(), error.getMessage(), ERROR_DOMAIN);
    }
}
