package com.dream.laughtale;

import android.app.Activity;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.sdk.AppLovinSdkUtils;
import com.applovin.mediation.nativeAds.MaxNativeAd;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;

import org.jetbrains.annotations.Nullable;

public class LaughTaleNativeAdapter implements MaxAdRevenueListener {

    private static final int LAUGHTALE_CLOSE_SIZE_DP = 25;
    private static final int LAUGHTALE_CLOSE_MARGIN_TOP_DP = 10;
    private static final int LAUGHTALE_CLOSE_MARGIN_START_DP = 20;
    private static final int LAUGHTALE_DELAY_WIDTH_DP = 35;
    private static final int LAUGHTALE_DELAY_HEIGHT_DP = 25;
    /** Meta 顶栏高度，与竞品 FrameLayout1 一致 */
    private static final int LAUGHTALE_META_HEADER_HEIGHT_DP = 52;
    /** Meta 关闭按钮底边距，与竞品一致：关闭按钮底部到广告层顶部 = 5dp */
    private static final int LAUGHTALE_META_CLOSE_MARGIN_BOTTOM_DP = 5;
    private static int LaughTale_debugNativeTemplateIndex = 0;

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
    private boolean LaughTale_isMetaNetwork = false;
    private boolean LaughTale_useMetaTemplate = false;
    private Button LaughTale_metaHeaderCloseBtn;
    private TextView LaughTale_metaHeaderDelayView;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable countdownRunnable;

    private static boolean isMetaNetwork(String networkName) {
        if (networkName == null) {
            return false;
        }
        String lower = networkName.toLowerCase();
        return lower.contains("facebook") || lower.contains("meta");
    }

