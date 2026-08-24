package com.dream.laughtale;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdPreloader;

import java.util.concurrent.ScheduledFuture;

public class LaughTaleInterstitialAdapter {
    private int LaughTale_interRetryAttempt;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleInterstitialListener LaughTale_InterstitialListener;
    private boolean LaughTale_adReady = false;
    private boolean LaughTale_isShowing = false;
    private boolean LaughTale_isLoading = false;
    private ScheduledFuture<?> LaughTale_retryFuture;
    private final PreloadCallback LaughTale_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADINTERLoaded");
            LaughTale_isLoading = false;
            LaughTale_adReady = true;
            LaughTale_interRetryAttempt = 0;
            LaughTaleCancelRetry();
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADINTERFailed");
            LaughTale_isLoading = false;
            LaughTale_adReady = false;
            LaughTale_interRetryAttempt++;
            long delay = (long) Math.pow(2, Math.min(6, LaughTale_interRetryAttempt));
            LaughTaleCancelRetry();
            LaughTale_retryFuture = LaughTaleAdRetryScheduler.scheduleSeconds(
                    LaughTaleInterstitialAdapter.this::LaughTaleLoadInterstitialAd, delay);
        }
    };

    public boolean LaughTaleIsShowing() {
        return LaughTale_isShowing;
    }

    public boolean LaughTaleIsLoading() {
        return LaughTale_isLoading;
    }

    public boolean LaughTaleIsAdAvailable() {
        String unitId = LaughTaleUnitId();
        return unitId != null && InterstitialAdPreloader.isAdAvailable(unitId);
    }

    public void LaughTaleInitInterstitialAdapter() {
        // 预加载在 MobileAds.initialize 完成后由 Load 触发
    }

    public void LaughTaleLoadInterstitialAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        String unitId = LaughTaleUnitId();
        if (unitId == null) {
            return;
        }
        if (LaughTale_isLoading || LaughTale_isShowing || InterstitialAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        LaughTaleCancelRetry();
        LaughTale_isLoading = true;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADINTER");
        if (!LaughTaleStartInterstitialPreloading(unitId)) {
            LaughTale_isLoading = false;
        }
    }

    private boolean LaughTaleStartInterstitialPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest);
        return InterstitialAdPreloader.start(unitId, preloadConfig, LaughTale_preloadCallback);
    }

    public boolean LaughTaleIsReady() {
        return LaughTale_adReady && LaughTaleIsAdAvailable();
    }

    public void LaughTaleShowInterstitialAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            if (LaughTale_InterstitialListener != null) {
                LaughTale_InterstitialListener.LaughTaleOnInterstitialAdFailedToShow();
            }
            return;
        }
        final String unitId = LaughTaleUnitId();
        if (unitId == null) {
            if (LaughTale_InterstitialListener != null) {
                LaughTale_InterstitialListener.LaughTaleOnInterstitialAdFailedToShow();
            }
            return;
        }
        if (!InterstitialAdPreloader.isAdAvailable(unitId)) {
            LaughTaleLoadInterstitialAd();
            if (LaughTale_InterstitialListener != null) {
                LaughTale_InterstitialListener.LaughTaleOnInterstitialAdFailedToShow();
            }
            return;
        }
        LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                final InterstitialAd interstitialAd = InterstitialAdPreloader.pollAd(unitId);
                if (interstitialAd == null) {
                    LaughTaleLoadInterstitialAd();
                    if (LaughTale_InterstitialListener != null) {
                        LaughTale_InterstitialListener.LaughTaleOnInterstitialAdFailedToShow();
                    }
                    return;
                }
                LaughTale_adReady = false;
                interstitialAd.setAdEventCallback(new InterstitialAdEventCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        LaughTale_isShowing = true;
                        if (LaughTale_InterstitialListener != null) {
                            LaughTale_InterstitialListener.LaughTaleOnInterstitialAdDisplayed();
                        }
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        LaughTale_isShowing = false;
                        interstitialAd.destroy();
                        if (LaughTale_InterstitialListener != null) {
                            LaughTale_InterstitialListener.LaughTaleOnInterstitialAdClosed();
                        }
                        LaughTaleLoadInterstitialAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(
                            @NonNull com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError fullScreenContentError) {
                        boolean wasShowing = LaughTale_isShowing;
                        LaughTale_isShowing = false;
                        interstitialAd.destroy();
                        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                                "=========", "INTERFailedToShow wasShowing=" + wasShowing
                                        + " " + fullScreenContentError.getMessage());
                        if (LaughTale_InterstitialListener != null) {
                            if (wasShowing) {
                                LaughTale_InterstitialListener.LaughTaleOnInterstitialAdClosed();
                            } else {
                                LaughTale_InterstitialListener.LaughTaleOnInterstitialAdFailedToShow();
                            }
                        }
                        LaughTaleLoadInterstitialAd();
                    }

                    @Override
                    public void onAdClicked() {
                        if (LaughTale_InterstitialListener != null) {
                            LaughTale_InterstitialListener.LaughTaleOnInterstitialAdClicked();
                        }
                    }

                    @Override
                    public void onAdPaid(@NonNull AdValue adValue) {
                        LaughTaleLogFirebaseRevenue(adValue, "INTERSTITIAL", unitId, interstitialAd);
                    }
                });
                interstitialAd.show(LaughTale_activity);
            }
        });
    }

    public void LaughTaleOnDestroy() {
        LaughTaleCancelRetry();
        LaughTale_isLoading = false;
        LaughTale_isShowing = false;
        LaughTale_adReady = false;
        String unitId = LaughTaleUnitId();
        if (unitId != null) {
            InterstitialAdPreloader.destroy(unitId);
        }
        LaughTale_activity = null;
        LaughTale_InterstitialListener = null;
    }

    private void LaughTaleCancelRetry() {
        LaughTaleAdRetryScheduler.cancel(LaughTale_retryFuture);
        LaughTale_retryFuture = null;
    }

    private String LaughTaleUnitId() {
        if (LaughTale_ad_unit == null) {
            return null;
        }
        String unitId = LaughTale_ad_unit.trim();
        return unitId.isEmpty() ? null : unitId;
    }

    private static void LaughTaleLogFirebaseRevenue(
            AdValue adValue, String adFormat, String unitId, InterstitialAd ad) {
        if (adValue == null) {
            return;
        }
        double revenue = adValue.getValueMicros() / 1_000_000.0;
        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(
                revenue, adFormat, LaughTaleResolveNetworkName(ad), unitId);
    }

    private static String LaughTaleResolveNetworkName(InterstitialAd ad) {
        if (ad == null || ad.getResponseInfo() == null) {
            return "AdMob";
        }
        ResponseInfo responseInfo = ad.getResponseInfo();
        AdSourceResponseInfo loaded = responseInfo.getLoadedAdSourceResponseInfo();
        if (loaded != null && loaded.getName() != null && !loaded.getName().isEmpty()) {
            return loaded.getName();
        }
        String adapter = responseInfo.getAdapterClassName();
        return adapter != null && !adapter.isEmpty() ? adapter : "AdMob";
    }
}
