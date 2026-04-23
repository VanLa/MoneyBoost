package com.dream.laughtale;

import android.app.Activity;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.MaxReward;
import com.applovin.mediation.MaxRewardedAdListener;
import com.applovin.mediation.ads.MaxRewardedAd;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LaughTaleRewardVideoAdapter implements MaxRewardedAdListener, MaxAdRevenueListener {
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();  // 创建一个共享的调度线程池
    private MaxRewardedAd LaughTale_rewardedAd;
    private int LaughTale_rewardRetryAttempt;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleRewardVideoListener LaughTale_rewardVideoListener;
    private double LaughTale_adPrice = 0;

    public void LaughTaleInitRewardVideoAdapter() {
        LaughTale_rewardedAd = MaxRewardedAd.getInstance(LaughTale_ad_unit, LaughTale_activity);
        LaughTale_rewardedAd.setListener(this);
        LaughTale_rewardedAd.setRevenueListener(this);
    }

    public void LaughTaleLoadRewardVideoAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_rewardedAd == null) {
            return;
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADREWARD");
        LaughTale_rewardedAd.loadAd();
    }

    public void LaughTaleShowRewardVideoAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_rewardedAd == null) {
            return;
        }
        if (LaughTale_rewardedAd.isReady()){
            LaughTale_activity.runOnUiThread(new Runnable() {
                public void run() {
                    LaughTale_rewardedAd.showAd(LaughTale_activity);
                }
            });
            LaughTale_adPrice = 0;
        }
    }

    public double LaughTaleGetADPrice() {
        return LaughTale_adPrice;
    }

    @Override
    public void onUserRewarded(MaxAd maxAd, MaxReward maxReward) {
        if (null != LaughTale_rewardVideoListener) {
            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdCompleted();
        }
    }

    @Override
    public void onAdLoaded(MaxAd maxAd) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADREWARDLoaded:" + maxAd.getRevenue());
        LaughTale_adPrice = maxAd.getRevenue();
        if (LaughTale_adPrice == 0) {
            LaughTale_adPrice = 0.01;
        }
        LaughTale_rewardRetryAttempt = 0;
    }

    @Override
    public void onAdDisplayed(MaxAd maxAd) {
        if (null != LaughTale_rewardVideoListener) {
            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdDisplayed();
        }
    }

    @Override
    public void onAdHidden(MaxAd maxAd) {
        if (null != LaughTale_rewardVideoListener) {
            LaughTale_rewardVideoListener.LaughTaleOnRewardVideoAdClosed();
        }
        LaughTaleLoadRewardVideoAd(); // 关闭后重新加载广告
    }

    @Override
    public void onAdClicked(MaxAd maxAd) {

    }

    @Override
    public void onAdLoadFailed(String s, MaxError maxError) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADREWARDFailed");
        LaughTale_rewardRetryAttempt++;
        // 使用不同的退避策略：低端设备用 4 的幂，高端设备用 2 的幂，最大延迟 64 秒
        long delay = (long) Math.pow(2, Math.min(6, LaughTale_rewardRetryAttempt));

        scheduler.schedule(LaughTaleRewardVideoAdapter.this::LaughTaleLoadRewardVideoAd, delay, TimeUnit.SECONDS);
    }

    @Override
    public void onAdDisplayFailed(MaxAd maxAd, MaxError maxError) {
        LaughTaleLoadRewardVideoAd();
    }

    @Override
    public void onAdRevenuePaid(MaxAd maxAd) {
        double revenue = maxAd.getRevenue(); // In USD
        String networkName = maxAd.getNetworkName(); // Display name of the network that showed the ad (e.g. "AdColony")
        String adUnitId = maxAd.getAdUnitId(); // The MAX Ad Unit ID
        MaxAdFormat adFormat = maxAd.getFormat(); // The ad format of the ad (e.g. BANNER, MREC, INTERSTITIAL, REWARDED)

        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue, adFormat.toString(), networkName, adUnitId);
    }
}
