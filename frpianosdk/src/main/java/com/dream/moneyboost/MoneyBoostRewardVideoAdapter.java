package com.dream.moneyboost;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader;

import java.util.concurrent.ScheduledFuture;

public class MoneyBoostRewardVideoAdapter {
    private int MoneyBoost_rewardRetryAttempt;
    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    public MoneyBoostRewardVideoListener MoneyBoost_rewardVideoListener;
    private boolean MoneyBoost_adReady = false;
    private boolean MoneyBoost_isShowing = false;
    private boolean MoneyBoost_isLoading = false;
    private ScheduledFuture<?> MoneyBoost_retryFuture;

    private final PreloadCallback MoneyBoost_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADREWARDLoaded");
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = true;
            MoneyBoost_rewardRetryAttempt = 0;
            MoneyBoostCancelRetry();
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADREWARDFailed");
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = false;
            MoneyBoost_rewardRetryAttempt++;
            long delay = (long) Math.pow(2, Math.min(6, MoneyBoost_rewardRetryAttempt));
            MoneyBoostCancelRetry();
            MoneyBoost_retryFuture = MoneyBoostAdRetryScheduler.scheduleSeconds(
                    MoneyBoostRewardVideoAdapter.this::MoneyBoostLoadRewardVideoAd, delay);
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

    public void MoneyBoostLoadRewardVideoAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            return;
        }
        if (MoneyBoost_isLoading || MoneyBoost_isShowing || RewardedAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = true;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADREWARD");
        if (!MoneyBoostStartRewardPreloading(unitId)) {
            MoneyBoost_isLoading = false;
        }
    }

    private boolean MoneyBoostStartRewardPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest);
        return RewardedAdPreloader.start(unitId, preloadConfig, MoneyBoost_preloadCallback);
    }

    public void MoneyBoostShowRewardVideoAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        final String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            return;
        }
        if (!RewardedAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        MoneyBoost_activity.runOnUiThread(new Runnable() {
            public void run() {
                final RewardedAd rewardedAd = RewardedAdPreloader.pollAd(unitId);
                if (rewardedAd == null) {
                    MoneyBoostLoadRewardVideoAd();
                    return;
                }
                MoneyBoost_adReady = false;
                rewardedAd.setAdEventCallback(new RewardedAdEventCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        MoneyBoost_isShowing = true;
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdDisplayed();
                        }
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        MoneyBoost_isShowing = false;
                        MoneyBoost_adReady = false;
                        rewardedAd.destroy();
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdClosed();
                        }
                        MoneyBoostLoadRewardVideoAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(
                            @NonNull com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError fullScreenContentError) {
                        MoneyBoost_adReady = false;
                        if (!MoneyBoost_isShowing) {
                            rewardedAd.destroy();
                            MoneyBoostLoadRewardVideoAd();
                            return;
                        }
                        MoneyBoost_isShowing = false;
                        rewardedAd.destroy();
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdClosed();
                        }
                        MoneyBoostLoadRewardVideoAd();
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
                rewardedAd.show(MoneyBoost_activity, new OnUserEarnedRewardListener() {
                    @Override
                    public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                        if (MoneyBoost_rewardVideoListener != null) {
                            MoneyBoost_rewardVideoListener.MoneyBoostOnRewardVideoAdCompleted();
                        }
                    }
                });
            }
        });
    }

    public boolean MoneyBoostIsReady() {
        return MoneyBoost_adReady && MoneyBoostIsAdAvailable();
    }

    public void MoneyBoostOnDestroy() {
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = false;
        MoneyBoost_isShowing = false;
        MoneyBoost_adReady = false;
        String unitId = MoneyBoostUnitId();
        if (unitId != null) {
            RewardedAdPreloader.destroy(unitId);
        }
        MoneyBoost_activity = null;
        MoneyBoost_rewardVideoListener = null;
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
