package com.dream.laughtale;

import android.app.Activity;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxInterstitialAd;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LaughTaleInterstitialAdapter implements MaxAdListener, MaxAdRevenueListener {
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();  // 创建一个共享的调度线程池
    private MaxInterstitialAd LaughTale_interstitialAd;
    private int LaughTale_interRetryAttempt;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleInterstitialListener LaughTale_InterstitialListener;
    private double LaughTale_adPrice = 0;

    public void LaughTaleInitInterstitialAdapter(){
        LaughTale_interstitialAd = new MaxInterstitialAd(LaughTale_ad_unit,LaughTale_activity);
        LaughTale_interstitialAd.setListener(this);
        LaughTale_interstitialAd.setRevenueListener(this);
    }

    public void LaughTaleLoadInterstitialAd(){
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_interstitialAd == null) {
            return;
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========","LOADINTER");
        LaughTale_interstitialAd.loadAd();
    }

    public double LaughTaleGetADPrice(){
        return LaughTale_adPrice;
    }

    public void LaughTaleShowInterstitialAd(){
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_interstitialAd == null) {
            return;
        }
        LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                // 显示插页广告
                LaughTale_interstitialAd.showAd(LaughTale_activity);
                // 重置广告价格
                LaughTale_adPrice = 0;
            }
        });
    }

    @Override
    public void onAdLoaded(MaxAd maxAd) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========","LOADINTERLoaded");
        LaughTale_adPrice = maxAd.getRevenue();
        if (LaughTale_adPrice == 0){
            LaughTale_adPrice = 0.01;
        }
        LaughTale_interRetryAttempt = 0;
    }

    @Override
    public void onAdDisplayed(MaxAd maxAd) {
        if (null != LaughTale_InterstitialListener){
            LaughTale_InterstitialListener.LaughTaleOnInterstitialAdDisplayed();
        }
    }

    @Override
    public void onAdHidden(MaxAd maxAd) {
        if (null != LaughTale_InterstitialListener){
            LaughTale_InterstitialListener.LaughTaleOnInterstitialAdClosed();
        }
        if (maxAd != null){
            maxAd = null;
        }
        LaughTaleLoadInterstitialAd();
    }

    @Override
    public void onAdClicked(MaxAd maxAd) {

    }

    @Override
    public void onAdLoadFailed(String s, MaxError maxError) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========","LOADINTERFailed");
        LaughTale_interRetryAttempt++;
        long delay = (long) Math.pow(2, Math.min(6, LaughTale_interRetryAttempt));
        scheduler.schedule(LaughTaleInterstitialAdapter.this::LaughTaleLoadInterstitialAd, delay, TimeUnit.SECONDS);
    }

    @Override
    public void onAdDisplayFailed(MaxAd maxAd, MaxError maxError) {
        LaughTaleLoadInterstitialAd();
    }

    @Override
    public void onAdRevenuePaid(MaxAd maxAd) {
        double revenue = maxAd.getRevenue(); // In USD
        String networkName = maxAd.getNetworkName(); // Display name of the network that showed the ad (e.g. "AdColony")
        String adUnitId = maxAd.getAdUnitId(); // The MAX Ad Unit ID
        MaxAdFormat adFormat = maxAd.getFormat(); // The ad format of the ad (e.g. BANNER, MREC, INTERSTITIAL, REWARDED)
//        String placement = maxAd.getPlacement(); // The placement this ad's postbacks are tied to
//        String networkPlacement = maxAd.getNetworkPlacement(); // The placement ID from the network that showed the ad

        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue,adFormat.toString(),networkName,adUnitId);
        //上报Bank 收入数据
//        LaughTaleBankManager.instance().updateAdStats("inter",revenue*1000);
    }
}
