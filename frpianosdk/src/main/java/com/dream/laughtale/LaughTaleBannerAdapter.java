package com.dream.laughtale;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;


//import com.applovin.impl.sdk.utils.AppLovinSdkExtraParameterKey;
import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxAdViewAdListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.ads.MaxAdView;
import com.applovin.sdk.AppLovinSdkUtils;

public class LaughTaleBannerAdapter implements MaxAdViewAdListener, MaxAdRevenueListener{

    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private MaxAdView LaughTale_adView;

    public void LaughTaleInitBannerAdapter() {
        this.LaughTale_adView = new MaxAdView(LaughTale_ad_unit,LaughTale_activity);
        this.LaughTale_adView.setListener(this);
        this.LaughTale_adView.setRevenueListener(this);
        int width = -1;
        int heightDp = MaxAdFormat.BANNER.getAdaptiveSize(this.LaughTale_activity).getHeight();
        int heightPx = AppLovinSdkUtils.dpToPx(this.LaughTale_activity, heightDp);
        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(width, heightPx);
        frameLayout.gravity = Gravity.BOTTOM;
        this.LaughTale_adView.setLayoutParams(frameLayout);
        this.LaughTale_adView.getAdFormat().getAdaptiveSize(400, this.LaughTale_activity).getHeight();
        this.LaughTale_adView.setExtraParameter("adaptive_banner", "true");
        this.LaughTale_adView.setBackgroundColor(Color.TRANSPARENT);
        this.LaughTale_adView.setVisibility(View.GONE);
        ViewGroup rootView = (ViewGroup)this.LaughTale_activity.findViewById(android.R.id.content);
        rootView.addView(this.LaughTale_adView);
    }

    public void LaughTaleLoadBannerView(){
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_adView == null) {
            return;
        }
        LaughTale_adView.loadAd();
    }

    public void LaughTaleShowBannerView() {
        if (null != LaughTaleBannerAdapter.this.LaughTale_adView){
            if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
                return;
            }
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerShouldShow");
            this.LaughTale_activity.runOnUiThread(new Runnable() {
                public void run() {
                    LaughTaleBannerAdapter.this.LaughTale_adView.setVisibility(View.VISIBLE);
                }
            });
            LaughTale_adView.startAutoRefresh();
        }
    }

    public void LaughTaleHideBannerView() {
        if (null != LaughTaleBannerAdapter.this.LaughTale_adView){
            if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
                return;
            }
            this.LaughTale_activity.runOnUiThread(new Runnable() {
                public void run() {
                    LaughTale_adView.setVisibility(View.GONE);
                }
            });
            LaughTale_adView.stopAutoRefresh();
            LaughTaleLoadBannerView();
        }
    }

    public void onAdExpanded(MaxAd maxAd) {

    }

    public void onAdCollapsed(MaxAd maxAd) {

    }

    public void onAdLoaded(MaxAd maxAd) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerLoaded:"+maxAd.getNetworkName());
    }

    public void onAdDisplayed(MaxAd maxAd) {

    }

    public void onAdHidden(MaxAd maxAd) {

    }

    public void onAdClicked(MaxAd maxAd) {
    }

    public void onAdLoadFailed(String s, MaxError maxError) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerLoadFailed"+maxError.getMessage());
    }

    public void onAdDisplayFailed(MaxAd maxAd, MaxError maxError) {
    }

    public void onAdRevenuePaid(MaxAd maxAd) {
        double revenue = maxAd.getRevenue();
        String networkName = maxAd.getNetworkName();
        String adUnitId = maxAd.getAdUnitId();
        MaxAdFormat adFormat = maxAd.getFormat();
        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue, adFormat.toString(), networkName, adUnitId);
    }
}
