package com.dream.laughtale;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.os.CountDownTimer;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesPlacement;
import com.google.android.libraries.ads.mobile.sdk.common.AdSourceResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.Image;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;
import com.google.android.libraries.ads.mobile.sdk.common.VideoOptions;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView;

import java.util.Collections;
import java.util.concurrent.ScheduledFuture;

/**
 * GMA Next-Gen Native 广告位。
 * FULLSCREEN：竞品 FSN 全屏 overlay（3s 倒计时 + 灰色 X）
 * HALF：截图模板（黄 Ad + icon/标题在上 + Media + 绿 CTA）；
 * 倒计时灰圆与关闭灰圆：
 * 第一条：倒计时随机左右，关闭在对侧；
 * 第二条：倒计时在第一条关闭的对侧，关闭与倒计时同侧（先后显示）。
 */
public class LaughTaleNativeAdapter {

    public enum DisplayMode {
        FULLSCREEN,
        HALF
    }

    private enum LoadState {
        UNLOAD,
        LOADING,
        LOADED
    }

    private NativeAd LaughTale_nativeAd;
    private NativeAdView LaughTale_nativeAdView;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleNativeListener LaughTale_nativeListener;
    /** FULLSCREEN 或 HALF，初始化时设定 */
    public DisplayMode LaughTale_displayMode = DisplayMode.FULLSCREEN;
    private double LaughTale_adPrice = 0;
    private FrameLayout fullscreenCloseFrame;
    private TextView fullscreenCountdownView;
    private ImageView fullscreenCloseIcon;
    private View halfCloseButton;
    private TextView halfCountdownView;
    private ImageView halfCloseIcon;
    private View halfCardAction;
    private View halfCardCountdown;
    private boolean LaughTale_halfCountdownOnLeft = true;
    private boolean LaughTale_halfCloseOnLeft = false;
    private FrameLayout LaughTale_overlayRoot;
    public boolean LaughTale_isOpen = false;
    private LoadState LaughTale_loadState = LoadState.UNLOAD;
    private boolean LaughTale_countdownFinished = false;
    private CountDownTimer countdownTimer;
    private int LaughTale_retryAttempt = 0;
    private long LaughTale_retryNotBeforeMs = 0;
    private ScheduledFuture<?> LaughTale_retryFuture;

    public boolean LaughTaleIsCountdownFinished() {
        return LaughTale_countdownFinished;
    }

    public boolean LaughTaleIsOpen() {
        return LaughTale_isOpen;
    }

    public boolean LaughTaleIsLoading() {
        return LaughTale_loadState == LoadState.LOADING;
    }

    public boolean LaughTaleIsHalfMode() {
        return LaughTale_displayMode == DisplayMode.HALF;
    }

