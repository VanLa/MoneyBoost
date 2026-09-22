package com.joyboost.moneyboost;

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

public class MoneyBoostInterstitialAdapter {
    private int MoneyBoost_interRetryAttempt;
    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    public MoneyBoostInterstitialListener MoneyBoost_InterstitialListener;
    private boolean MoneyBoost_adReady = false;
    private boolean MoneyBoost_isShowing = false;
    private boolean MoneyBoost_isLoading = false;
    private ScheduledFuture<?> MoneyBoost_retryFuture;
    private final PreloadCallback MoneyBoost_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADINTERLoaded");
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = true;
            MoneyBoost_interRetryAttempt = 0;
            MoneyBoostCancelRetry();
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADINTERFailed");
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = false;
            MoneyBoost_interRetryAttempt++;
            long delay = (long) Math.pow(2, Math.min(6, MoneyBoost_interRetryAttempt));
            MoneyBoostCancelRetry();
            MoneyBoost_retryFuture = MoneyBoostAdRetryScheduler.scheduleSeconds(
                    MoneyBoostInterstitialAdapter.this::MoneyBoostLoadInterstitialAd, delay);
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

    public void MoneyBoostLoadInterstitialAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            return;
        }
        if (MoneyBoost_isLoading || MoneyBoost_isShowing || InterstitialAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = true;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADINTER");
        if (!MoneyBoostStartInterstitialPreloading(unitId)) {
            MoneyBoost_isLoading = false;
        }
    }

    private boolean MoneyBoostStartInterstitialPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest);
        return InterstitialAdPreloader.start(unitId, preloadConfig, MoneyBoost_preloadCallback);
    }

    public boolean MoneyBoostIsReady() {
        return MoneyBoost_adReady && MoneyBoostIsAdAvailable();
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
                final InterstitialAd interstitialAd = InterstitialAdPreloader.pollAd(unitId);
                if (interstitialAd == null) {
                    MoneyBoostLoadInterstitialAd();
                    if (MoneyBoost_InterstitialListener != null) {
                        MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdFailedToShow();
                    }
                    return;
                }
                MoneyBoost_adReady = false;
                interstitialAd.setAdEventCallback(new InterstitialAdEventCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        MoneyBoost_isShowing = true;
                        if (MoneyBoost_InterstitialListener != null) {
                            MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdDisplayed();
                        }
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        MoneyBoost_isShowing = false;
                        interstitialAd.destroy();
                        if (MoneyBoost_InterstitialListener != null) {
                            MoneyBoost_InterstitialListener.MoneyBoostOnInterstitialAdClosed();
                        }
                        MoneyBoostLoadInterstitialAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(
                            @NonNull com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError fullScreenContentError) {
                        boolean wasShowing = MoneyBoost_isShowing;
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
                        MoneyBoostLoadInterstitialAd();
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
                interstitialAd.show(MoneyBoost_activity);
            }
        });
    }

    public void MoneyBoostOnDestroy() {
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = false;
        MoneyBoost_isShowing = false;
        MoneyBoost_adReady = false;
        String unitId = MoneyBoostUnitId();
        if (unitId != null) {
            InterstitialAdPreloader.destroy(unitId);
        }
        MoneyBoost_activity = null;
        MoneyBoost_InterstitialListener = null;
    }

    private void MoneyBoostCancelRetry() {
        MoneyBoostAdRetryScheduler.cancel(MoneyBoost_retryFuture);
        MoneyBoost_retryFuture = null;
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
