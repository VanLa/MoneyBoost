package com.dream.moneyboost;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
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

public class MoneyBoostMRECBannerAdapter {

    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    private AdView MoneyBoost_adView;
    private BannerAd MoneyBoost_bannerAd;
    private boolean MoneyBoost_isLoaded = false;
    /** Unity 主动 show 前禁止展示，避免 prefetch/加载回调自动弹出 */
    private boolean MoneyBoost_showRequested = false;
    private int MoneyBoost_pendingShowType = 0;
    /** hide 后延后补货，避免短间隔反复 load */
    private static final long PREFETCH_DELAY_MS = 5000L;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable prefetchRunnable = new Runnable() {
        @Override
        public void run() {
            if (MoneyBoost_adView != null && !MoneyBoost_showRequested) {
                MoneyBoostLoadMRECView();
            }
        }
    };

    public boolean MoneyBoostIsLoaded() {
        return MoneyBoost_isLoaded;
    }

    public boolean MoneyBoostIsVisible() {
        return MoneyBoost_adView != null && MoneyBoost_adView.getVisibility() == View.VISIBLE;
    }

    public boolean MoneyBoostIsShowRequested() {
        return MoneyBoost_showRequested;
    }

    public void MoneyBoostInitBannerAdapter() {
        this.MoneyBoost_adView = new AdView(this.MoneyBoost_activity);
        AdSize adSize = AdSize.MEDIUM_RECTANGLE;
        this.MoneyBoost_adView.resize(adSize);
        int heightPx = adSize.getHeightInPixels(this.MoneyBoost_activity);

        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, heightPx);
        frameLayout.gravity = Gravity.CENTER;
        this.MoneyBoost_adView.setLayoutParams(frameLayout);
        this.MoneyBoost_adView.setBackgroundColor(Color.TRANSPARENT);
        this.MoneyBoost_adView.setVisibility(View.GONE);

        ViewGroup rootView = (ViewGroup) this.MoneyBoost_activity.findViewById(android.R.id.content);
        if (rootView == null) {
            return;
        }
        ViewParent existingParent = this.MoneyBoost_adView.getParent();
        if (existingParent instanceof ViewGroup) {
            ((ViewGroup) existingParent).removeView(this.MoneyBoost_adView);
        }
        if (this.MoneyBoost_adView.getParent() == null) {
            rootView.addView(this.MoneyBoost_adView, frameLayout);
        }
    }

    public void MoneyBoostLoadMRECView() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        if (MoneyBoost_adView == null || MoneyBoost_ad_unit == null || MoneyBoost_ad_unit.trim().isEmpty()) {
            return;
        }
        BannerAdRequest adRequest = new BannerAdRequest.Builder(
                MoneyBoost_ad_unit.trim(), AdSize.MEDIUM_RECTANGLE).build();
        MoneyBoost_adView.loadAd(adRequest, new AdLoadCallback<BannerAd>() {
            @Override
            public void onAdLoaded(@NonNull BannerAd bannerAd) {
                MoneyBoost_onMrecLoaded(bannerAd);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                MoneyBoost_isLoaded = false;
                MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                        "=========", "BannerLoadFailed" + adError.getMessage());
            }
        });
    }

    private void MoneyBoost_onMrecLoaded(@NonNull BannerAd bannerAd) {
        MoneyBoost_isLoaded = true;
        if (MoneyBoost_bannerAd != null) {
            MoneyBoost_bannerAd.destroy();
        }
        MoneyBoost_bannerAd = bannerAd;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                "=========", "BannerLoaded:" + MoneyBoostResolveNetworkName(bannerAd));
        bannerAd.setAdEventCallback(new BannerAdEventCallback() {
            @Override
            public void onAdImpression() {
                MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "BannerDisplayed");
                MoneyBoostEnforceHiddenIfNotRequested();
            }

            @Override
            public void onAdShowedFullScreenContent() {
                MoneyBoostEnforceHiddenIfNotRequested();
            }

            @Override
            public void onAdDismissedFullScreenContent() {
                MoneyBoostEnforceHiddenIfNotRequested();
            }

            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                if (!MoneyBoost_showRequested) {
                    MoneyBoostEnforceHiddenIfNotRequested();
                    return;
                }
                double revenue = adValue.getValueMicros() / 1_000_000.0;
                MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseRevenue(
                        revenue, "MREC", MoneyBoostResolveNetworkName(bannerAd), MoneyBoost_ad_unit);
            }
        });
        MoneyBoostEnforceHiddenIfNotRequested();
    }

    public void MoneyBoostShowMRECView(int type) {
        if (MoneyBoost_adView == null) {
            return;
        }
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        MoneyBoost_showRequested = true;
        MoneyBoost_pendingShowType = type;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "BannerShouldShow");
        mainHandler.removeCallbacks(prefetchRunnable);
        if (!MoneyBoost_isLoaded) {
            MoneyBoostLoadMRECView();
        }
        MoneyBoostRunOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (!MoneyBoost_showRequested || MoneyBoost_adView == null) {
                    return;
                }
                MoneyBoostApplyLayoutForType(MoneyBoost_pendingShowType);
                MoneyBoost_adView.setVisibility(View.VISIBLE);
            }
        });
    }

    public void MoneyBoostHideMRECView() {
        if (MoneyBoost_adView == null) {
            return;
        }
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        MoneyBoost_showRequested = false;
        mainHandler.removeCallbacks(prefetchRunnable);
        MoneyBoostRunOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (MoneyBoost_adView == null) {
                    return;
                }
                MoneyBoost_adView.setVisibility(View.GONE);
                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) MoneyBoost_adView.getLayoutParams();
                params.topMargin = 0;
                params.bottomMargin = 0;
                params.gravity = Gravity.CENTER;
                MoneyBoost_adView.setLayoutParams(params);
            }
        });
        mainHandler.postDelayed(prefetchRunnable, PREFETCH_DELAY_MS);
    }

    public void MoneyBoostPauseAutoRefresh() {
        // GMA Next-Gen MREC auto-refresh is controlled in AdMob console; no-op here.
    }

    public void MoneyBoostResumeAutoRefresh() {
        // GMA Next-Gen MREC auto-refresh is controlled in AdMob console; no-op here.
    }

    public void MoneyBoostOnDestroy() {
        mainHandler.removeCallbacks(prefetchRunnable);
        MoneyBoost_showRequested = false;
        MoneyBoost_isLoaded = false;
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

    private void MoneyBoostApplyLayoutForType(int type) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) MoneyBoost_adView.getLayoutParams();
        int dp100 = MoneyBoostDpToPx(MoneyBoost_activity, 100);
        int dp150 = MoneyBoostDpToPx(MoneyBoost_activity, 150);
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
        MoneyBoost_adView.setLayoutParams(params);
    }

    private void MoneyBoostEnforceHiddenIfNotRequested() {
        if (MoneyBoost_showRequested || MoneyBoost_adView == null) {
            return;
        }
        MoneyBoostRunOnMainThread(new Runnable() {
            @Override
            public void run() {
                if (MoneyBoost_showRequested || MoneyBoost_adView == null) {
                    return;
                }
                MoneyBoost_adView.setVisibility(View.GONE);
            }
        });
    }

    private void MoneyBoostRunOnMainThread(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else if (MoneyBoost_activity != null) {
            MoneyBoost_activity.runOnUiThread(runnable);
        } else {
            mainHandler.post(runnable);
        }
    }

    private static int MoneyBoostDpToPx(Activity activity, int dp) {
        return (int) (dp * activity.getResources().getDisplayMetrics().density + 0.5f);
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
