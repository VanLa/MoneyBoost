package com.dream.laughtale;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
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

public class LaughTaleMRECBannerAdapter {

    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private AdView LaughTale_adView;
    private BannerAd LaughTale_bannerAd;
    private boolean LaughTale_isLoaded = false;
    /** Unity 主动 show 前禁止展示，避免 prefetch/加载回调自动弹出 */
    private boolean LaughTale_showRequested = false;
    private int LaughTale_pendingShowType = 0;
    /** hide 后延后补货，避免短间隔反复 load */
    private static final long PREFETCH_DELAY_MS = 5000L;
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
        this.LaughTale_adView = new AdView(this.LaughTale_activity);
        AdSize adSize = AdSize.MEDIUM_RECTANGLE;
        this.LaughTale_adView.resize(adSize);
        int heightPx = adSize.getHeightInPixels(this.LaughTale_activity);

        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
        frameLayout.gravity = Gravity.CENTER;
        this.LaughTale_adView.setLayoutParams(frameLayout);
        this.LaughTale_adView.setBackgroundColor(Color.TRANSPARENT);
        this.LaughTale_adView.setVisibility(View.GONE);

        ViewGroup rootView = (ViewGroup) this.LaughTale_activity.findViewById(android.R.id.content);
        rootView.addView(this.LaughTale_adView);
    }

    public void LaughTaleLoadMRECView() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_adView == null || LaughTale_ad_unit == null || LaughTale_ad_unit.trim().isEmpty()) {
            return;
        }
        BannerAdRequest adRequest = new BannerAdRequest.Builder(
                LaughTale_ad_unit.trim(), AdSize.MEDIUM_RECTANGLE).build();
        LaughTale_adView.loadAd(adRequest, new AdLoadCallback<BannerAd>() {
            @Override
            public void onAdLoaded(@NonNull BannerAd bannerAd) {
                LaughTale_onMrecLoaded(bannerAd);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                LaughTale_isLoaded = false;
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                        "=========", "BannerLoadFailed" + adError.getMessage());
            }
        });
    }

    private void LaughTale_onMrecLoaded(@NonNull BannerAd bannerAd) {
        LaughTale_isLoaded = true;
        if (LaughTale_bannerAd != null) {
            LaughTale_bannerAd.destroy();
        }
        LaughTale_bannerAd = bannerAd;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=========", "BannerLoaded:" + LaughTaleResolveNetworkName(bannerAd));
        bannerAd.setAdEventCallback(new BannerAdEventCallback() {
            @Override
            public void onAdImpression() {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "BannerDisplayed");
                LaughTaleEnforceHiddenIfNotRequested();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                LaughTaleEnforceHiddenIfNotRequested();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                LaughTaleEnforceHiddenIfNotRequested();
            }

            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                if (!LaughTale_showRequested) {
                    LaughTaleEnforceHiddenIfNotRequested();
                    return;
                }
                double revenue = adValue.getValueMicros() / 1_000_000.0;
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(
                        revenue, "MREC", LaughTaleResolveNetworkName(bannerAd), LaughTale_ad_unit);
            }
        });
        LaughTaleEnforceHiddenIfNotRequested();
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
            }
        });
        mainHandler.postDelayed(prefetchRunnable, PREFETCH_DELAY_MS);
    }

    public void LaughTalePauseAutoRefresh() {
        // GMA Next-Gen MREC auto-refresh is controlled in AdMob console; no-op here.
    }

    public void LaughTaleResumeAutoRefresh() {
        // GMA Next-Gen MREC auto-refresh is controlled in AdMob console; no-op here.
    }

    public void LaughTaleOnDestroy() {
        mainHandler.removeCallbacks(prefetchRunnable);
        LaughTale_showRequested = false;
        LaughTale_isLoaded = false;
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

    private void LaughTaleApplyLayoutForType(int type) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) LaughTale_adView.getLayoutParams();
        int dp100 = LaughTaleDpToPx(LaughTale_activity, 100);
        int dp150 = LaughTaleDpToPx(LaughTale_activity, 150);
        if (type == 3) {
            params.gravity = Gravity.CENTER;
            params.topMargin = dp100;
            params.bottomMargin = 0;
        } else if (type == 1) {
            params.gravity = Gravity.CENTER;
            params.topMargin = -dp150;
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

    private static int LaughTaleDpToPx(Activity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
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
