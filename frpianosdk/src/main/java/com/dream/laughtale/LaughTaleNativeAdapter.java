package com.dream.laughtale;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;

import org.jetbrains.annotations.Nullable;

public class LaughTaleNativeAdapter implements MaxAdRevenueListener {

    private enum LoadState {
        UNLOAD,
        LOADING,
        LOADED
    }

    private static final float COUNTDOWN_MIN_SIZE_DP = 25f;
    private static final int COUNTDOWN_SECONDS = 3;

    private MaxNativeAdLoader LaughTale_nativeAdLoader;
    private MaxNativeAdView LaughTale_nativeAdView;
    private MaxAd LaughTale_nativeAd;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleNativeListener LaughTale_nativeListener;
    private double LaughTale_adPrice = 0;
    private View adView;
    private Button controlBtn;
    private TextView delayView;
    private FrameLayout LaughTale_overlayRoot;
    public boolean LaughTale_isOpen = false;
    private boolean LaughTale_isMetaNetwork = false;
    private LoadState LaughTale_loadState = LoadState.UNLOAD;
    private final NativeAdListener nativeAdListener = new NativeAdListener();
    private boolean LaughTale_countdownFinished = false;
    private CountDownTimer countdownTimer;

    public boolean LaughTaleIsCountdownFinished() {
        return LaughTale_countdownFinished;
    }

    public boolean LaughTaleIsOpen() {
        return LaughTale_isOpen;
    }

    public boolean LaughTaleIsLoading() {
        return LaughTale_loadState == LoadState.LOADING;
    }

