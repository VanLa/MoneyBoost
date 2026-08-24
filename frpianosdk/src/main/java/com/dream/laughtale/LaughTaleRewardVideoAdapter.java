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
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdPreloader;

import java.util.concurrent.ScheduledFuture;

public class LaughTaleRewardVideoAdapter {
    private int LaughTale_rewardRetryAttempt;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleRewardVideoListener LaughTale_rewardVideoListener;
    private boolean LaughTale_adReady = false;
    private boolean LaughTale_isShowing = false;
    private boolean LaughTale_isLoading = false;
    private ScheduledFuture<?> LaughTale_retryFuture;

    private final PreloadCallback LaughTale_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADREWARDLoaded");
            LaughTale_isLoading = false;
            LaughTale_adReady = true;
            LaughTale_rewardRetryAttempt = 0;
            LaughTaleCancelRetry();
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADREWARDFailed");
            LaughTale_isLoading = false;
            LaughTale_adReady = false;
            LaughTale_rewardRetryAttempt++;
            long delay = (long) Math.pow(2, Math.min(6, LaughTale_rewardRetryAttempt));
            LaughTaleCancelRetry();
            LaughTale_retryFuture = LaughTaleAdRetryScheduler.scheduleSeconds(
                    LaughTaleRewardVideoAdapter.this::LaughTaleLoadRewardVideoAd, delay);
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
        return unitId != null && RewardedAdPreloader.isAdAvailable(unitId);
    }

    public void LaughTaleInitRewardVideoAdapter() {
        // 预加载在 MobileAds.initialize 完成后由 Load 触发
    }

    public void LaughTaleLoadRewardVideoAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        String unitId = LaughTaleUnitId();
        if (unitId == null) {
            return;
        }
        if (LaughTale_isLoading || LaughTale_isShowing || RewardedAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        LaughTaleCancelRetry();
        LaughTale_isLoading = true;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADREWARD");
        if (!LaughTaleStartRewardPreloading(unitId)) {
            LaughTale_isLoading = false;
        }
    }

    private boolean LaughTaleStartRewardPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest);
        return RewardedAdPreloader.start(unitId, preloadConfig, LaughTale_preloadCallback);
    }

    public void LaughTaleShowRewardVideoAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        final String unitId = LaughTaleUnitId();
        if (unitId == null) {
            return;
        }
        if (!RewardedAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                final RewardedAd rewardedAd = RewardedAdPreloader.pollAd(unitId);
                if (rewardedAd == null) {
                    LaughTaleLoadRewardVideoAd();
                    return;
                }
                LaughTale_adReady = false;
                rewardedAd.setAdEventCallback(new RewardedAdEventCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        LaughTale_isShowing = true;
                        if (LaughTale_rewardVideoListener != null) {
                            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdDisplayed();
                        }
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        LaughTale_isShowing = false;
                        LaughTale_adReady = false;
                        rewardedAd.destroy();
                        if (LaughTale_rewardVideoListener != null) {
                            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdClosed();
                        }
                        LaughTaleLoadRewardVideoAd();
                    }

                    @Override
                    public void onAdFailedToShowFullScreenContent(
                            @NonNull com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError fullScreenContentError) {
                        LaughTale_adReady = false;
                        if (!LaughTale_isShowing) {
                            rewardedAd.destroy();
                            LaughTaleLoadRewardVideoAd();
                            return;
                        }
                        LaughTale_isShowing = false;
                        rewardedAd.destroy();
                        if (LaughTale_rewardVideoListener != null) {
                            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdClosed();
                        }
                        LaughTaleLoadRewardVideoAd();
                    }

                    @Override
                    public void onAdClicked() {
                        if (LaughTale_rewardVideoListener != null) {
                            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdClicked();
                        }
                    }

                    @Override
                    public void onAdPaid(@NonNull AdValue adValue) {
                        double revenue = adValue.getValueMicros() / 1_000_000.0;
                        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(
                                revenue, "REWARDED", LaughTaleResolveNetworkName(rewardedAd), unitId);
                    }
                });
                rewardedAd.show(LaughTale_activity, new OnUserEarnedRewardListener() {
                    @Override
                    public void onUserEarnedReward(@NonNull RewardItem rewardItem) {
                        if (LaughTale_rewardVideoListener != null) {
                            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdCompleted();
                        }
                    }
                });
            }
        });
    }

    public boolean LaughTaleIsReady() {
        return LaughTale_adReady && LaughTaleIsAdAvailable();
    }

    public void LaughTaleOnDestroy() {
        LaughTaleCancelRetry();
        LaughTale_isLoading = false;
        LaughTale_isShowing = false;
        LaughTale_adReady = false;
        String unitId = LaughTaleUnitId();
        if (unitId != null) {
            RewardedAdPreloader.destroy(unitId);
        }
        LaughTale_activity = null;
        LaughTale_rewardVideoListener = null;
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

    private static String LaughTaleResolveNetworkName(RewardedAd ad) {
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
