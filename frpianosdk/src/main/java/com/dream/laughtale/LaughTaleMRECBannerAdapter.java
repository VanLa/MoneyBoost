package com.dream.laughtale;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
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
    private boolean LaughTale_isLoaded = false;
    /** Unity 主动 show 前禁止展示，避免 prefetch/加载回调自动弹出 */
    private boolean LaughTale_showRequested = false;
    private int LaughTale_pendingShowType = 0;
    private static final long PREFETCH_DELAY_MS = 1000L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable prefetchRunnable = new Runnable() {
        @Override
        public void run() {
            if (LaughTale_adView != null && !LaughTale_showRequested) {
                LaughTaleLoadMRECView();
            }
        }
    };

    public boolean LaughTaleIsLoaded() {
        return LaughTale_isLoaded;
    }

    public boolean LaughTaleIsVisible() {
        return LaughTale_adView != null && LaughTale_adView.getVisibility() == View.VISIBLE;
    }

    public boolean LaughTaleIsShowRequested() {
        return LaughTale_showRequested;
    }

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
        if (LaughTale_adView == null) {
            return;
        }
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        LaughTale_showRequested = true;
        LaughTale_pendingShowType = type;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerShouldShow");
        mainHandler.removeCallbacks(prefetchRunnable);
        if (!LaughTale_isLoaded) {
            LaughTaleLoadMRECView();
        }
        LaughTaleRunOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (!LaughTale_showRequested || LaughTale_adView == null) {
                    return;
                }
                LaughTaleApplyLayoutForType(LaughTale_pendingShowType);
                LaughTale_adView.setVisibility(View.VISIBLE);
                LaughTale_adView.startAutoRefresh();
            }
        });
    }

    public void LaughTaleHideMRECView() {
        if (LaughTale_adView == null) {
            return;
        }
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        LaughTale_showRequested = false;
        mainHandler.removeCallbacks(prefetchRunnable);
        LaughTaleRunOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (LaughTale_adView == null) {
                    return;
                }
                LaughTale_adView.setVisibility(View.GONE);
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
                params.topMargin = 0;
                params.bottomMargin = 0;
                params.gravity = Gravity.CENTER;
                LaughTale_adView.setLayoutParams(params);
                LaughTale_adView.stopAutoRefresh();
            }
        });
        mainHandler.postDelayed(prefetchRunnable, PREFETCH_DELAY_MS);
    }

    public void LaughTalePauseAutoRefresh() {
        if (LaughTale_adView != null && LaughTale_showRequested && LaughTale_adView.getVisibility() == View.VISIBLE) {
            LaughTale_adView.stopAutoRefresh();
        }
    }

    public void LaughTaleResumeAutoRefresh() {
        if (LaughTale_adView != null && LaughTale_showRequested && LaughTale_adView.getVisibility() == View.VISIBLE) {
            LaughTale_adView.startAutoRefresh();
        }
    }

    private void LaughTaleApplyLayoutForType(int type) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
        if (type == 3) {
            params.gravity = Gravity.CENTER;
            params.topMargin = AppLovinSdkUtils.dpToPx(LaughTale_activity, 100);
            params.bottomMargin = 0;
        } else if (type == 1) {
            params.gravity = Gravity.CENTER;
            params.topMargin = -AppLovinSdkUtils.dpToPx(LaughTale_activity, 150);
            params.bottomMargin = 0;
        } else if (type == 4) {
            params.gravity = Gravity.BOTTOM;
            params.topMargin = 0;
            params.bottomMargin = 180;
        } else {
            params.gravity = Gravity.CENTER;
            params.topMargin = 0;
            params.bottomMargin = 0;
        }
        LaughTale_adView.setLayoutParams(params);
    }

    private void LaughTaleEnforceHiddenIfNotRequested() {
        if (LaughTale_showRequested || LaughTale_adView == null) {
            return;
        }
        LaughTaleRunOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (LaughTale_showRequested || LaughTale_adView == null) {
                    return;
                }
                LaughTale_adView.setVisibility(View.GONE);
                LaughTale_adView.stopAutoRefresh();
            }
        });
    }

    private void LaughTaleRunOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else if (LaughTale_activity != null) {
            LaughTale_activity.runOnUiThread(runnable);
        } else {
            mainHandler.post(runnable);
        }
    }

    public void onAdExpanded(MaxAd maxAd) {
        LaughTaleEnforceHiddenIfNotRequested();
    }

    public void onAdCollapsed(MaxAd maxAd) {
        LaughTaleEnforceHiddenIfNotRequested();
    }

    public void onAdLoaded(MaxAd maxAd) {
        LaughTale_isLoaded = true;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerLoaded:"+maxAd.getNetworkName());
        LaughTaleEnforceHiddenIfNotRequested();
    }

    public void onAdDisplayed(MaxAd maxAd) {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerDisplayed");
        LaughTaleEnforceHiddenIfNotRequested();
    }

    public void onAdHidden(MaxAd maxAd) {
    }

    public void onAdClicked(MaxAd maxAd) {
    }

    public void onAdLoadFailed(String s, MaxError maxError) {
        LaughTale_isLoaded = false;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerLoadFailed"+maxError.getMessage());
    }

    public void onAdDisplayFailed(MaxAd maxAd, MaxError maxError) {
        LaughTaleEnforceHiddenIfNotRequested();
    }

    public void onAdRevenuePaid(MaxAd maxAd) {
        if (!LaughTale_showRequested) {
            LaughTaleEnforceHiddenIfNotRequested();
            return;
        }
        double revenue = maxAd.getRevenue();
        String networkName = maxAd.getNetworkName();
        String adUnitId = maxAd.getAdUnitId();
        MaxAdFormat adFormat = maxAd.getFormat();
        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue, adFormat.toString(), networkName, adUnitId);
    }

}