    /** 对齐竞品 AdInst.tick：仅 UNLOAD 时由外层 5s tick 触发 load。 */
    public void LaughTaleTickReload() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_isOpen || LaughTale_loadState == LoadState.LOADING) {
            return;
        }
        if (LaughTale_loadState != LoadState.UNLOAD) {
            return;
        }
        LaughTaleLoadNativeAdView();
    }

    public void LaughTaleCloseNativeAdOnBackground() {
        LaughTaleHideView(false, true, false);
    }

    public void LaughTaleDestroyNativeInventoryOnly() {
        if (LaughTale_activity == null) {
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            cancelCountdown();
            detachFromParent(LaughTale_overlayRoot);
            LaughTale_isOpen = false;
            clearLoadedInventory();
            LaughTale_loadState = LoadState.UNLOAD;
            LaughTaleLoadNativeAdView();
        });
    }

    private static boolean isMetaNetwork(String networkName) {
        if (networkName == null) {
            return false;
        }
        String lower = networkName.toLowerCase();
        return lower.contains("facebook") || lower.contains("meta");
    }

    private void refreshMetaNetworkFlag() {
        if (LaughTale_nativeAd == null) {
            LaughTale_isMetaNetwork = false;
            return;
        }
        LaughTale_isMetaNetwork = isMetaNetwork(LaughTale_nativeAd.getNetworkName());
    }

    private boolean isMetaAd() {
        return LaughTale_isMetaNetwork;
    }

    private MaxNativeAdView createNativeAdView() {
        adView = LayoutInflater.from(LaughTale_activity)
                .inflate(R.layout.laughtale_max_native, null);
        initCloseViews();
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(adView)
                .setTitleTextViewId(R.id.ad_headline)
                .setBodyTextViewId(R.id.ad_body)
                .setIconImageViewId(R.id.ad_app_icon)
                .setMediaContentViewGroupId(R.id.ad_media)
                .setCallToActionButtonId(R.id.ad_call_to_action)
                .setOptionsContentViewGroupId(R.id.options_view)
                .build();
        return new MaxNativeAdView(binder, LaughTale_activity);
    }

    private void initCloseViews() {
        delayView = adView.findViewById(R.id.delay_time);
        delayView.setVisibility(View.VISIBLE);
        delayView.setText(COUNTDOWN_SECONDS + "s");
        controlBtn = adView.findViewById(R.id.btn_close);
        controlBtn.setVisibility(View.GONE);
        controlBtn.setOnClickListener(v -> {
            if (LaughTale_overlayRoot != null) {
                LaughTale_overlayRoot.removeAllViews();
            }
            LaughTaleHideView(true, false, false);
        });
    }

    private void applyCloseButtonSize() {
        if (controlBtn == null || delayView == null || LaughTale_activity == null) {
            return;
        }
        LaughTaleLayoutSize layoutSize = new LaughTaleLayoutSize(LaughTale_activity);
        float closeSizeDp = isMetaAd() ? layoutSize.closeSizeMeta : layoutSize.closeSize;
        float countdownSizeDp = Math.max(closeSizeDp, COUNTDOWN_MIN_SIZE_DP);
        int closePx = dp2px(LaughTale_activity, closeSizeDp);

        ConstraintLayout.LayoutParams closeLp = (ConstraintLayout.LayoutParams) controlBtn.getLayoutParams();
        closeLp.width = closePx;
        closeLp.height = closePx;
        controlBtn.setLayoutParams(closeLp);

        ConstraintLayout.LayoutParams delayLp = (ConstraintLayout.LayoutParams) delayView.getLayoutParams();
        delayLp.width = dp2px(LaughTale_activity, 1.4f * countdownSizeDp);
        delayLp.height = dp2px(LaughTale_activity, countdownSizeDp);
        delayView.setLayoutParams(delayLp);
        delayView.setTextSize(0.7f * countdownSizeDp);
    }

    public void LaughTaleInitNativeAdapter() {
        if (LaughTale_activity == null) {
            return;
        }
        if (LaughTale_overlayRoot == null) {
            LaughTale_overlayRoot = new FrameLayout(LaughTale_activity);
            LaughTale_overlayRoot.setClickable(true);
            LaughTale_overlayRoot.setFocusable(true);
        }
    }

    public void LaughTaleRefreshStaleInventoryIfNeeded() {
        if (LaughTale_isOpen || LaughTale_loadState == LoadState.LOADING) {
            return;
        }
        if (LaughTale_loadState != LoadState.LOADED) {
            return;
        }
        if (LaughTaleCanShowNativeAd()) {
            return;
        }
        LaughTale_loadState = LoadState.UNLOAD;
        LaughTale_adPrice = 0;
    }

    /** 对齐竞品 doLoad：每次 load 新建 MaxNativeAdLoader。 */
    public void LaughTaleLoadNativeAdView() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_isOpen || LaughTale_loadState != LoadState.UNLOAD) {
            return;
        }
        clearLoadedInventory();
        LaughTale_loadState = LoadState.LOADING;
        LaughTale_nativeAdLoader = new MaxNativeAdLoader(LaughTale_ad_unit, LaughTale_activity);
        LaughTale_nativeAdLoader.setRevenueListener(this);
        LaughTale_nativeAdLoader.setNativeAdListener(nativeAdListener);
        LaughTale_nativeAdLoader.loadAd(createNativeAdView());
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "NativeStartLoad");
    }

    public double LaughTaleGetADPrice() {
        if (LaughTale_loadState != LoadState.LOADED || LaughTale_isOpen) {
            return 0;
        }
        return LaughTale_adPrice;
    }

    public boolean LaughTaleCanShowNativeAd() {
        if (LaughTale_loadState != LoadState.LOADED || LaughTale_isOpen) {
            return false;
        }
        if (LaughTale_nativeAd == null) {
            return false;
        }
        MaxNativeAd nativeAd = LaughTale_nativeAd.getNativeAd();
        return nativeAd != null && !nativeAd.isExpired();
    }

    private void notifyNativeAdShowSkipped() {
        if (LaughTale_nativeListener != null) {
            LaughTale_nativeListener.LaughTaleOnNativeAdShowSkipped();
        }
    }

    public void LaughTaleShowNativeAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_isOpen) {
            return;
        }
        if (LaughTale_nativeAdView == null || LaughTale_nativeAd == null || LaughTale_loadState != LoadState.LOADED) {
            notifyNativeAdShowSkipped();
            return;
        }
        if (!LaughTaleCanShowNativeAd()) {
            LaughTale_loadState = LoadState.UNLOAD;
            LaughTale_adPrice = 0;
            notifyNativeAdShowSkipped();
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            if (LaughTale_isOpen || LaughTale_nativeAdView == null || LaughTale_loadState != LoadState.LOADED) {
                notifyNativeAdShowSkipped();
                return;
            }
            if (!LaughTaleCanShowNativeAd()) {
                notifyNativeAdShowSkipped();
                return;
            }
            LaughTale_isOpen = true;
            LaughTale_countdownFinished = false;
            showNativeAdWithOverlay();
            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
            }
        });
    }

    private void showNativeAdWithOverlay() {
        refreshMetaNetworkFlag();
        applyCloseButtonSize();
        controlBtn.setVisibility(View.GONE);
        delayView.setVisibility(View.VISIBLE);
        delayView.setText(COUNTDOWN_SECONDS + "s");

        LaughTaleInitNativeAdapter();
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        if (LaughTale_overlayRoot.getParent() == null) {
            LaughTale_activity.addContentView(LaughTale_overlayRoot, overlayParams);
        }
        LaughTale_overlayRoot.setBackgroundColor(Color.TRANSPARENT);
        LaughTale_overlayRoot.removeAllViews();
        LaughTale_overlayRoot.addView(LaughTale_nativeAdView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        cancelCountdown();
        countdownTimer = new CountDownTimer(COUNTDOWN_SECONDS * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                delayView.setText((millisUntilFinished / 1000) + "s");
            }

            @Override
            public void onFinish() {
                LaughTale_countdownFinished = true;
                delayView.setVisibility(View.GONE);
                controlBtn.setVisibility(View.VISIBLE);
            }
        };
        countdownTimer.start();
    }

    public void LaughTaleAutoHideNativeAd() {
        LaughTaleHideView(true, false, false);
    }

    private void cancelCountdown() {
        if (countdownTimer != null) {
            countdownTimer.cancel();
            countdownTimer = null;
        }
    }

    private void LaughTaleHideView(boolean clickedCloseButton, boolean silentClose, boolean removeOverlayChildrenFirst) {
        if (LaughTale_activity == null) {
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            if (!LaughTale_isOpen) {
                return;
            }
            cancelCountdown();
            if (removeOverlayChildrenFirst && LaughTale_overlayRoot != null) {
                LaughTale_overlayRoot.removeAllViews();
            }
            detachFromParent(LaughTale_overlayRoot);
            LaughTale_isOpen = false;
            onNativeAdClosed(clickedCloseButton, silentClose);
        });
    }

    private void detachFromParent(View view) {
        if (view == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) view.getParent();
        if (parent != null) {
            parent.removeView(view);
        }
    }

    private void tryDestroyAd() {
        if (LaughTale_nativeAd != null && LaughTale_nativeAdLoader != null) {
            LaughTale_nativeAdLoader.destroy(LaughTale_nativeAd);
        }
    }

    private void clearLoadedInventory() {
        tryDestroyAd();
        LaughTale_nativeAd = null;
        LaughTale_nativeAdView = null;
        LaughTale_adPrice = 0;
    }

    /** 对齐竞品 onHide：仅标记 UNLOAD，不 destroy；补货交给 5s tick。 */
    private void onNativeAdClosed(boolean clickedCloseButton, boolean silentClose) {
        LaughTale_loadState = LoadState.UNLOAD;
        LaughTale_adPrice = 0;
        if (LaughTale_nativeListener != null) {
            LaughTale_nativeListener.LaughTaleOnNativeAdClosed(clickedCloseButton, silentClose);
        }
    }

    private static int dp2px(Context context, float dp) {
        return (int) ((dp * context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    private class NativeAdListener extends MaxNativeAdListener {
        @Override
        public void onNativeAdLoaded(@Nullable final MaxNativeAdView nativeAdView, final MaxAd ad) {
            if (LaughTale_loadState != LoadState.LOADING) {
                return;
            }
            if (LaughTale_nativeAd != null && LaughTale_nativeAdLoader != null) {
                LaughTale_nativeAdLoader.destroy(LaughTale_nativeAd);
            }
            LaughTale_nativeAd = ad;
            LaughTale_nativeAdView = nativeAdView;
            LaughTale_isMetaNetwork = isMetaNetwork(ad.getNetworkName());
            LaughTale_adPrice = ad.getRevenue();
            if (LaughTale_adPrice == 0) {
                LaughTale_adPrice = 0.02;
            }
            LaughTale_loadState = LoadState.LOADED;
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=========", "NativeLoaded " + ad.getNetworkName());
            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdLoaded();
            }
        }

        @Override
        public void onNativeAdLoadFailed(final String adUnitId, final MaxError error) {
            if (LaughTale_loadState != LoadState.LOADING) {
                return;
            }
            LaughTale_loadState = LoadState.UNLOAD;
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=========", "LOADNativeFailed " + error.getMessage());
        }

        @Override
        public void onNativeAdClicked(final MaxAd ad) {
            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdClicked();
            }
            if (LaughTale_isOpen) {
                LaughTaleHideView(false, false, false);
            }
        }
    }

    @Override
    public void onAdRevenuePaid(MaxAd maxAd) {
        double revenue = maxAd.getRevenue();
        String networkName = maxAd.getNetworkName();
        String adUnitId = maxAd.getAdUnitId();
        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(
                revenue, maxAd.getFormat().toString(), networkName, adUnitId);
    }
}
