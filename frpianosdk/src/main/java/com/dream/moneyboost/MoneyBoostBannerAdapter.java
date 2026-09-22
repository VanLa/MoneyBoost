package com.dream.moneyboost;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;

public class MoneyBoostBannerAdapter {

    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    private AdView MoneyBoost_adView;
    private BannerAd MoneyBoost_bannerAd;
    private boolean MoneyBoost_isLoaded = false;
    private boolean MoneyBoost_isLoading = false;
    private boolean MoneyBoost_collapsibleRequest = false;

    public boolean MoneyBoostIsLoaded() {
        return MoneyBoost_isLoaded;
    }

    public boolean MoneyBoostIsLoading() {
        return MoneyBoost_isLoading;
    }

    public void MoneyBoostInitBannerAdapter() {
        this.MoneyBoost_adView = new AdView(MoneyBoost_activity);
        int widthDp = MoneyBoostGetScreenWidthDp(MoneyBoost_activity);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        if (adSize == null) {
            adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        }
        this.MoneyBoost_adView.resize(adSize);
        int heightPx = adSize.getHeightInPixels(MoneyBoost_activity);
        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
        frameLayout.gravity = Gravity.BOTTOM;
        this.MoneyBoost_adView.setLayoutParams(frameLayout);
        this.MoneyBoost_adView.setBackgroundColor(Color.TRANSPARENT);
        this.MoneyBoost_adView.setVisibility(View.GONE);
        ViewGroup rootView = (ViewGroup) this.MoneyBoost_activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        // 防止重复 Init / Activity 重建后 again addView 触发 already has a parent
        ViewParent existingParent = this.MoneyBoost_adView.getParent();
        if (existingParent instanceof ViewGroup) {
            ((ViewGroup) existingParent).removeView(this.MoneyBoost_adView);
        }
        if (this.MoneyBoost_adView.getParent() == null) {
            rootView.addView(this.MoneyBoost_adView, frameLayout);
        }
    }

    /** Loads an AdMob collapsible banner anchored at the bottom. */
    public void MoneyBoostLoadCollapsibleBannerView() {
        MoneyBoost_collapsibleRequest = true;
        // A collapsible request must be explicit even when a regular banner is cached.
        MoneyBoost_isLoaded = false;
        MoneyBoostLoadBannerView();
    }

