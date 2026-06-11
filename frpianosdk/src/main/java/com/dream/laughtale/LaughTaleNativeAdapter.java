package com.dream.laughtale;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;

import org.jetbrains.annotations.Nullable;

public class LaughTaleNativeAdapter implements MaxAdRevenueListener {

    private MaxNativeAdLoader LaughTale_nativeAdLoader;
    private MaxNativeAdView LaughTale_nativeAdView;
    private MaxAd LaughTale_nativeAd;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private int LaughTale_nativeRetryAttempt;
    public LaughTaleNativeListener LaughTale_nativeListener;
    private double LaughTale_adPrice = 0;
    private Button controlBtn;
    private int delayTime = 3;
    private TextView delayView;
    private View adView;
    private LinearLayout LaughTale_root;
    public boolean LaughTale_isOpen = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    public void LaughTaleInitNativeAdapter() {
        LinearLayout linearLayout = new LinearLayout(LaughTale_activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.LaughTale_root = linearLayout;
    }

    private void initAdView() {
        delayTime = 3;
        this.adView = LayoutInflater.from(LaughTale_activity).inflate(R.layout.laughtale_max_native, (ViewGroup) null);
        this.delayView = (TextView) this.adView.findViewById(R.id.delay_time);
        this.delayView.setText(this.delayTime + "s");
        this.controlBtn = (Button) this.adView.findViewById(R.id.btn_close);
        this.controlBtn.setVisibility(View.GONE);
        this.controlBtn.setOnClickListener(v -> LaughTaleHideView());
    }

    private MaxNativeAdView createNativeAdView() {
        initAdView();
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(this.adView)
                .setTitleTextViewId(R.id.ad_headline)
                .setBodyTextViewId(R.id.ad_body)
                .setIconImageViewId(R.id.ad_app_icon)
                .setMediaContentViewGroupId(R.id.ad_media)
                .setCallToActionButtonId(R.id.ad_call_to_action)
                .setOptionsContentViewGroupId(R.id.options_view)
                .build();
        return new MaxNativeAdView(binder, LaughTale_activity);
    }

    private void startCountdown() {
        if (countdownRunnable != null) {
            mainHandler.removeCallbacks(countdownRunnable);
        }
        countdownRunnable = new Runnable() {
            @Override
            public void run() {
                if (!LaughTale_isOpen) return;
                if (delayTime <= 0) {
                    delayView.setVisibility(View.GONE);
                    controlBtn.setVisibility(View.VISIBLE);
                } else {
                    delayTime--;
                    if (delayView != null) {
                        delayView.setText(delayTime + "s");
                    }
                    mainHandler.postDelayed(this, 1000L);
                }
            }
        };
        mainHandler.postDelayed(countdownRunnable, 1000L);
    }

    public void LaughTaleLoadNativeAdView() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) return;
        if (LaughTale_isOpen) return;
        tryDestroyAd();
        LaughTale_nativeAdLoader = new MaxNativeAdLoader(LaughTale_ad_unit, LaughTale_activity);
        LaughTale_nativeAdLoader.setRevenueListener(this);
        LaughTale_nativeAdLoader.setNativeAdListener(new NativeAdListener());
        LaughTale_nativeAdLoader.loadAd(createNativeAdView());
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "NativeStartLoad");
    }

    public double LaughTaleGetADPrice() {
        return LaughTale_adPrice;
    }

    public boolean LaughTaleCanShowNativeAd() {
        if (LaughTale_nativeAd == null) {
            return false;
        }
        MaxNativeAd nativeAd = LaughTale_nativeAd.getNativeAd();
        return nativeAd != null && !nativeAd.isExpired();
    }

    public void LaughTaleShowNativeAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) return;
        if (LaughTale_nativeAdView == null || LaughTale_nativeAd == null || LaughTale_root == null) return;
        if (!LaughTaleCanShowNativeAd()) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "NativeShowSkipped not ready or expired");
            LaughTaleLoadNativeAdView();
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            LaughTale_isOpen = true;
            LaughTale_adPrice = 0;
            delayTime = 3;
            if (countdownRunnable != null) {
                mainHandler.removeCallbacks(countdownRunnable);
            }
            startCountdown();

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            LaughTale_activity.addContentView(LaughTale_root, layoutParams);
            LaughTale_root.removeAllViews();
            LaughTale_root.setGravity(16);
            LaughTale_root.addView(LaughTale_nativeAdView, layoutParams);

            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
            }
        });
    }

    public void LaughTaleAutoHideNativeAd() {
        LaughTaleHideView(null);
    }

    private void LaughTaleHideView() {
        LaughTaleHideView(controlBtn);
    }

    private void LaughTaleHideView(View view) {
        LaughTale_activity.runOnUiThread(() -> {
            if (!LaughTale_isOpen) return;
            if (countdownRunnable != null) {
                mainHandler.removeCallbacks(countdownRunnable);
            }
            if (LaughTale_root != null && LaughTale_root.getParent() != null) {
                ((ViewGroup) LaughTale_root.getParent()).removeView(LaughTale_root);
            }
            LaughTale_isOpen = false;
            onNativeAdClosed(view != null);
        });
    }

    private void tryDestroyAd() {
        if (LaughTale_nativeAd != null && LaughTale_nativeAdLoader != null) {
            LaughTale_nativeAdLoader.destroy(LaughTale_nativeAd);
        }
    }

    private void onNativeAdClosed(boolean clickedCloseButton) {
        tryDestroyAd();
        LaughTale_nativeAd = null;
        LaughTale_nativeAdView = null;
        LaughTale_adPrice = 0;
        if (LaughTale_nativeListener != null) {
            LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
        }
        mainHandler.postDelayed(this::LaughTaleLoadNativeAdView, 500);
    }

    private class NativeAdListener extends MaxNativeAdListener {
        @Override
        public void onNativeAdLoaded(@Nullable final MaxNativeAdView nativeAdView, final MaxAd ad) {
            if (LaughTale_nativeAd != null && LaughTale_nativeAdLoader != null) {
                LaughTale_nativeAdLoader.destroy(LaughTale_nativeAd);
            }
            LaughTale_nativeRetryAttempt = 0;
            LaughTale_nativeAd = ad;
            LaughTale_nativeAdView = nativeAdView;
            LaughTale_adPrice = ad.getRevenue();
            if (LaughTale_adPrice == 0) {
                LaughTale_adPrice = 0.02;
            }
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=========", "NativeLoaded " + ad.getNetworkName());
        }

        @Override
        public void onNativeAdLoadFailed(final String adUnitId, final MaxError error) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=========", "LOADNativeFailed " + error.getMessage());
            LaughTale_nativeRetryAttempt++;
            long delay = (long) Math.pow(2, Math.min(6, LaughTale_nativeRetryAttempt));
            LaughTaleAdRetryScheduler.scheduleSeconds(() -> {
                if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) return;
                mainHandler.post(LaughTaleNativeAdapter.this::LaughTaleLoadNativeAdView);
            }, delay);
        }

        @Override
        public void onNativeAdClicked(final MaxAd ad) {
        }

        @Override
        public void onNativeAdExpired(final MaxAd ad) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "NativeExpired");
            LaughTale_adPrice = 0;
            if (!LaughTale_isOpen) {
                mainHandler.post(LaughTaleNativeAdapter.this::LaughTaleLoadNativeAdView);
            }
        }
    }

    @Override
    public void onAdRevenuePaid(MaxAd maxAd) {
        double revenue = maxAd.getRevenue();
        String networkName = maxAd.getNetworkName();
        String adUnitId = maxAd.getAdUnitId();
        MaxAdFormat adFormat = maxAd.getFormat();
        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue, adFormat.toString(), networkName, adUnitId);
    }
}
