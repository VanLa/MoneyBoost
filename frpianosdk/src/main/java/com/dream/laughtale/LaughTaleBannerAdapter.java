package com.dream.laughtale;

import android.app.Activity;
import android.graphics.Color;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
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

public class LaughTaleBannerAdapter {

    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private AdView LaughTale_adView;
    private BannerAd LaughTale_bannerAd;
    private boolean LaughTale_isLoaded = false;
    private boolean LaughTale_isLoading = false;

    public boolean LaughTaleIsLoaded() {
        return LaughTale_isLoaded;
    }

    public boolean LaughTaleIsLoading() {
        return LaughTale_isLoading;
    }

    public void LaughTaleInitBannerAdapter() {
        this.LaughTale_adView = new AdView(LaughTale_activity);
        int widthDp = LaughTaleGetScreenWidthDp(LaughTale_activity);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(LaughTale_activity, widthDp);
        if (adSize == null) {
            adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(LaughTale_activity, widthDp);
        }
        this.LaughTale_adView.resize(adSize);
        int heightPx = adSize.getHeightInPixels(LaughTale_activity);
        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
        frameLayout.gravity = Gravity.BOTTOM;
        this.LaughTale_adView.setLayoutParams(frameLayout);
        this.LaughTale_adView.setBackgroundColor(Color.TRANSPARENT);
        this.LaughTale_adView.setVisibility(View.GONE);
        ViewGroup rootView = (ViewGroup) this.LaughTale_activity.findViewById(android.R.id.content);
        rootView.addView(this.LaughTale_adView);
    }

    public void LaughTaleLoadBannerView() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        // resize/loadAd 必须主线程；开屏 dismiss 可能从 GMA(BG) 回调进来
        if (Looper.myLooper() != Looper.getMainLooper()) {
            LaughTale_activity.runOnUiThread(this::LaughTaleLoadBannerView);
            return;
        }
        if (LaughTale_adView == null || LaughTale_ad_unit == null || LaughTale_ad_unit.trim().isEmpty()) {
            return;
        }
        if (LaughTale_isLoading || LaughTale_isLoaded) {
            return;
        }
        LaughTale_isLoading = true;
        int widthDp = LaughTaleGetScreenWidthDp(LaughTale_activity);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(LaughTale_activity, widthDp);
        if (adSize == null) {
            adSize = AdSize.getLargeAnchoredAdaptiveBannerAdSize(LaughTale_activity, widthDp);
        }
        LaughTale_adView.resize(adSize);
        BannerAdRequest adRequest = new BannerAdRequest.Builder(LaughTale_ad_unit.trim(), adSize).build();
        LaughTale_adView.loadAd(adRequest, new AdLoadCallback<BannerAd>() {
            @Override
            public void onAdLoaded(@NonNull BannerAd bannerAd) {
                LaughTale_isLoading = false;
                LaughTale_onBannerLoaded(bannerAd);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                LaughTale_isLoading = false;
                LaughTale_isLoaded = false;
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerLoadFailed" + adError.getMessage());
            }
        });
    }

    private void LaughTale_onBannerLoaded(@NonNull BannerAd bannerAd) {
        LaughTale_isLoaded = true;
        if (LaughTale_bannerAd != null) {
            LaughTale_bannerAd.destroy();
        }
        LaughTale_bannerAd = bannerAd;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=========", "BannerLoaded:" + LaughTaleResolveNetworkName(bannerAd));
        bannerAd.setAdEventCallback(new BannerAdEventCallback() {
            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                double revenue = adValue.getValueMicros() / 1_000_000.0;
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(
                        revenue, "BANNER", LaughTaleResolveNetworkName(bannerAd), LaughTale_ad_unit);
            }
        });
        if (LaughTale_adView == null || LaughTale_activity == null
                || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        AdSize loadedSize = bannerAd.getAdSize();
        if (loadedSize == null) {
            return;
        }
        final int heightPx = loadedSize.getHeightInPixels(LaughTale_activity);
        LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                if (LaughTale_adView == null) {
                    return;
                }
                ViewGroup.LayoutParams params = LaughTale_adView.getLayoutParams();
                if (params != null && params.height != heightPx) {
                    params.height = heightPx;
                    LaughTale_adView.setLayoutParams(params);
                }
                // 加载完成后再 Sync：Init 前 Unity 调 ShowBanner 时 adapter 还空，否则会一直 GONE 到插屏关闭
                LaughTaleMediationManager.getInstance().LaughTaleOnBannerAdLoaded();
            }
        });
    }

    public void LaughTaleShowBannerView() {
        if (LaughTale_adView == null) {
            return;
        }
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerShouldShow");
        this.LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                if (LaughTale_adView == null) {
                    return;
                }
                if (!LaughTale_isLoaded) {
                    LaughTaleLoadBannerView();
                }
                LaughTale_adView.setVisibility(View.VISIBLE);
            }
        });
    }

    public void LaughTaleHideBannerView() {
        if (LaughTale_adView == null) {
            return;
        }
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        this.LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                LaughTale_adView.setVisibility(View.GONE);
            }
        });
    }

    public void LaughTalePauseAutoRefresh() {
        // GMA Next-Gen banner auto-refresh is controlled in AdMob console; no-op here.
    }

    public void LaughTaleResumeAutoRefresh() {
        // GMA Next-Gen banner auto-refresh is controlled in AdMob console; no-op here.
    }

    public void LaughTaleOnDestroy() {
        LaughTale_isLoaded = false;
        LaughTale_isLoading = false;
        if (LaughTale_bannerAd != null) {
            LaughTale_bannerAd.destroy();
            LaughTale_bannerAd = null;
        }
        if (LaughTale_adView != null) {
            ViewGroup parent = LaughTale_adView.getParent() instanceof ViewGroup
                    ? (ViewGroup) LaughTale_adView.getParent() : null;
            if (parent != null) {
                parent.removeView(LaughTale_adView);
            }
            LaughTale_adView.destroy();
            LaughTale_adView = null;
        }
        LaughTale_activity = null;
    }

    private static int LaughTaleGetScreenWidthDp(Activity activity) {
        DisplayMetrics metrics = activity.getResources().getDisplayMetrics();
        return (int) (metrics.widthPixels / metrics.density);
    }

    private static String LaughTaleResolveNetworkName(BannerAd ad) {
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
