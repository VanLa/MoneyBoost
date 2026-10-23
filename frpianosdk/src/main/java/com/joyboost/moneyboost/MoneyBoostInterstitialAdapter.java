package com.joyboost.moneyboost;

import android.app.Activity;
import android.util.Log;

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

public class MoneyBoostInterstitialAdapter {
    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    public MoneyBoostInterstitialListener MoneyBoost_InterstitialListener;
    private volatile boolean MoneyBoost_isShowing = false;
    private volatile boolean MoneyBoost_isLoading = false;
    private boolean MoneyBoost_preloadingStarted;
    private final PreloadCallback MoneyBoost_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            if (MoneyBoost_activity == null) return;
            MoneyBoostAdDiagnostics.load("inter", MoneyBoost_ad_unit, "success", null);
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADINTERLoaded");
            MoneyBoost_isLoading = false;

        }

        @Override
        public void onAdsExhausted(@NonNull String preloadId) {
            // The SDK replenishes the registered preload configuration automatically.

            MoneyBoost_isLoading = false;
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            if (MoneyBoost_activity == null) return;
            MoneyBoostAdDiagnostics.load("inter", MoneyBoost_ad_unit, "failed", adError);
            Log.w("MoneyBoostAds", "Interstitial load failed unit=" + preloadId + " code=" + adError.getCode() + " message=" + adError.getMessage());
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADINTERFailed");
            MoneyBoost_isLoading = false;

            // The registered SDK preloader owns retries and replenishment.
        }
    };

    public boolean MoneyBoostIsShowing() {
        return MoneyBoost_isShowing;
    }

    public boolean MoneyBoostIsLoading() {
        return MoneyBoost_isLoading;
    }

    public boolean MoneyBoostIsAdAvailable() {
        String unitId = MoneyBoostUnitId();
        return unitId != null && InterstitialAdPreloader.isAdAvailable(unitId);
    }

    public void MoneyBoostInitInterstitialAdapter() {
        // 预加载在 MobileAds.initialize 完成后由 Load 触发
    }

    public synchronized void MoneyBoostLoadInterstitialAd() {
        if (!MoneyBoostMediationManager.getInstance().MoneyBoostCanLoadAds()) return;
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            return;
        }
        if (MoneyBoost_preloadingStarted) {
            return;
        }

        MoneyBoost_isLoading = true;
        MoneyBoostAdDiagnostics.load("inter", MoneyBoost_ad_unit, "requested", null);
        Log.i("MoneyBoostAds", "Interstitial load requested unit=" + MoneyBoost_ad_unit);
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADINTER");
        MoneyBoost_preloadingStarted = true;
        try {
            if (!MoneyBoostStartInterstitialPreloading(unitId)) {
                MoneyBoost_preloadingStarted = false;
                MoneyBoostAdDiagnostics.load("inter", MoneyBoost_ad_unit, "preloader_rejected", null);
                MoneyBoost_isLoading = false;
            }
        } catch (RuntimeException error) {
            MoneyBoost_preloadingStarted = false;
            MoneyBoost_isLoading = false;
            throw error;
        }
    }

    private boolean MoneyBoostStartInterstitialPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest, 2);
        return InterstitialAdPreloader.start(unitId, preloadConfig, MoneyBoost_preloadCallback);
    }

    public boolean MoneyBoostIsReady() {
        return MoneyBoostIsAdAvailable();
    }

    public void MoneyBoostShowInterstitialAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            if (MoneyBoost_InterstitialListener != null) {
                MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
            }
            return;
        }
        final String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            if (MoneyBoost_InterstitialListener != null) {
                MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
            }
            return;
        }
        if (!InterstitialAdPreloader.isAdAvailable(unitId)) {
            MoneyBoostLoadInterstitialAd();
            if (MoneyBoost_InterstitialListener != null) {
                MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
            }
            return;
        }
        MoneyBoost_activity.runOnUiThread(new Runnable() {
            public void run() {
                if (!MoneyBoostMediationManager.getInstance().MoneyBoostCanLoadAds()
                        || MoneyBoost_activity == null || MoneyBoost_activity.isFinishing()
                        || MoneyBoost_activity.isDestroyed()) {
                    if (MoneyBoost_InterstitialListener != null) MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
                    return;
                }
                if (MoneyBoost_isShowing) return;
                final InterstitialAd interstitialAd = InterstitialAdPreloader.pollAd(unitId);
                if (interstitialAd == null) {
                    if (MoneyBoost_InterstitialListener != null) {
                        MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
                    }
                    return;
                }

                MoneyBoost_isShowing = true;
                final java.util.concurrent.atomic.AtomicBoolean finished =
                        new java.util.concurrent.atomic.AtomicBoolean();
                interstitialAd.setAdEventCallback(new InterstitialAdEventCallback() {
                    private boolean displayed;
                    @Override
                    public synchronized void onAdShowedFullScreenContent() {
                        if (finished.get() || displayed) return;
                        displayed = true;
                        MoneyBoost_isShowing = true;
                        if (MoneyBoost_InterstitialListener != null) {
                            MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdDisplayed();
                        }
                    }

                    @Override
                    public synchronized void onAdDismissedFullScreenContent() {
                        if (!finished.compareAndSet(false, true)) return;
                        MoneyBoost_isShowing = false;
                        interstitialAd.destroy();
                        if (MoneyBoost_InterstitialListener != null) {
                            MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdClosed();
                        }
                    }

                    @Override
                    public synchronized void onAdFailedToShowFullScreenContent(
                            @NonNull com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError fullScreenContentError) {
                        if (!finished.compareAndSet(false, true)) return;
                        boolean wasShowing = displayed;
                        MoneyBoost_isShowing = false;
                        interstitialAd.destroy();
                        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                                "=========", "INTERFailedToShow wasShowing=" + wasShowing
                                        + " " + fullScreenContentError.getMessage());
                        if (MoneyBoost_InterstitialListener != null) {
                            if (wasShowing) {
                                MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdClosed();
                            } else {
                                MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
                            }
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        if (MoneyBoost_InterstitialListener != null) {
                            MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdClicked();
                        }
                    }

                    @Override
                    public void onAdPaid(@NonNull AdValue adValue) {
                        MoneyBoostLogFirebaseRevenue(adValue, "INTERSTITIAL", unitId, interstitialAd);
                    }
                });
                try {
                    interstitialAd.show(MoneyBoost_activity);
                } catch (RuntimeException error) {
                    if (finished.compareAndSet(false, true)) {
                        MoneyBoost_isShowing = false;
                        interstitialAd.destroy();
                        if (MoneyBoost_InterstitialListener != null) MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
                    }
                }
            }
        });
    }

    public synchronized void MoneyBoostOnDestroy() {

        MoneyBoost_isLoading = false;
        MoneyBoost_isShowing = false;

        String unitId = MoneyBoostUnitId();
        if (unitId != null) {
            InterstitialAdPreloader.destroy(unitId);
        }
        MoneyBoost_preloadingStarted = false;
        MoneyBoost_activity = null;
        MoneyBoost_InterstitialListener = null;
    }

    private String MoneyBoostUnitId() {
        if (MoneyBoost_ad_unit == null) {
            return null;
        }
        String unitId = MoneyBoost_ad_unit.trim();
        return unitId.isEmpty() ? null : unitId;
    }

    private static void MoneyBoostLogFirebaseRevenue(
            AdValue adValue, String adFormat, String unitId, InterstitialAd ad) {
        if (adValue == null) {
            return;
        }
        double revenue = adValue.getValueMicros() / 1_000_000.0;
        MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseRevenue(
                revenue, adFormat, MoneyBoostResolveNetworkName(ad), unitId);
    }

    private static String MoneyBoostResolveNetworkName(InterstitialAd ad) {
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
