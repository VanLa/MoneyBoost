package com.joyboost.moneyboost;

import android.app.Activity;
import android.util.Log;
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
import java.util.concurrent.atomic.AtomicInteger;

import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback;
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
    private boolean preloadingStarted, destroyed;
    private final String preloadId = "moneyboost_banner_" + java.util.UUID.randomUUID();
    private final android.os.Handler refreshHandler = new android.os.Handler(Looper.getMainLooper());
    private long lastTick, visibleMillis;
    private volatile boolean currentImpressed;
    private static final long REFRESH_VISIBLE_MS = 30000L;
    private final Runnable refreshTick = new Runnable() {
        @Override public void run() {
            if (destroyed) return;
            long now = android.os.SystemClock.elapsedRealtime();
            long delta = Math.min(1000L, Math.max(0L, now - lastTick)); lastTick = now;
            if (currentImpressed && !MoneyBoost_isCollapsiblePlacement && MoneyBoost_adView != null && MoneyBoost_adView.isShown()
                    && MoneyBoostMediationManager.getInstance().MoneyBoostCanRefreshBannerAds()) {
                visibleMillis += delta;
                if (MoneyBoost_bannerAd != null && visibleMillis >= REFRESH_VISIBLE_MS) {
                    try { pollBanner(true); }
                    catch (RuntimeException error) { Log.w("MoneyBoostAds", "Banner inventory unavailable", error); }
                }
            }
            refreshHandler.postDelayed(this, 1000L);
        }
    };
    private int MoneyBoost_loadGeneration = 0;
    private final AtomicInteger MoneyBoost_impressionCycle = new AtomicInteger(-1);
    private boolean MoneyBoost_isCollapsiblePlacement = false;

    public boolean MoneyBoostIsLoaded() {
        return MoneyBoost_isLoaded || (preloadingStarted && BannerAdPreloader.isAdAvailable(preloadId));
    }

    public boolean MoneyBoostIsLoading() {
        return MoneyBoost_isLoading;
    }

    public void MoneyBoostInitBannerAdapter() {
        this.MoneyBoost_adView = new AdView(MoneyBoost_activity);
        int widthDp = MoneyBoostGetScreenWidthDp(MoneyBoost_activity);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        if (adSize == null) {
            adSize = AdSize.BANNER;
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
        MoneyBoost_isCollapsiblePlacement = true;
        // A collapsible request must be explicit even when a regular banner is cached.
        MoneyBoost_isLoaded = false;
        MoneyBoostLoadBannerView();
    }

    public void MoneyBoostLoadBannerView() {
        if (!MoneyBoostMediationManager.getInstance().MoneyBoostCanLoadAds()) return;
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        // View registration and inventory consumption must run on the UI thread.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            MoneyBoost_activity.runOnUiThread(this::MoneyBoostLoadBannerView);
            return;
        }
        if (MoneyBoost_adView == null || MoneyBoost_ad_unit == null || MoneyBoost_ad_unit.trim().isEmpty()) {
            return;
        }
        if (destroyed || preloadingStarted) return;
        MoneyBoost_isLoading = true;
        final String diagnosticFormat = MoneyBoost_isCollapsiblePlacement ? "collapsible" : "banner";
        MoneyBoostAdDiagnostics.load(diagnosticFormat, MoneyBoost_ad_unit, "requested", null);
        Log.i("MoneyBoostAds", "Banner load requested unit=" + MoneyBoost_ad_unit);
        try {
        int widthDp = MoneyBoostGetScreenWidthDp(MoneyBoost_activity);
        AdSize adSize = AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(MoneyBoost_activity, widthDp);
        if (adSize == null) {
            adSize = AdSize.BANNER;
        }
        MoneyBoost_adView.resize(adSize);
        BannerAdRequest.Builder requestBuilder = new BannerAdRequest.Builder(MoneyBoost_ad_unit.trim(), adSize);
        if (MoneyBoost_isCollapsiblePlacement) {
            Bundle extras = new Bundle();
            extras.putString("collapsible", "bottom");
            requestBuilder.setGoogleExtrasBundle(extras);
        }
        BannerAdRequest adRequest = requestBuilder.build();
        final int generation = MoneyBoost_loadGeneration;
        final Activity requestActivity = MoneyBoost_activity;
        preloadingStarted = true;
            boolean started = BannerAdPreloader.start(preloadId, new PreloadConfiguration(adRequest, 1), new PreloadCallback() {
                @Override public void onAdPreloaded(String id, ResponseInfo info) {
                    requestActivity.runOnUiThread(() -> {
                        if (destroyed || generation != MoneyBoost_loadGeneration) return;
                        MoneyBoostAdDiagnostics.load(diagnosticFormat, MoneyBoost_ad_unit, "success", null);
                        MoneyBoost_isLoading = false;
                        MoneyBoostMediationManager.getInstance().MoneyBoostOnBannerAdLoaded();
                    });
                }
                @Override public void onAdFailedToPreload(String id, LoadAdError error) {
                    if (destroyed || generation != MoneyBoost_loadGeneration) return;
                    MoneyBoostAdDiagnostics.load(diagnosticFormat, MoneyBoost_ad_unit, "failed", error);
                    // Google owns failed-request retry/backoff. Keep the registered configuration.
                }
                @Override public void onAdsExhausted(String id) { }
            });
            if (!started) { preloadingStarted = false; MoneyBoost_isLoading = false; }
            else {
                lastTick = android.os.SystemClock.elapsedRealtime();
                refreshHandler.removeCallbacks(refreshTick);
                refreshHandler.postDelayed(refreshTick, 1000L);
                if (BannerAdPreloader.isAdAvailable(preloadId))
                    MoneyBoostMediationManager.getInstance().MoneyBoostOnBannerAdLoaded();
            }
        } catch (RuntimeException error) {
            preloadingStarted = false; MoneyBoost_isLoading = false;
            Log.w("MoneyBoostAds", "Banner preloader registration unavailable", error);
        }
    }

    private void pollBanner(boolean replacing) {
        if (destroyed || !preloadingStarted || !BannerAdPreloader.isAdAvailable(preloadId)) return;
        if (replacing && (MoneyBoost_isCollapsiblePlacement || MoneyBoost_adView == null || !MoneyBoost_adView.isShown()
                || !MoneyBoostMediationManager.getInstance().MoneyBoostCanRefreshBannerAds())) return;
        BannerAd next = BannerAdPreloader.pollAd(preloadId);
        if (next == null) return; // Keep the current banner if inventory temporarily runs out.
        AdView candidate = null;
        try {
            if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
                next.destroy(); return;
            }
            ViewGroup root = MoneyBoost_activity.findViewById(android.R.id.content);
            AdSize size = next.getAdSize();
            if (root == null || size == null) { next.destroy(); return; }
            // Prepare a hidden Google AdView; leave the displayed view untouched until registration succeeds.
            candidate = new AdView(MoneyBoost_activity);
            candidate.setVisibility(View.GONE);
            candidate.setBackgroundColor(Color.TRANSPARENT);
            candidate.resize(size);
            FrameLayout.LayoutParams layout = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, size.getHeightInPixels(MoneyBoost_activity));
            layout.gravity = Gravity.BOTTOM;
            MoneyBoostBindBannerCallbacks(next);
            candidate.registerBannerAd(next, MoneyBoost_activity);
            root.addView(candidate, layout);
        } catch (RuntimeException error) {
            if (candidate != null) {
                candidate.unregisterBannerAd();
                ViewParent parent = candidate.getParent();
                if (parent instanceof ViewGroup) ((ViewGroup)parent).removeView(candidate);
                candidate.destroy();
            }
            next.destroy();
            Log.w("MoneyBoostAds", "Banner replacement unavailable; retaining current banner", error);
            return;
        }
        AdView previousView = MoneyBoost_adView;
        BannerAd previousAd = MoneyBoost_bannerAd;
        boolean wasVisible = previousView != null && previousView.getVisibility() == View.VISIBLE;
        MoneyBoost_adView = candidate;
        MoneyBoost_bannerAd = next;
        MoneyBoost_isLoaded = true;
        currentImpressed = false;
        visibleMillis = 0;
        // Expose the replacement before releasing the previous renderer.
        candidate.setVisibility(wasVisible ? View.VISIBLE : View.GONE);
        if (previousView != null) {
            previousView.setVisibility(View.GONE);
            previousView.unregisterBannerAd();
            ViewParent parent = previousView.getParent();
            if (parent instanceof ViewGroup) ((ViewGroup)parent).removeView(previousView);
            previousView.destroy();
        }
        if (previousAd != null) previousAd.destroy();
    }

    private void MoneyBoostBindBannerCallbacks(@NonNull BannerAd bannerAd) {
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                "=========", "BannerLoaded:" + MoneyBoostResolveNetworkName(bannerAd)
                        + " collapsible=" + bannerAd.isCollapsible());
        final int generation = MoneyBoost_loadGeneration;
        // Refresh callback advances the identity on this same BannerAd object.
        final AtomicInteger cycle = new AtomicInteger(MoneyBoost_impressionCycle.incrementAndGet());
        bannerAd.setBannerAdRefreshCallback(new BannerAdRefreshCallback() {
            @Override
            public void onAdRefreshed() {
                if (generation != MoneyBoost_loadGeneration || MoneyBoost_bannerAd != bannerAd) return;
                visibleMillis = 0;
                currentImpressed = false;
                cycle.set(MoneyBoost_impressionCycle.incrementAndGet());
            }
        });
        bannerAd.setAdEventCallback(new BannerAdEventCallback() {
            @Override
            public void onAdImpression() {
                if (generation != MoneyBoost_loadGeneration || MoneyBoost_bannerAd != bannerAd) return;
                currentImpressed = true;
                MoneyBoostMediationManager.getInstance().MoneyBoostOnBannerImpression(MoneyBoostBannerAdapter.this, cycle.get(), bannerAd.isCollapsible());
            }

            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                if (generation != MoneyBoost_loadGeneration || MoneyBoost_bannerAd != bannerAd || adValue == null) return;
                double revenue = adValue.getValueMicros() / 1_000_000.0;
                MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseRevenue(
                        revenue, MoneyBoost_isCollapsiblePlacement ? "COLLAPSIBLE_BANNER" : "BANNER", MoneyBoostResolveNetworkName(bannerAd), MoneyBoost_ad_unit);
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
                if (!preloadingStarted) MoneyBoostLoadBannerView();
                if (MoneyBoost_bannerAd == null) pollBanner(false);
                MoneyBoost_adView.setVisibility(MoneyBoost_bannerAd != null ? View.VISIBLE : View.GONE);
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
            adSize = AdSize.BANNER;
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
            if (MoneyBoost_adView != null && MoneyBoost_adView.isShown()
                    && MoneyBoost_adView.getParent() instanceof ViewGroup
                    && ((ViewGroup)MoneyBoost_adView.getParent()).indexOfChild(MoneyBoost_adView)
                    != ((ViewGroup)MoneyBoost_adView.getParent()).getChildCount() - 1) {
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
        // Visibility/foreground gate in refreshTick pauses visible-time rotation.
    }

    public void MoneyBoostResumeAutoRefresh() {
        // Visibility/foreground gate in refreshTick pauses visible-time rotation.
    }

    public void MoneyBoostOnDestroy() {
        if (destroyed) return;
        destroyed = true;
        MoneyBoost_loadGeneration++;
        refreshHandler.removeCallbacks(refreshTick);
        if (preloadingStarted) BannerAdPreloader.destroy(preloadId);
        preloadingStarted = false;
        MoneyBoost_isLoaded = false;
        MoneyBoost_isLoading = false;
        if (MoneyBoost_bannerAd != null) {
            if (MoneyBoost_adView != null) MoneyBoost_adView.unregisterBannerAd();
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
