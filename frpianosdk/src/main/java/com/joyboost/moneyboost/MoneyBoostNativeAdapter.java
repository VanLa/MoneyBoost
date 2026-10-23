package com.joyboost.moneyboost;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.nativead.*;
import java.util.Collections;

/** One embedded placement, owned by its page. All methods run on the Android UI thread. */
final class MoneyBoostNativeAdapter {
    private final Activity activity;
    private final String placement, unit;
    private volatile NativeAd ad;
    private NativeAdView view;
    private volatile boolean preloadingStarted;
    private boolean requested;
    private final String preloadId;
    private volatile boolean destroyed;
    private volatile long loadedAt;
    private static final long MAX_AGE_MS = 55 * 60 * 1000L;
    private int generation;
    private float x, y, width, height;
    private final android.view.ViewTreeObserver.OnGlobalLayoutListener layoutListener = this::sync;

    MoneyBoostNativeAdapter(Activity activity, String placement, String unit) {
        this.activity = activity; this.placement = placement; this.unit = unit;
        this.preloadId = "moneyboost_native_" + placement + "_" + java.util.UUID.randomUUID();
        ViewGroup root = root();
        if (root != null) root.getViewTreeObserver().addOnGlobalLayoutListener(layoutListener);
    }

    void load() {
        if (destroyed || preloadingStarted
                || !MoneyBoostMediationManager.getInstance().MoneyBoostCanShowNativeAds()) return;
        final int ticket = generation;
        MoneyBoostAdDiagnostics.load("native_" + placement, unit, "requested", null);
        try {
            NativeAdRequest request = new NativeAdRequest.Builder(unit,
                    Collections.singletonList(NativeAd.NativeAdType.NATIVE)).build();
            preloadingStarted = true;
            boolean started = NativeAdPreloader.start(preloadId, new PreloadConfiguration(request, 1), new PreloadCallback() {
                @Override public void onAdPreloaded(String id, ResponseInfo info) {
                    if (destroyed || generation != ticket) return;
                    MoneyBoostAdDiagnostics.load("native_" + placement, unit, "success", null);
                    // Do not poll here: inventory remains owned by Google until the slot is visible.
                }
                @Override public void onAdFailedToPreload(String id, LoadAdError error) {
                    if (destroyed || generation != ticket) return;
                    MoneyBoostAdDiagnostics.load("native_" + placement, unit, "failed", error);
                    // The registered Google preloader owns retry/backoff and replenishment.
                }
                @Override public void onAdsExhausted(String id) { }
            });
            if (!started) preloadingStarted = false;
        } catch (RuntimeException error) {
            preloadingStarted = false;
            android.util.Log.w("MoneyBoostAds", "Native preloader unavailable", error);
        }
    }

    private void acquireAd() {
        if (ad != null || !preloadingStarted || destroyed) return;
        NativeAdLoadResult.NativeAdLoadSuccessResult result = NativeAdPreloader.pollAd(preloadId);
        if (!(result instanceof NativeAdLoadResult.NativeAdSuccess)) {
            if (result instanceof NativeAdLoadResult.BannerAdSuccess) ((NativeAdLoadResult.BannerAdSuccess)result).getAd().destroy();
            else if (result instanceof NativeAdLoadResult.CustomNativeAdSuccess) ((NativeAdLoadResult.CustomNativeAdSuccess)result).getAd().destroy();
            return;
        }
        NativeAd loaded = ((NativeAdLoadResult.NativeAdSuccess)result).getAd();
        ad = loaded; loadedAt = SystemClock.elapsedRealtime();
        final String requestId = java.util.UUID.randomUUID().toString();
        final java.util.concurrent.atomic.AtomicBoolean impressionLogged = new java.util.concurrent.atomic.AtomicBoolean();
        loaded.setAdEventCallback(new NativeAdEventCallback() {
            @Override public void onAdPaid(AdValue value) {
                if (destroyed || ad != loaded || value == null) return;
                String network = "AdMob";
                if (loaded.getResponseInfo() != null && loaded.getResponseInfo().getLoadedAdSourceResponseInfo() != null)
                    network = loaded.getResponseInfo().getLoadedAdSourceResponseInfo().getName();
                MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseRevenue(
                        value.getValueMicros() / 1_000_000.0, "NATIVE", network, unit);
            }
            @Override public void onAdImpression() {
                if (destroyed || ad != loaded || !impressionLogged.compareAndSet(false, true)) return;
                MoneyBoostAdDiagnostics.record("native_show", eventParams(requestId));
            }
            @Override public void onAdClicked() {
                if (destroyed || ad != loaded) return;
                MoneyBoostAdDiagnostics.record("native_click", eventParams(requestId));
            }
        });
    }

    void show(float x, float y, float width, float height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
        requested = true;
        if (ad != null && expired()) clearAd();
        load(); sync();
    }

    private boolean expired() { return SystemClock.elapsedRealtime() - loadedAt >= MAX_AGE_MS; }
    boolean isReady() { return !destroyed && ((ad != null && !expired()) || (preloadingStarted && NativeAdPreloader.isAdAvailable(preloadId))); }

    private android.os.Bundle eventParams(String requestId) {
        android.os.Bundle params = new android.os.Bundle();
        params.putString("placement", placement);
        params.putString("scene", "native_" + placement);
        params.putString("ad_type", "NATIVE");
        params.putString("ad_unit_id", MoneyBoostAdDiagnostics.shortText(unit));
        params.putString("request_id", requestId);
        return params;
    }

    void hide() { requested = false; if (view != null) view.setVisibility(View.GONE); }

