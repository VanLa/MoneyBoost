package com.joyboost.moneyboost;

import android.app.Activity;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.rewarded.OnUserEarnedRewardListener;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardItem;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;

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

    private RewardedAd MoneyBoost_rewardedAd;
    private final AdLoadCallback<RewardedAd> MoneyBoost_loadCallback = new AdLoadCallback<RewardedAd>() {
        @Override
        public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADREWARDLoaded");
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = true;
            MoneyBoost_rewardedAd = rewardedAd;
            MoneyBoost_rewardRetryAttempt = 0;
            MoneyBoostCancelRetry();
        }

        @Override
        public void onAdFailedToLoad(@NonNull LoadAdError adError) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========",
                    "LOADREWARDFailed code=" + adError.getCode()
                            + " message=" + adError.getMessage());
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = false;
            MoneyBoost_rewardedAd = null;
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
        return MoneyBoost_rewardedAd != null;
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
        if (MoneyBoost_isLoading || MoneyBoost_isShowing || MoneyBoost_rewardedAd != null) {
            return;
        }
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = true;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADREWARD");
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        RewardedAd.load(adRequest, MoneyBoost_loadCallback);
    }

    public void MoneyBoostShowRewardVideoAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        final String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            return;
        }
        if (MoneyBoost_rewardedAd == null) {
            return;
        }
        MoneyBoost_activity.runOnUiThread(new Runnable() {
            public void run() {
                final RewardedAd rewardedAd = MoneyBoost_rewardedAd;
                MoneyBoost_rewardedAd = null;
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
        return MoneyBoost_adReady && MoneyBoost_rewardedAd != null;
    }

    public void MoneyBoostOnDestroy() {
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = false;
        MoneyBoost_isShowing = false;
        MoneyBoost_adReady = false;
        if (MoneyBoost_rewardedAd != null) {
            MoneyBoost_rewardedAd.destroy();
            MoneyBoost_rewardedAd = null;
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
