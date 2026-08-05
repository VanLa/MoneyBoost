package com.dream.laughtale;

import android.app.Activity;


import androidx.annotation.NonNull;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxAppOpenAd;


public class LaughTaleSplashAdapter implements MaxAdListener {
    private MaxAppOpenAd LaughTale_splashOpenAd;
    public Activity LaughTale_activity;
    public String LaughTale_ad_unit;
    private boolean LaughTale_isReady = false;
    public boolean LaughTale_autoShow = false;
    public String LaughTale_pendingScene = "launch";
    public LaughTaleSplashListener LaughTale_splashListener;
    private int LaughTale_splashRetryAttempt;
    private double LaughTale_adPrice = 0;
    private boolean LaughTale_isShowing = false;

    public boolean LaughTaleIsShowing() {
        return LaughTale_isShowing;
    }

    public void LaughTaleInitSplashAdapter(){
        LaughTale_splashOpenAd = new MaxAppOpenAd(LaughTale_ad_unit,LaughTale_activity);
        LaughTale_splashOpenAd.setListener(this);
    }

    public void LaughTaleLoadSplashAD(){
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_splashOpenAd == null) {
            return;
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("======","LOADSplash");
        LaughTale_splashOpenAd.loadAd();
    }

    public Boolean LaughTaleIsADReady(){
        return LaughTale_isReady;
    }

    public double LaughTaleGetADPrice() {
        return LaughTale_isReady ? LaughTale_adPrice : 0;
    }

    public void LaughTaleShowSplashAd(String scene)
    {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_splashOpenAd == null) {
            return;
        }
        if (!LaughTale_isReady) {
            LaughTaleLoadSplashAD();
            return;
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleSplashAdapter","showSplashAd scene=" + scene);
        LaughTale_splashOpenAd.showAd();
        LaughTale_isReady = false;
        LaughTale_adPrice = 0;
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd maxAd) {
        LaughTale_splashRetryAttempt = 0;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleSplashAdapter","onSplashAdLoaded");
        LaughTale_adPrice = maxAd.getRevenue();
        if (LaughTale_adPrice == 0) {
            LaughTale_adPrice = 0.02;
        }
        LaughTale_isReady = true;
        if (LaughTale_autoShow) {
            LaughTale_autoShow = false;
            if (LaughTale_splashListener != null) {
                LaughTale_splashListener.LaughTaleOnSplashAdReadyForAutoShow(LaughTale_pendingScene);
            }
        }
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd maxAd) {
        LaughTale_isShowing = true;
        if (null != LaughTale_splashListener){
            LaughTale_splashListener.LaughTaleOnSplashAdDisplayed();
        }
    }

    @Override
    public void onAdLoadFailed(@NonNull String s, @NonNull MaxError maxError) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========","LOADSplashFailed");
        LaughTale_splashRetryAttempt++;
        LaughTaleAdRetryScheduler.scheduleSeconds(LaughTaleSplashAdapter.this::LaughTaleLoadSplashAD,
                (long) Math.pow(2, Math.min(6, LaughTale_splashRetryAttempt)));
    }

    @Override
    public void onAdHidden(@NonNull MaxAd maxAd) {
        LaughTale_isShowing = false;
        if (null != LaughTale_splashListener){
            LaughTale_splashListener.LaughTaleOnSplashAdClosed();
        }
        LaughTaleLoadSplashAD();
    }

    @Override
    public void onAdClicked(@NonNull MaxAd maxAd) {

    }



    @Override
    public void onAdDisplayFailed(@NonNull MaxAd maxAd, @NonNull MaxError maxError) {
        LaughTale_isShowing = false;
        if (null != LaughTale_splashListener){
            LaughTale_splashListener.LaughTaleOnSplashAdDisplayFailed();
        }
        LaughTaleLoadSplashAD();
    }
}
