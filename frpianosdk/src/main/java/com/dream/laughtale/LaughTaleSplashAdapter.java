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
    public LaughTaleSplashListener LaughTale_splashListener;
    private int LaughTale_splashRetryAttempt;

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

    public void LaughTaleShowSplashAd(String scene)
    {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_splashOpenAd == null) {
            return;
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleSplashAdapter","showSplashAd");
        LaughTale_splashOpenAd.showAd();
        LaughTale_isReady = false;
    }

    @Override
    public void onAdLoaded(@NonNull MaxAd maxAd) {
        LaughTale_splashRetryAttempt = 0;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleSplashAdapter","onSplashAdLoaded");
        LaughTale_isReady = true;
        if (LaughTale_autoShow) {
            LaughTale_autoShow = false;
            LaughTaleShowSplashAd("launch");
        }
    }

    @Override
    public void onAdDisplayed(@NonNull MaxAd maxAd) {
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
        LaughTaleLoadSplashAD();
    }
}