    private boolean resolveUseMetaTemplate() {
        if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
            boolean useMetaTemplate = LaughTale_debugNativeTemplateIndex % 2 == 0;
            LaughTale_debugNativeTemplateIndex++;
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=========", "NativeTemplateDebug useMetaTemplate=" + useMetaTemplate);
            return useMetaTemplate;
        }
        return LaughTale_isMetaNetwork;
    }

    private Button getActiveCloseBtn() {
        return LaughTale_useMetaTemplate ? LaughTale_metaHeaderCloseBtn : controlBtn;
    }

    private TextView getActiveDelayView() {
        return LaughTale_useMetaTemplate ? LaughTale_metaHeaderDelayView : delayView;
    }

    private void resetAdMediaTopMargin() {
        if (LaughTale_nativeAdView == null || LaughTale_activity == null) {
            return;
        }
        View mediaView = LaughTale_nativeAdView.findViewById(R.id.ad_media);
        if (mediaView == null) {
            return;
        }
        ViewGroup.LayoutParams layoutParams = mediaView.getLayoutParams();
        if (!(layoutParams instanceof ViewGroup.MarginLayoutParams)) {
            return;
        }
        ViewGroup.MarginLayoutParams marginLayoutParams = (ViewGroup.MarginLayoutParams) layoutParams;
        if (marginLayoutParams.topMargin != 0) {
            marginLayoutParams.topMargin = 0;
            mediaView.setLayoutParams(marginLayoutParams);
        }
    }

    private void setInternalCloseDelayVisible(boolean visible) {
        if (controlBtn != null) {
            controlBtn.setVisibility(View.GONE);
        }
        if (delayView != null) {
            delayView.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private View createMetaHeaderBar() {
        int headerHeightPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, LAUGHTALE_META_HEADER_HEIGHT_DP);
        int closeSizePx = AppLovinSdkUtils.dpToPx(LaughTale_activity, LAUGHTALE_CLOSE_SIZE_DP);
        int delayWidthPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, LAUGHTALE_DELAY_WIDTH_DP);
        int delayHeightPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, LAUGHTALE_DELAY_HEIGHT_DP);
        int marginStartPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, LAUGHTALE_CLOSE_MARGIN_START_DP);
        int marginBottomPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, LAUGHTALE_META_CLOSE_MARGIN_BOTTOM_DP);

        FrameLayout header = new FrameLayout(LaughTale_activity);
        header.setBackgroundColor(Color.BLACK);

        LaughTale_metaHeaderCloseBtn = new Button(LaughTale_activity);
        LaughTale_metaHeaderCloseBtn.setBackgroundResource(R.mipmap.ad_close);
        LaughTale_metaHeaderCloseBtn.setVisibility(View.GONE);
        LaughTale_metaHeaderCloseBtn.setOnClickListener(v -> LaughTaleHideView());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(closeSizePx, closeSizePx);
        closeParams.gravity = Gravity.BOTTOM | Gravity.START;
        closeParams.setMarginStart(marginStartPx);
        closeParams.bottomMargin = marginBottomPx;
        header.addView(LaughTale_metaHeaderCloseBtn, closeParams);

        LaughTale_metaHeaderDelayView = new TextView(LaughTale_activity);
        LaughTale_metaHeaderDelayView.setTextColor(0xFF666666);
        LaughTale_metaHeaderDelayView.setGravity(Gravity.CENTER);
        LaughTale_metaHeaderDelayView.setBackgroundResource(R.drawable.back_time);
        FrameLayout.LayoutParams delayParams = new FrameLayout.LayoutParams(delayWidthPx, delayHeightPx);
        delayParams.gravity = Gravity.BOTTOM | Gravity.START;
        delayParams.setMarginStart(marginStartPx);
        delayParams.bottomMargin = marginBottomPx;
        header.addView(LaughTale_metaHeaderDelayView, delayParams);

        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, headerHeightPx));
        return header;
    }

    private void mountNativeAdView() {
        LinearLayout.LayoutParams fullScreenParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
        if (LaughTale_useMetaTemplate) {
            LaughTale_root.addView(createMetaHeaderBar(), new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    AppLovinSdkUtils.dpToPx(LaughTale_activity, LAUGHTALE_META_HEADER_HEIGHT_DP)));
            LinearLayout.LayoutParams adParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            LaughTale_root.addView(LaughTale_nativeAdView, adParams);
            return;
        }
        LaughTale_root.addView(LaughTale_nativeAdView, fullScreenParams);
    }

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
                TextView activeDelayView = getActiveDelayView();
                Button activeCloseBtn = getActiveCloseBtn();
                if (delayTime <= 0) {
                    if (activeDelayView != null) {
                        activeDelayView.setVisibility(View.GONE);
                    }
                    if (activeCloseBtn != null) {
                        activeCloseBtn.setVisibility(View.VISIBLE);
                    }
                } else {
                    delayTime--;
                    if (activeDelayView != null) {
                        activeDelayView.setText(delayTime + "s");
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
        if (LaughTale_isOpen) return;
        if (LaughTale_nativeAdView == null || LaughTale_nativeAd == null || LaughTale_root == null) return;
        if (!LaughTaleCanShowNativeAd()) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "NativeShowSkipped not ready or expired");
            LaughTaleLoadNativeAdView();
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            if (LaughTale_isOpen) return;
            if (LaughTale_nativeAdView == null || LaughTale_root == null) return;

            LaughTale_isOpen = true;
            LaughTale_adPrice = 0;
            LaughTale_useMetaTemplate = resolveUseMetaTemplate();
            resetAdMediaTopMargin();
            setInternalCloseDelayVisible(!LaughTale_useMetaTemplate);

            detachFromParent(LaughTale_root);
            detachFromParent(LaughTale_nativeAdView);

            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT);
            LaughTale_activity.addContentView(LaughTale_root, layoutParams);
            LaughTale_root.removeAllViews();
            LaughTale_root.setOrientation(LinearLayout.VERTICAL);
            LaughTale_root.setGravity(Gravity.TOP);
            mountNativeAdView();

            delayTime = 3;
            TextView activeDelayView = getActiveDelayView();
            Button activeCloseBtn = getActiveCloseBtn();
            if (activeDelayView != null) {
                activeDelayView.setVisibility(View.VISIBLE);
                activeDelayView.setText(delayTime + "s");
            }
            if (activeCloseBtn != null) {
                activeCloseBtn.setVisibility(View.GONE);
            }
            if (countdownRunnable != null) {
                mainHandler.removeCallbacks(countdownRunnable);
            }
            startCountdown();

            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
            }
        });
    }

    public void LaughTaleAutoHideNativeAd() {
        LaughTaleHideView(null);
    }

    private void LaughTaleHideView() {
        LaughTaleHideView(getActiveCloseBtn());
    }

    private void LaughTaleHideView(View view) {
        LaughTale_activity.runOnUiThread(() -> {
            if (!LaughTale_isOpen) return;
            if (countdownRunnable != null) {
                mainHandler.removeCallbacks(countdownRunnable);
            }
            detachFromParent(LaughTale_root);
            LaughTale_isOpen = false;
            LaughTale_metaHeaderCloseBtn = null;
            LaughTale_metaHeaderDelayView = null;
            onNativeAdClosed(view != null);
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
            LaughTale_isMetaNetwork = isMetaNetwork(ad.getNetworkName());
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
