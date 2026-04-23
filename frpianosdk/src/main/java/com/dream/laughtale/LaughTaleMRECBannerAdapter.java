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

public class LaughTaleMRECBannerAdapter implements MaxAdViewAdListener, MaxAdRevenueListener{

    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private MaxAdView LaughTale_adView;

    public void LaughTaleInitBannerAdapter() {
        this.LaughTale_adView = new MaxAdView(this.LaughTale_ad_unit,MaxAdFormat.MREC,LaughTale_activity);
        this.LaughTale_adView.setListener(this);
        this.LaughTale_adView.setRevenueListener(this);

        int width = -1;
        int heightPx = AppLovinSdkUtils.dpToPx(this.LaughTale_activity, 250);

        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(width, heightPx);
        frameLayout.gravity = Gravity.CENTER;
        this.LaughTale_adView.setLayoutParams(frameLayout);
        this.LaughTale_adView.setBackgroundColor(Color.TRANSPARENT);
        this.LaughTale_adView.setVisibility(View.GONE);
        this.LaughTale_adView.setExtraParameter("adaptive_banner", "true");
        this.LaughTale_adView.stopAutoRefresh();


        ViewGroup rootView = (ViewGroup)this.LaughTale_activity.findViewById(android.R.id.content);
        rootView.addView(this.LaughTale_adView);

    }

    public void LaughTaleLoadMRECView(){
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_adView == null) {
            return;
        }
        LaughTale_adView.loadAd();
    }

    public void LaughTaleShowMRECView(int type) {
        if (null != LaughTaleMRECBannerAdapter.this.LaughTale_adView){
            if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
                return;
            }
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerShouldShow");
            this.LaughTale_activity.runOnUiThread(new Runnable() {
                public void run() {
                    // 如果 type == 3, 调整 LaughTale_adView 位置
                    if (type == 3) {
                        int marginTopPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, 100);  // 向下移动 50 dp
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
                        params.gravity = Gravity.CENTER; // 确保其他情况重置为居中
                        params.topMargin = marginTopPx;
                        LaughTale_adView.setLayoutParams(params);
                    }else if (type == 1) {
                        int marginTopPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, 150);  // 向上移动 50 dp
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
                        params.gravity = Gravity.CENTER; // 确保其他情况重置为居中
                        params.topMargin = - marginTopPx;
                        LaughTale_adView.setLayoutParams(params);
                    }else if (type == 4){
                        // 设置到屏幕底部
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
                        params.gravity = Gravity.BOTTOM; // 重力设置为底部
                        params.topMargin = 0; // 清除顶部边距
                        params.bottomMargin = 180; // 可选的底部边距，根据需求调整
                        LaughTale_adView.setLayoutParams(params);
                    } else {
                        // 默认居中显示
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
                        params.gravity = Gravity.CENTER; // 明确居中
                        params.topMargin = 0;
                        LaughTale_adView.setLayoutParams(params);
                    }

                    LaughTale_adView.setVisibility(View.VISIBLE);
                }
            });
            LaughTale_adView.startAutoRefresh();
        }
    }

    public void LaughTaleHideMRECView() {
        if (null != LaughTaleMRECBannerAdapter.this.LaughTale_adView){
            if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
                return;
            }
            this.LaughTale_activity.runOnUiThread(new Runnable() {
                public void run() {
                    LaughTale_adView.setVisibility(View.GONE);
                    // 恢复为屏幕中心位置
                    FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
                    params.topMargin = 0;  // 恢复为屏幕中心
                    params.gravity = Gravity.CENTER; // 明确居中
                    LaughTale_adView.setLayoutParams(params);
                }
            });
            LaughTale_adView.stopAutoRefresh();
            LaughTaleLoadMRECView();
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
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerDisplayed");
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
