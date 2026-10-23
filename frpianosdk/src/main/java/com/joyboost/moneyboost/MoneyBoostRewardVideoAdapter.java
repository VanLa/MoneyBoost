package com.joyboost.moneyboost;

import android.app.Activity;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;

public class MoneyBoostRewardVideoAdapter {
    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    public MoneyBoostRewardVideoListener MoneyBoost_rewardVideoListener;
    private volatile boolean MoneyBoost_isShowing = false;
    private volatile boolean MoneyBoost_isLoading = false;
    private boolean MoneyBoost_preloadingStarted;

    private final PreloadCallback MoneyBoost_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            if (MoneyBoost_activity == null) return;
            MoneyBoostAdDiagnostics.load("reward", MoneyBoost_ad_unit, "success", null);
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADREWARDLoaded");
            MoneyBoost_isLoading = false;

        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            if (MoneyBoost_activity == null) return;
            MoneyBoostAdDiagnostics.load("reward", MoneyBoost_ad_unit, "failed", adError);
            Log.w("MoneyBoostAds", "Rewarded load failed unit=" + MoneyBoost_ad_unit + " code=" + adError.getCode() + " message=" + adError.getMessage());
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========",
                    "LOADREWARDFailed code=" + adError.getCode()
                            + " message=" + adError.getMessage());
            MoneyBoost_isLoading = false;

            // The registered SDK preloader owns retries and replenishment.
        }
        @Override
        public void onAdsExhausted(@NonNull String preloadId) {
            MoneyBoost_isLoading = false;
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
        return unitId != null && RewardedAdPreloader.isAdAvailable(unitId);
    }

    public void MoneyBoostInitRewardVideoAdapter() {
        // 预加载在 MobileAds.initialize 完成后由 Load 触发
    }

    public synchronized void MoneyBoostLoadRewardVideoAd() {
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
        MoneyBoostAdDiagnostics.load("reward", MoneyBoost_ad_unit, "requested", null);
        Log.i("MoneyBoostAds", "RewardVideo load requested unit=" + MoneyBoost_ad_unit);
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADREWARD");
        MoneyBoost_preloadingStarted = true;
        try {
            AdRequest adRequest = new AdRequest.Builder(unitId).build();
            if (!RewardedAdPreloader.start(unitId, new PreloadConfiguration(adRequest, 1), MoneyBoost_preloadCallback)) {
                MoneyBoost_preloadingStarted = false;
                MoneyBoost_isLoading = false;
                MoneyBoostAdDiagnostics.load("reward", MoneyBoost_ad_unit, "preloader_rejected", null);
            }
        } catch (RuntimeException error) {
            MoneyBoost_preloadingStarted = false;
            MoneyBoost_isLoading = false;
            throw error;
        }
    }

    public void MoneyBoostShowRewardVideoAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            MoneyBoostNotifyFailedToShow();
            return;
        }
        final String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            MoneyBoostNotifyFailedToShow();
            return;
        }
        if (!MoneyBoostIsAdAvailable()) {
            MoneyBoostNotifyFailedToShow();
            return;
        }
        MoneyBoost_activity.runOnUiThread(new Runnable() {
            public void run() {
                if (!MoneyBoostMediationManager.getInstance().MoneyBoostCanLoadAds()
                        || MoneyBoost_activity == null || MoneyBoost_activity.isFinishing()
                        || MoneyBoost_activity.isDestroyed()) {
                    MoneyBoostNotifyFailedToShow();
                    return;
                }
                if (MoneyBoost_isShowing) return;
                final RewardedAd rewardedAd = RewardedAdPreloader.pollAd(unitId);
                if (rewardedAd == null) {
                    MoneyBoostNotifyFailedToShow();
                    return;
                }

                MoneyBoost_isShowing = true;
                final java.util.concurrent.atomic.AtomicBoolean finished =
                        new java.util.concurrent.atomic.AtomicBoolean();
                final java.util.concurrent.atomic.AtomicBoolean showFailed =
                        new java.util.concurrent.atomic.AtomicBoolean();
                rewardedAd.setAdEventCallback(new RewardedAdEventCallback() {
                    private boolean displayed;
                    @Override
                    public synchronized void onAdShowedFullScreenContent() {
                        if (finished.get() || displayed) return;
                        displayed = true;
                        MoneyBoost_isShowing = true;
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdDisplayed();
                        }
                    }

                    @Override
                    public synchronized void onAdDismissedFullScreenContent() {
                        if (!finished.compareAndSet(false, true)) return;
                        MoneyBoost_isShowing = false;

                        rewardedAd.destroy();
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdClosed();
                        }
                    }

                    @Override
                    public synchronized void onAdFailedToShowFullScreenContent(
                            @NonNull com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError fullScreenContentError) {
                        if (!finished.compareAndSet(false, true)) return;

                        showFailed.set(true);
                        if (!displayed) {
                            MoneyBoost_isShowing = false;
                            rewardedAd.destroy();
                            MoneyBoostNotifyFailedToShow();
                            return;
                        }
                        MoneyBoost_isShowing = false;
                        rewardedAd.destroy();
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdClosed();
                        }
                    }

                    @Override
                    public void onAdClicked() {
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdClicked();
                        }
                    }

                    @Override
                    public void onAdPaid(@NonNull AdValue adValue) {
                        double revenue = adValue.getValueMicros() / 1_000_000.0;
                        MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseRevenue(
                                revenue, "REWARDED", MoneyBoostResolveNetworkName(rewardedAd), unitId);
                    }
                });
                try {
                    rewardedAd.show(MoneyBoost_activity, new OnUserEarnedRewardListener() {
                        private final java.util.concurrent.atomic.AtomicBoolean delivered =
                                new java.util.concurrent.atomic.AtomicBoolean();
                        @Override
                        public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                            if (!showFailed.get() && delivered.compareAndSet(false, true) && MoneyBoost_rewardVideoListener != null) {
                                MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdCompleted();
                            }
                        }
                    });
                } catch (RuntimeException e) {
                    if (finished.compareAndSet(false, true)) {
                        showFailed.set(true);
                        MoneyBoost_isShowing = false;
                        rewardedAd.destroy();
                        MoneyBoostNotifyFailedToShow();
                    }
                }
            }
        });
    }

    public boolean MoneyBoostIsReady() {
        return MoneyBoostIsAdAvailable();
    }

    private void MoneyBoostNotifyFailedToShow() {
        if (MoneyBoost_rewardVideoListener != null) {
            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdFailedToShow();
        }
    }

    public synchronized void MoneyBoostOnDestroy() {

        MoneyBoost_isLoading = false;
        MoneyBoost_isShowing = false;

        String unitId = MoneyBoostUnitId();
        if (unitId != null) RewardedAdPreloader.destroy(unitId);
        MoneyBoost_preloadingStarted = false;
        MoneyBoost_activity = null;
        MoneyBoost_rewardVideoListener = null;
    }

    private String MoneyBoostUnitId() {
        if (MoneyBoost_ad_unit == null) {
            return null;
        }
        String unitId = MoneyBoost_ad_unit.trim();
        return unitId.isEmpty() ? null : unitId;
    }

    private static String MoneyBoostResolveNetworkName(RewardedAd ad) {
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