    void sync() {
        ViewGroup root = root();
        if (root == null) return;
        MoneyBoostMediationManager manager = MoneyBoostMediationManager.getInstance();
        int left = Math.round(x * root.getWidth()), top = Math.round(y * root.getHeight());
        int w = Math.round(width * root.getWidth()), h = Math.round(height * root.getHeight());
        int reserve = manager.MoneyBoostGetNativeBottomReservePx();
        boolean fits = MoneyBoostNativeGeometry.fits(left, top, w, h, root.getWidth(), root.getHeight(),
                reserve, dp(260), dp(200));
        // Free the hidden Native buffer for the requested collapsible format under Google's global cache limit.
        if (manager.MoneyBoostHasCollapsibleRequest() && preloadingStarted) {
            NativeAdPreloader.destroy(preloadId); preloadingStarted = false; generation++;
        }
        if (!requested || destroyed || !manager.MoneyBoostCanShowNativeAds() || !fits) {
            if (view != null) view.setVisibility(View.GONE); return;
        }
        if (ad != null && expired()) clearAd();
        if (ad == null) {
            try { acquireAd(); }
            catch (RuntimeException error) {
                clearAd();
                android.util.Log.w("MoneyBoostAds", "Native inventory unavailable", error);
                return;
            }
        }
        if (view == null && ad != null && !destroyed) {
            try { createView(); }
            catch (RuntimeException error) {
                clearAd();
                android.util.Log.w("MoneyBoostAds", "Native rendering unavailable", error);
            }
        }
        if (view == null) return;
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(w, h);
        params.gravity = Gravity.TOP | Gravity.LEFT; params.leftMargin = left; params.topMargin = top;
        ViewGroup.LayoutParams old = view.getLayoutParams();
        if (!(old instanceof FrameLayout.LayoutParams)
                || old.width != w || old.height != h
                || ((FrameLayout.LayoutParams)old).leftMargin != left
                || ((FrameLayout.LayoutParams)old).topMargin != top) view.setLayoutParams(params);
        view.setVisibility(View.VISIBLE);
        // Banner is always the upper overlay, even if a future layout accidentally intersects it.
        manager.MoneyBoostBringBannersToFront();
    }

    private void createView() {
        ViewGroup root = root();
        if (root == null || ad == null) return;
        view = new NativeAdView(activity);
        view.setVisibility(View.GONE);
        GradientDrawable bg = new GradientDrawable(); bg.setColor(Color.WHITE); bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), Color.rgb(205, 195, 235)); view.setBackground(bg);
        LinearLayout outer = new LinearLayout(activity); outer.setOrientation(LinearLayout.VERTICAL);
        outer.setPadding(dp(12), dp(6), dp(30), dp(8));
        view.addView(outer, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = text("Ad", 12, Color.DKGRAY); // Publisher attribution; SDK supplies AdChoices.
        outer.addView(badge, new LinearLayout.LayoutParams(-1, dp(20)));
        LinearLayout content = new LinearLayout(activity); content.setGravity(Gravity.CENTER_VERTICAL);
        outer.addView(content, new LinearLayout.LayoutParams(-1, -1));
        MediaView media = new MediaView(activity); media.setImageScaleType(ImageView.ScaleType.FIT_CENTER);
        media.setMediaContent(ad.getMediaContent());
        content.addView(media, new LinearLayout.LayoutParams(dp(120), dp(120)));
        LinearLayout detail = new LinearLayout(activity); detail.setOrientation(LinearLayout.VERTICAL);
        detail.setPadding(dp(10), 0, 0, 0);
        content.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
        TextView headline = text(limit(ad.getHeadline(), 25), 15, Color.rgb(42, 29, 80));
        detail.addView(headline); view.setHeadlineView(headline);
        if (ad.getIcon() != null && ad.getIcon().getDrawable() != null) {
            ImageView icon = new ImageView(activity); icon.setImageDrawable(ad.getIcon().getDrawable());
            detail.addView(icon, new LinearLayout.LayoutParams(dp(24), dp(24))); view.setIconView(icon);
        }
        if (ad.getCallToAction() != null) {
            Button cta = new Button(activity); cta.setText(limit(ad.getCallToAction(), 15)); cta.setTextSize(13);
            detail.addView(cta, new LinearLayout.LayoutParams(-1, dp(48))); view.setCallToActionView(cta);
        }
        // Never register manual impression/click handlers or make the background clickable.
        root.addView(view, new FrameLayout.LayoutParams(dp(300), dp(200)));
        view.registerNativeAd(ad, media);
    }

    private static String limit(String value, int count) {
        if (value == null || value.codePointCount(0, value.length()) <= count) return value;
        return value.substring(0, value.offsetByCodePoints(0, count));
    }

    private TextView text(String value, int size, int color) {
        TextView t = new TextView(activity); t.setText(value); t.setTextSize(size); t.setTextColor(color); return t;
    }
    private int dp(int n) { return Math.round(n * activity.getResources().getDisplayMetrics().density); }
    private ViewGroup root() { return activity.findViewById(android.R.id.content); }
    private void clearAd() {
        if (view != null) { ViewGroup parent = (ViewGroup)view.getParent(); if (parent != null) parent.removeView(view); view.destroy(); view = null; }
        if (ad != null) { ad.destroy(); ad = null; }
    }
    void destroy() {
        destroyed = true; generation++; requested = false;
        if (preloadingStarted) NativeAdPreloader.destroy(preloadId);
        preloadingStarted = false;
        ViewGroup root = root();
        if (root != null) root.getViewTreeObserver().removeOnGlobalLayoutListener(layoutListener);
        clearAd();
    }
}