    /** 对齐竞品：仅 UNLOAD 时由外层 5s tick 触发 load；退避窗口内跳过。 */
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
        if (System.currentTimeMillis() < LaughTale_retryNotBeforeMs) {
            return;
        }
        if (LaughTale_retryFuture != null && !LaughTale_retryFuture.isDone()) {
            return;
        }
        LaughTaleLoadNativeAdView();
    }

    public void LaughTaleCloseNativeAdOnBackground() {
        LaughTaleHideView(false, true, false);
    }

    /** 被其它广告顶掉时静默关闭，不触发 Unity CLOSE / double 链路 */
    public void LaughTaleForceCloseNativeAdSilent() {
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

    /** Activity Destroy：停倒计时/重试、卸 overlay、毁库存，不再补货。 */
    public void LaughTaleRelease() {
        cancelRetry();
        LaughTale_retryNotBeforeMs = 0;
        LaughTale_retryAttempt = 0;
        Runnable release = () -> {
            cancelCountdown();
            detachFromParent(LaughTale_overlayRoot);
            LaughTale_overlayRoot = null;
            LaughTale_isOpen = false;
            clearLoadedInventory();
            LaughTale_loadState = LoadState.UNLOAD;
            LaughTale_nativeListener = null;
            LaughTale_activity = null;
        };
        Activity activity = LaughTale_activity;
        if (activity != null && !activity.isDestroyed()) {
            activity.runOnUiThread(release);
        } else {
            release.run();
        }
    }

    private int layoutResId() {
        return LaughTale_displayMode == DisplayMode.HALF
                ? R.layout.laughtale_max_native_half
                : R.layout.laughtale_max_native;
    }

    private NativeAdView createNativeAdView() {
        NativeAdView nativeAdView = (NativeAdView) LayoutInflater.from(LaughTale_activity)
                .inflate(layoutResId(), null);
        if (LaughTale_displayMode == DisplayMode.FULLSCREEN) {
            bindFullscreenCloseViews(nativeAdView);
        } else {
            bindHalfCloseViews(nativeAdView);
        }
        return nativeAdView;
    }

    private void bindFullscreenCloseViews(NativeAdView nativeAdView) {
        fullscreenCloseFrame = nativeAdView.findViewById(R.id.btnClose);
        fullscreenCountdownView = nativeAdView.findViewById(R.id.tv_countdown);
        fullscreenCloseIcon = nativeAdView.findViewById(R.id.iv_close);
        halfCloseButton = null;
        if (fullscreenCountdownView != null) {
            fullscreenCountdownView.setVisibility(View.VISIBLE);
            fullscreenCountdownView.setText(LaughTaleNativeAdConstants.COUNTDOWN_FULLSCREEN_SEC + "s");
        }
        if (fullscreenCloseIcon != null) {
            fullscreenCloseIcon.setVisibility(View.GONE);
        }
        if (fullscreenCloseFrame != null) {
            fullscreenCloseFrame.setOnClickListener(v -> {
                if (LaughTale_countdownFinished) {
                    LaughTaleHideView(true, false, false);
                }
            });
        }
    }

    private void bindHalfCloseViews(NativeAdView nativeAdView) {
        halfCloseButton = nativeAdView.findViewById(R.id.btnClose);
        halfCountdownView = nativeAdView.findViewById(R.id.tv_countdown);
        halfCloseIcon = nativeAdView.findViewById(R.id.iv_close);
        halfCardAction = nativeAdView.findViewById(R.id.cardAction);
        halfCardCountdown = nativeAdView.findViewById(R.id.cardCountdown);
        fullscreenCloseFrame = null;
        fullscreenCountdownView = null;
        fullscreenCloseIcon = null;
        LaughTale_countdownFinished = false;
        resetHalfCountdownUi();
        if (halfCloseButton != null) {
            halfCloseButton.setOnClickListener(v -> {
                if (LaughTale_countdownFinished) {
                    LaughTaleHideView(true, false, false);
                }
            });
        }
        applyHalfCloseSide();
    }

    public void LaughTaleSetHalfSides(boolean countdownOnLeft, boolean closeOnLeft) {
        LaughTale_halfCountdownOnLeft = countdownOnLeft;
        LaughTale_halfCloseOnLeft = closeOnLeft;
        if (LaughTale_activity == null) {
            return;
        }
        LaughTale_activity.runOnUiThread(this::applyHalfCloseSide);
    }

    private void applyHalfCloseSide() {
        if (halfCardAction == null || halfCardCountdown == null) {
            return;
        }
        applyHalfWidgetGravity(halfCardCountdown, LaughTale_halfCountdownOnLeft);
        applyHalfWidgetGravity(halfCardAction, LaughTale_halfCloseOnLeft);
        halfCardCountdown.requestLayout();
        halfCardAction.requestLayout();
        if (halfCardAction.getParent() instanceof View) {
            ((View) halfCardAction.getParent()).requestLayout();
        }
    }

    /** 用绝对 LEFT/RIGHT Gravity，避免 ConstraintSet / RTL 换边失效。 */
    private static void applyHalfWidgetGravity(View widget, boolean onLeft) {
        ViewGroup.LayoutParams raw = widget.getLayoutParams();
        FrameLayout.LayoutParams lp;
        if (raw instanceof FrameLayout.LayoutParams) {
            lp = (FrameLayout.LayoutParams) raw;
        } else {
            lp = new FrameLayout.LayoutParams(
                    raw != null ? raw.width : ViewGroup.LayoutParams.WRAP_CONTENT,
                    raw != null ? raw.height : ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        // 清掉 START/END；半屏关闭/倒计时叠在 Media 顶部左右
        lp.gravity = (onLeft ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP;
        widget.setLayoutParams(lp);
    }

    private void resetHalfCountdownUi() {
        LaughTale_countdownFinished = false;
        if (halfCardCountdown != null) {
            halfCardCountdown.setVisibility(View.VISIBLE);
        }
        if (halfCountdownView != null) {
            halfCountdownView.setVisibility(View.VISIBLE);
            halfCountdownView.setText(String.valueOf(LaughTaleNativeAdConstants.COUNTDOWN_HALF_SEC));
        }
        // 倒计时结束前：关闭侧（含 X）整圆隐藏
        if (halfCardAction != null) {
            halfCardAction.setVisibility(View.INVISIBLE);
        }
        if (halfCloseIcon != null) {
            halfCloseIcon.setVisibility(View.GONE);
        }
        if (halfCloseButton != null) {
            halfCloseButton.setClickable(false);
            halfCloseButton.setFocusable(false);
        }
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

    public void LaughTaleLoadNativeAdView() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        if (LaughTale_ad_unit == null || LaughTale_ad_unit.trim().isEmpty()) {
            return;
        }
        if (LaughTale_isOpen || LaughTale_loadState != LoadState.UNLOAD) {
            return;
        }
        if (System.currentTimeMillis() < LaughTale_retryNotBeforeMs) {
            return;
        }
        cancelRetry();
        clearLoadedInventory();
        LaughTale_loadState = LoadState.LOADING;
        // 先请求，成功后再 inflate，避免失败路径反复建 View
        NativeAdRequest adRequest = new NativeAdRequest.Builder(
                LaughTale_ad_unit.trim(),
                Collections.singletonList(NativeAd.NativeAdType.NATIVE))
                .setAdChoicesPlacement(AdChoicesPlacement.TOP_RIGHT)
                .setVideoOptions(new VideoOptions.Builder()
                        .setStartMuted(LaughTaleNativeAdConstants.START_MUTE_VIDEO)
                        .build())
                .build();
        NativeAdLoader.load(adRequest, new NativeAdLoaderCallback() {
            @Override
            public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                if (LaughTale_loadState != LoadState.LOADING
                        || LaughTale_activity == null
                        || LaughTale_activity.isFinishing()
                        || LaughTale_activity.isDestroyed()) {
                    nativeAd.destroy();
                    // 避免永久卡在 LOADING，导致 tick/关后补货再也不进
                    if (LaughTale_loadState == LoadState.LOADING) {
                        LaughTale_loadState = LoadState.UNLOAD;
                        LaughTale_adPrice = 0;
                    }
                    return;
                }
                LaughTale_onNativeAdLoaded(nativeAd);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError adError) {
                if (LaughTale_loadState != LoadState.LOADING) {
                    return;
                }
                LaughTale_loadState = LoadState.UNLOAD;
                LaughTale_adPrice = 0;
                LaughTaleScheduleRetry();
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                        "=========", "LOADNativeFailed " + adError.getMessage());
            }
        });
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========",
                "NativeStartLoad mode=" + LaughTale_displayMode + " unit=" + LaughTale_ad_unit);
    }

    private void LaughTale_onNativeAdLoaded(@NonNull NativeAd nativeAd) {
        LaughTale_retryAttempt = 0;
        LaughTale_retryNotBeforeMs = 0;
        cancelRetry();
        LaughTale_nativeAdView = createNativeAdView();
        LaughTale_nativeAd = nativeAd;
        LaughTale_adPrice = 0.02;
        LaughTale_loadState = LoadState.LOADED;
        // 仅预填文案；registerNativeAd 必须在 View 附着并完成 layout 后调用，
        // 否则校验器会报 Advertiser assets outside native ad view。
        if (LaughTale_displayMode == DisplayMode.FULLSCREEN) {
            bindFullscreenAssetTexts(nativeAd, LaughTale_nativeAdView);
        } else {
            bindHalfAssetTexts(nativeAd, LaughTale_nativeAdView);
        }
        nativeAd.setAdEventCallback(new NativeAdEventCallback() {
            @Override
            public void onAdClicked() {
                if (LaughTale_nativeListener != null) {
                    LaughTale_nativeListener.LaughTaleOnNativeAdClicked();
                }
                if (LaughTale_isOpen && LaughTale_displayMode == DisplayMode.FULLSCREEN) {
                    LaughTaleHideView(false, false, false);
                }
            }

            @Override
            public void onAdPaid(@NonNull AdValue adValue) {
                double revenue = adValue.getValueMicros() / 1_000_000.0;
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(
                        revenue, "NATIVE", LaughTaleResolveNetworkName(nativeAd), LaughTale_ad_unit);
            }
        });
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=========", "NativeLoaded mode=" + LaughTale_displayMode + " "
                        + LaughTaleResolveNetworkName(nativeAd));
        if (LaughTale_nativeListener != null) {
            LaughTale_nativeListener.LaughTaleOnNativeAdLoaded();
        }
    }

    private void populateNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView nativeAdView) {
        if (LaughTale_displayMode == DisplayMode.FULLSCREEN) {
            populateFullscreenNativeAdView(nativeAd, nativeAdView);
        } else {
            populateHalfNativeAdView(nativeAd, nativeAdView);
        }
    }

    /** 加载成功时预填，不 register（避免 0 尺寸时注册）。 */
    private void bindFullscreenAssetTexts(@NonNull NativeAd nativeAd, @NonNull NativeAdView nativeAdView) {
        TextView headlineView = nativeAdView.findViewById(R.id.headline);
        TextView ctaView = nativeAdView.findViewById(R.id.cta);
        ImageView iconView = nativeAdView.findViewById(R.id.icon);
        if (headlineView != null) {
            String headline = nativeAd.getHeadline();
            headlineView.setText(headline != null ? headline : "");
            // Google 样例：缺资产用 INVISIBLE，不用 GONE（GONE 易触发 outside 校验）
            headlineView.setVisibility(headline != null ? View.VISIBLE : View.INVISIBLE);
        }
        if (ctaView != null) {
            String cta = nativeAd.getCallToAction();
            ctaView.setText(cta != null ? cta : "");
            ctaView.setVisibility(cta != null ? View.VISIBLE : View.INVISIBLE);
        }
        if (iconView != null) {
            Image icon = nativeAd.getIcon();
            if (icon != null && icon.getDrawable() != null) {
                iconView.setImageDrawable(icon.getDrawable());
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setImageDrawable(null);
                iconView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void populateFullscreenNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView nativeAdView) {
        TextView headlineView = nativeAdView.findViewById(R.id.headline);
        TextView ctaView = nativeAdView.findViewById(R.id.cta);
        ImageView iconView = nativeAdView.findViewById(R.id.icon);
        MediaView mediaView = nativeAdView.findViewById(R.id.media_view);

        bindFullscreenAssetTexts(nativeAd, nativeAdView);

        if (headlineView != null) {
            nativeAdView.setHeadlineView(headlineView);
        }
        if (ctaView != null) {
            nativeAdView.setCallToActionView(ctaView);
        }
        if (iconView != null) {
            // 始终绑定 iconView（即使 INVISIBLE），与 Google Next-Gen 样例一致
            nativeAdView.setIconView(iconView);
        }
        if (mediaView != null) {
            mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        // 竞品不 setAdChoicesView：由 SDK 按 AdChoicesPlacement 自动插入
        nativeAdView.registerNativeAd(nativeAd, mediaView);
    }

    /** 加载成功时预填，不 register（避免未附着 0 尺寸时注册）。 */
    private void bindHalfAssetTexts(@NonNull NativeAd nativeAd, @NonNull NativeAdView nativeAdView) {
        TextView headlineView = nativeAdView.findViewById(R.id.ad_headline);
        TextView bodyView = nativeAdView.findViewById(R.id.ad_body);
        TextView ctaView = nativeAdView.findViewById(R.id.ad_call_to_action);
        ImageView iconView = nativeAdView.findViewById(R.id.ad_app_icon);
        if (headlineView != null) {
            String headline = nativeAd.getHeadline();
            headlineView.setText(headline != null ? headline : "");
            headlineView.setVisibility(headline != null ? View.VISIBLE : View.INVISIBLE);
        }
        if (bodyView != null) {
            String body = nativeAd.getBody();
            bodyView.setText(body != null ? body : "");
            bodyView.setVisibility(body != null ? View.VISIBLE : View.INVISIBLE);
        }
        if (ctaView != null) {
            String cta = nativeAd.getCallToAction();
            if (cta == null || cta.isEmpty()) {
                cta = "Open";
            }
            ctaView.setText(cta);
            ctaView.setVisibility(View.VISIBLE);
        }
        if (iconView != null) {
            Image icon = nativeAd.getIcon();
            if (icon != null && icon.getDrawable() != null) {
                iconView.setImageDrawable(icon.getDrawable());
                iconView.setVisibility(View.VISIBLE);
            } else {
                iconView.setImageDrawable(null);
                iconView.setVisibility(View.INVISIBLE);
            }
        }
    }

    private void populateHalfNativeAdView(@NonNull NativeAd nativeAd, @NonNull NativeAdView nativeAdView) {
        TextView headlineView = nativeAdView.findViewById(R.id.ad_headline);
        TextView bodyView = nativeAdView.findViewById(R.id.ad_body);
        TextView ctaView = nativeAdView.findViewById(R.id.ad_call_to_action);
        ImageView iconView = nativeAdView.findViewById(R.id.ad_app_icon);
        MediaView mediaView = nativeAdView.findViewById(R.id.ad_media);

        bindHalfAssetTexts(nativeAd, nativeAdView);

        if (headlineView != null) {
            nativeAdView.setHeadlineView(headlineView);
        }
        if (bodyView != null) {
            nativeAdView.setBodyView(bodyView);
        }
        if (ctaView != null) {
            nativeAdView.setCallToActionView(ctaView);
        }
        if (iconView != null) {
            nativeAdView.setIconView(iconView);
        }
        if (mediaView != null) {
            mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        nativeAdView.registerNativeAd(nativeAd, mediaView);
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
        return LaughTale_nativeAd != null && LaughTale_nativeAdView != null;
    }

    private void notifyNativeAdShowSkipped() {
        if (LaughTale_nativeListener != null) {
            LaughTale_nativeListener.LaughTaleOnNativeAdShowSkipped();
        }
    }

    /** 全屏展示 */
    public void LaughTaleShowNativeAd() {
        LaughTaleShowNativeAdInternal(DisplayMode.FULLSCREEN);
    }

    /** 半屏展示（底部）；countdown/close 左右由 MediationManager 按 double 规则传入。 */
    public void LaughTaleShowNativeAdHalf(boolean countdownOnLeft, boolean closeOnLeft) {
        LaughTale_halfCountdownOnLeft = countdownOnLeft;
        LaughTale_halfCloseOnLeft = closeOnLeft;
        LaughTaleShowNativeAdInternal(DisplayMode.HALF);
    }

    private void LaughTaleShowNativeAdInternal(DisplayMode showAs) {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            notifyNativeAdShowSkipped();
            return;
        }
        if (LaughTale_isOpen) {
            notifyNativeAdShowSkipped();
            return;
        }
        if (LaughTale_nativeAdView == null || LaughTale_nativeAd == null || LaughTale_loadState != LoadState.LOADED) {
            notifyNativeAdShowSkipped();
            return;
        }
        if (!LaughTaleCanShowNativeAd()) {
            // 库存不可用：清掉并补货，避免 UNLOAD 却残留旧 View
            clearLoadedInventory();
            LaughTale_loadState = LoadState.UNLOAD;
            LaughTale_adPrice = 0;
            notifyNativeAdShowSkipped();
            LaughTaleLoadNativeAdView();
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            if (LaughTale_isOpen || LaughTale_nativeAdView == null || LaughTale_loadState != LoadState.LOADED) {
                notifyNativeAdShowSkipped();
                return;
            }
            if (!LaughTaleCanShowNativeAd()) {
                clearLoadedInventory();
                LaughTale_loadState = LoadState.UNLOAD;
                notifyNativeAdShowSkipped();
                LaughTaleLoadNativeAdView();
                return;
            }
            LaughTale_isOpen = true;
            LaughTale_countdownFinished = false;
            if (showAs == DisplayMode.HALF) {
                showNativeAdHalfOverlay();
            } else {
                showNativeAdFullscreenOverlay();
            }
            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
            }
        });
    }

    private void showNativeAdFullscreenOverlay() {
        if (fullscreenCountdownView != null) {
            fullscreenCountdownView.setVisibility(View.VISIBLE);
            fullscreenCountdownView.setText(LaughTaleNativeAdConstants.COUNTDOWN_FULLSCREEN_SEC + "s");
        }
        if (fullscreenCloseIcon != null) {
            fullscreenCloseIcon.setVisibility(View.GONE);
        }

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        attachOrReuseOverlay(overlayParams);
        LaughTale_overlayRoot.setBackgroundColor(Color.TRANSPARENT);
        attachNativeAdViewToOverlay(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyFullscreenSafeRootInsets();
        registerNativeAdAfterLayout();
        startFullscreenCountdown();
    }

    /** Overlay 复用：首次 addContentView，之后只改 LayoutParams + VISIBLE。 */
    private void attachOrReuseOverlay(FrameLayout.LayoutParams overlayParams) {
        LaughTaleInitNativeAdapter();
        if (LaughTale_overlayRoot == null || LaughTale_activity == null) {
            return;
        }
        // Activity 重建后旧 overlay 可能仍挂在旧 decor 上，必须卸下再挂到当前 Activity
        ViewParent overlayParent = LaughTale_overlayRoot.getParent();
        if (overlayParent instanceof ViewGroup) {
            ViewGroup oldParent = (ViewGroup) overlayParent;
            View currentContent = LaughTale_activity.findViewById(android.R.id.content);
            if (currentContent != null && oldParent != currentContent && !isDescendantOf(oldParent, currentContent)) {
                oldParent.removeView(LaughTale_overlayRoot);
            }
        }
        if (LaughTale_overlayRoot.getParent() == null) {
            LaughTale_activity.addContentView(LaughTale_overlayRoot, overlayParams);
        } else {
            LaughTale_overlayRoot.setLayoutParams(overlayParams);
        }
        LaughTale_overlayRoot.setVisibility(View.VISIBLE);
    }

    /**
     * 防止 IllegalStateException: The specified child already has a parent。
     * 多重展示 / 顶掉再 show 时，NativeAdView 可能仍挂在旧 parent 上。
     */
    private void attachNativeAdViewToOverlay(FrameLayout.LayoutParams childParams) {
        if (LaughTale_overlayRoot == null || LaughTale_nativeAdView == null) {
            return;
        }
        ViewParent childParent = LaughTale_nativeAdView.getParent();
        if (childParent instanceof ViewGroup) {
            ((ViewGroup) childParent).removeView(LaughTale_nativeAdView);
        }
        LaughTale_overlayRoot.removeAllViews();
        LaughTale_overlayRoot.addView(LaughTale_nativeAdView, childParams);
    }

    private static boolean isDescendantOf(View child, View ancestor) {
        View current = child;
        while (current != null) {
            if (current == ancestor) {
                return true;
            }
            Object parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
        return false;
    }

    private void registerNativeAdAfterLayout() {
        final NativeAd ad = LaughTale_nativeAd;
        final NativeAdView adView = LaughTale_nativeAdView;
        if (ad == null || adView == null) {
            return;
        }
        adView.post(() -> {
            if (LaughTale_nativeAd != ad || LaughTale_nativeAdView != adView || !LaughTale_isOpen) {
                return;
            }
            if (adView.getWidth() <= 0 || adView.getHeight() <= 0) {
                adView.post(() -> {
                    if (LaughTale_nativeAd == ad && LaughTale_nativeAdView == adView && LaughTale_isOpen) {
                        populateNativeAdView(ad, adView);
                    }
                });
                return;
            }
            populateNativeAdView(ad, adView);
        });
    }

    /**
     * 对齐竞品 FsnAd.AdDialog：
     * safeRootView.topPadding = max(fsn_ad_choice_size, cutoutTop - closeBtnSize/2)
     * 把关闭钮顶到「谷歌广告」AdChoices 下方。
     */
    private void applyFullscreenSafeRootInsets() {
        if (LaughTale_nativeAdView == null || LaughTale_activity == null) {
            return;
        }
        final View safeRoot = LaughTale_nativeAdView.findViewById(R.id.safeRootView);
        if (safeRoot == null) {
            return;
        }
        final int closeBtnSize = LaughTale_activity.getResources()
                .getDimensionPixelSize(R.dimen.fsn_close_btn_size);
        final int adChoiceSize = LaughTale_activity.getResources()
                .getDimensionPixelSize(R.dimen.fsn_ad_choice_size);
        ViewCompat.setOnApplyWindowInsetsListener(safeRoot, (view, windowInsets) -> {
            Insets cutout = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout());
            int topPad = Math.max(adChoiceSize, cutout.top - (closeBtnSize / 2));
            view.setPadding(0, topPad, 0, 0);
            return windowInsets;
        });
        // 无刘海时也至少顶开 AdChoices 高度
        safeRoot.setPadding(0, adChoiceSize, 0, 0);
        ViewCompat.requestApplyInsets(safeRoot);
    }

    private void showNativeAdHalfOverlay() {
        applyHalfCloseSide();
        resetHalfCountdownUi();

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM);
        attachOrReuseOverlay(overlayParams);
        LaughTale_overlayRoot.setBackgroundColor(Color.TRANSPARENT);
        attachNativeAdViewToOverlay(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp2px(LaughTale_activity, LaughTaleNativeAdConstants.HALF_HEIGHT_DP)));
        registerNativeAdAfterLayout();
        startHalfCountdown();
    }

    private void startFullscreenCountdown() {
        cancelCountdown();
        final int seconds = LaughTaleNativeAdConstants.COUNTDOWN_FULLSCREEN_SEC;
        countdownTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (fullscreenCountdownView != null) {
                    long sec = Math.max(1, (millisUntilFinished + 999) / 1000);
                    fullscreenCountdownView.setText(sec + "s");
                }
            }

            @Override
            public void onFinish() {
                LaughTale_countdownFinished = true;
                if (fullscreenCountdownView != null) {
                    fullscreenCountdownView.setVisibility(View.GONE);
                }
                if (fullscreenCloseIcon != null) {
                    fullscreenCloseIcon.setVisibility(View.VISIBLE);
                }
            }
        };
        countdownTimer.start();
    }

    /** 半屏：一侧倒计时 3→2→1，结束后另一侧显示 X 并可关。 */
    private void startHalfCountdown() {
        cancelCountdown();
        resetHalfCountdownUi();
        final int seconds = LaughTaleNativeAdConstants.COUNTDOWN_HALF_SEC;
        countdownTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (halfCountdownView != null) {
                    long sec = Math.max(1, (millisUntilFinished + 999) / 1000);
                    halfCountdownView.setText(String.valueOf(sec));
                }
            }

            @Override
            public void onFinish() {
                LaughTale_countdownFinished = true;
                // 倒计时结束：藏倒计时圆，对侧显示 X 并可点
                if (halfCardCountdown != null) {
                    halfCardCountdown.setVisibility(View.INVISIBLE);
                }
                if (halfCountdownView != null) {
                    halfCountdownView.setVisibility(View.GONE);
                }
                if (halfCardAction != null) {
                    halfCardAction.setVisibility(View.VISIBLE);
                }
                if (halfCloseIcon != null) {
                    halfCloseIcon.setVisibility(View.VISIBLE);
                }
                if (halfCloseButton != null) {
                    halfCloseButton.setClickable(true);
                    halfCloseButton.setFocusable(true);
                }
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

    private void cancelRetry() {
        LaughTaleAdRetryScheduler.cancel(LaughTale_retryFuture);
        LaughTale_retryFuture = null;
    }

    private void LaughTaleScheduleRetry() {
        cancelRetry();
        LaughTale_retryAttempt++;
        long delaySec = (long) Math.pow(2, Math.min(6, LaughTale_retryAttempt));
        LaughTale_retryNotBeforeMs = System.currentTimeMillis() + delaySec * 1000L;
        // 调度线程回调里必须切回主线程再 destroy/inflate View
        LaughTale_retryFuture = LaughTaleAdRetryScheduler.scheduleSeconds(() -> {
            Activity activity = LaughTale_activity;
            if (activity != null && !activity.isDestroyed()) {
                activity.runOnUiThread(this::LaughTaleLoadNativeAdView);
            }
        }, delaySec);
    }

    private void LaughTaleHideView(boolean clickedCloseButton, boolean silentClose, boolean removeOverlayChildrenFirst) {
        Runnable hide = () -> {
            if (!LaughTale_isOpen) {
                return;
            }
            cancelCountdown();
            if (LaughTale_nativeAdView != null) {
                detachFromParent(LaughTale_nativeAdView);
            }
            if (LaughTale_overlayRoot != null) {
                if (removeOverlayChildrenFirst || LaughTale_overlayRoot.getChildCount() > 0) {
                    LaughTale_overlayRoot.removeAllViews();
                }
                // 复用 overlay：隐藏即可，避免反复 addContentView / detach
                LaughTale_overlayRoot.setVisibility(View.GONE);
            }
            LaughTale_isOpen = false;
            onNativeAdClosed(clickedCloseButton, silentClose);
        };
        Activity activity = LaughTale_activity;
        if (activity == null) {
            // Activity 已空仍要清状态，避免永远 isOpen 挡补货
            LaughTale_isOpen = false;
            cancelCountdown();
            clearLoadedInventory();
            LaughTale_loadState = LoadState.UNLOAD;
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            hide.run();
        } else {
            activity.runOnUiThread(hide);
        }
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
        if (LaughTale_nativeAd != null) {
            LaughTale_nativeAd.destroy();
        }
        if (LaughTale_nativeAdView != null) {
            LaughTale_nativeAdView.destroy();
        }
    }

    private void clearLoadedInventory() {
        tryDestroyAd();
        LaughTale_nativeAd = null;
        LaughTale_nativeAdView = null;
        LaughTale_adPrice = 0;
    }

    private void onNativeAdClosed(boolean clickedCloseButton, boolean silentClose) {
        clearLoadedInventory();
        LaughTale_loadState = LoadState.UNLOAD;
        LaughTale_adPrice = 0;
        if (LaughTale_nativeListener != null) {
            LaughTale_nativeListener.LaughTaleOnNativeAdClosed(clickedCloseButton, silentClose);
        }
        if (LaughTaleNativeAdConstants.LOAD_NEW_AFTER_CLOSE) {
            LaughTaleLoadNativeAdView();
        }
    }

    private static int dp2px(Context context, float dp) {
        return (int) ((dp * context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    private static String LaughTaleResolveNetworkName(NativeAd ad) {
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