    public void MoneyBoostLoadBannerView() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        // resize/loadAd 必须主线程；开屏 dismiss 可能从 GMA(BG) 回调进来
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MoneyBoost_activity.runOnUiThread(this::MoneyBoostLoadBannerView);
            return;
        }
        if (MoneyBoost_adView == null || MoneyBoost_ad_unit == null || MoneyBoost_ad_unit.trim().isEmpty()) {
            return;
        }
        if (MoneyBoost_isLoading || MoneyBoost_isLoaded) {
            return;
        }
        MoneyBoost_isLoading = true;
        int widthDp = MoneyBoostGetScreenWidthDp(MoneyBoost_activity);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        if (adSize == null) {
            adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        }
        MoneyBoost_adView.resize(adSize);
        BannerAdRequest.Builder requestBuilder = new BannerAdRequest.Builder(MoneyBoost_ad_unit.trim(), adSize);
        if (MoneyBoost_collapsibleRequest) {
            Bundle extras = new Bundle();
            extras.putString("collapsible", "bottom");
            requestBuilder.setGoogleExtrasBundle(extras);
            MoneyBoost_collapsibleRequest = false;
        }
        BannerAdRequest adRequest = requestBuilder.build();
        MoneyBoost_adView.loadAd(adRequest, new AdLoadCallback<BannerAd>() {
            @Override
            public void onAdLoaded(@NonNull BannerAd bannerAd) {
                MoneyBoost_isLoading = false;
                MoneyBoost_onBannerLoaded(bannerAd);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                MoneyBoost_isLoading = false;
                MoneyBoost_isLoaded = false;
                MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "BannerLoadFailed" + adError.getMessage());
            }
        });
    }

    private void MoneyBoost_onBannerLoaded(@NonNull BannerAd bannerAd) {
        MoneyBoost_isLoaded = true;
        if (MoneyBoost_bannerAd != null) {
            MoneyBoost_bannerAd.destroy();
        }
        MoneyBoost_bannerAd = bannerAd;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                "=========", "BannerLoaded:" + MoneyBoostResolveNetworkName(bannerAd)
                        + " collapsible=" + bannerAd.isCollapsible());
        bannerAd.setAdEventCallback(new BannerAdEventCallback() {
            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                double revenue = adValue.getValueMicros() / 1_000_000.0;
                MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseRevenue(
                        revenue, "BANNER", MoneyBoostResolveNetworkName(bannerAd), MoneyBoost_ad_unit);
            }
        });
        if (MoneyBoost_adView == null || MoneyBoost_activity == null
                || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        AdSize loadedSize = bannerAd.getAdSize();
        if (loadedSize == null) {
            return;
        }
        final int heightPx = loadedSize.getHeightInPixels(MoneyBoost_activity);
        MoneyBoost_activity.runOnUiThread(new Runnable() {
            public void run() {
                if (MoneyBoost_adView == null) {
                    return;
                }
                ViewGroup.LayoutParams params = MoneyBoost_adView.getLayoutParams();
                if (params != null && params.height != heightPx) {
                    params.height = heightPx;
                    MoneyBoost_adView.setLayoutParams(params);
                }
                // 加载完成后再 Sync：Init 前 Unity 调 ShowBanner 时 adapter 还空，否则会一直 GONE 到插屏关闭
                MoneyBoostMediationManager.getInstance().MoneyBoostOnBannerAdLoaded();
            }
        });
    }

    public void MoneyBoostShowBannerView() {
        if (MoneyBoost_adView == null) {
            return;
        }
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "BannerShouldShow");
        this.MoneyBoost_activity.runOnUiThread(new Runnable() {
            public void run() {
                if (MoneyBoost_adView == null) {
                    return;
                }
                if (!MoneyBoost_isLoaded) {
                    MoneyBoostLoadBannerView();
                }
                MoneyBoost_adView.setVisibility(View.VISIBLE);
            }
        });
    }

    public boolean MoneyBoostIsVisible() {
        return MoneyBoost_adView != null && MoneyBoost_adView.getVisibility() == View.VISIBLE;
    }

    public void MoneyBoostHideBannerView() {
        if (MoneyBoost_adView == null) {
            return;
        }
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        this.MoneyBoost_activity.runOnUiThread(new Runnable() {
            public void run() {
                MoneyBoost_adView.setVisibility(View.GONE);
            }
        });
    }

    /**
     * 半屏遮罩底部预留高度（px），避免 #b3000000 盖住 Banner。
     * 优先用当前 AdView 实测高度，否则用自适应 Banner 估算（+5dp，对齐历史 b2x）。
     */
    public int MoneyBoostGetBannerReserveHeightPx() {
        if (MoneyBoost_activity == null) {
            return 0;
        }
        if (MoneyBoost_adView != null && MoneyBoost_adView.getHeight() > 0) {
            return MoneyBoost_adView.getHeight() + dp2px(5);
        }
        int widthDp = MoneyBoostGetScreenWidthDp(MoneyBoost_activity);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        if (adSize == null) {
            adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        }
        if (adSize == null) {
            return dp2px(50 + 5);
        }
        return adSize.getHeightInPixels(MoneyBoost_activity) + dp2px(5);
    }

    /** 半屏展示后把 Banner 提到最前，保证露在遮罩下方的区域可点。 */
    public void MoneyBoostBringBannerToFront() {
        if (MoneyBoost_adView == null) {
            return;
        }
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        Runnable bring = () -> {
            if (MoneyBoost_adView != null) {
                MoneyBoost_adView.bringToFront();
            }
        };
        if (Looper.myLooper() == Looper.getMainLooper()) {
            bring.run();
        } else {
            MoneyBoost_activity.runOnUiThread(bring);
        }
    }

    private int dp2px(float dp) {
        return (int) ((dp * MoneyBoost_activity.getResources().getDisplayMetrics().density) + 0.5f);
    }

    public void MoneyBoostPauseAutoRefresh() {
        // GMA Next-Gen banner auto-refresh is controlled in AdMob console; no-op here.
    }

    public void MoneyBoostResumeAutoRefresh() {
        // GMA Next-Gen banner auto-refresh is controlled in AdMob console; no-op here.
    }

    public void MoneyBoostOnDestroy() {
        MoneyBoost_isLoaded = false;
        MoneyBoost_isLoading = false;
        if (MoneyBoost_bannerAd != null) {
            MoneyBoost_bannerAd.destroy();
            MoneyBoost_bannerAd = null;
        }
        if (MoneyBoost_adView != null) {
            ViewGroup parent = MoneyBoost_adView.getParent() instanceof ViewGroup
                    ? (ViewGroup) MoneyBoost_adView.getParent() : null;
            if (parent != null) {
                parent.removeView(MoneyBoost_adView);
            }
            MoneyBoost_adView.destroy();
            MoneyBoost_adView = null;
        }
        MoneyBoost_activity = null;
    }

    private static int MoneyBoostGetScreenWidthDp(Activity activity) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return (int) (metrics.widthPixels / metrics.density);
    }

    private static String MoneyBoostResolveNetworkName(BannerAd ad) {
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
