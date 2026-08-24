package com.dream.laughtale;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.AnimationDrawable;
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
 * FULLSCREEN：FSN 全屏 overlay（3s 倒计时 + 灰色 X）
 * HALF：历史「更换广告模版」b2x 卡片（居中 + 半透明遮罩，统一关闭尺寸）
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
    /** 只有完成 attach/layout/register 后才算真正可展示。 */
    private boolean LaughTale_nativeRegistered = false;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleNativeListener LaughTale_nativeListener;
    /** FULLSCREEN 或 HALF，初始化时设定 */
    public DisplayMode LaughTale_displayMode = DisplayMode.FULLSCREEN;
    private View closeFrame;
    private TextView countdownView;
    private ImageView closeIcon;
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

    /** 被其它广告顶掉时静默关闭，不触发 Unity CLOSE */
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
        bindCloseViews(nativeAdView);
        if (LaughTale_displayMode == DisplayMode.HALF) {
            applyHalfTemplateLayout(nativeAdView);
        } else {
            applyUnifiedCloseButtonSize(nativeAdView);
        }
        return nativeAdView;
    }

    private void bindCloseViews(NativeAdView nativeAdView) {
        closeFrame = nativeAdView.findViewById(R.id.btnClose);
        countdownView = nativeAdView.findViewById(R.id.tv_countdown);
        closeIcon = nativeAdView.findViewById(R.id.iv_close);
        LaughTale_countdownFinished = false;
        resetCloseCountdownUi();
        if (closeFrame != null) {
            closeFrame.setOnClickListener(v -> {
                if (LaughTale_countdownFinished) {
                    LaughTaleHideView(true, false, false);
                }
            });
        }
    }

    /** 关闭钮尺寸统一取 closeSize，不区分 Meta / 非 Meta。 */
    private void applyUnifiedCloseButtonSize(NativeAdView nativeAdView) {
        if (nativeAdView == null || LaughTale_activity == null) {
            return;
        }
        View close = nativeAdView.findViewById(R.id.btnClose);
        if (close == null) {
            return;
        }
        float sizeDp = LaughTaleFirebaseManager.instance().LaughTale_native_close_size;
        if (sizeDp <= 0f) {
            sizeDp = LaughTaleLayoutSize.DEFAULT_CLOSE_SIZE_DP;
        }
        int px = dp2px(LaughTale_activity, sizeDp);
        ViewGroup.LayoutParams lp = close.getLayoutParams();
        if (lp != null) {
            lp.width = px;
            lp.height = px;
            close.setLayoutParams(lp);
        }
        // 半屏倒计时条高度跟关闭钮对齐（历史 DEFAULT 逻辑，去掉 Meta 分支）
        if (LaughTale_displayMode == DisplayMode.HALF && countdownView != null) {
            ViewGroup.LayoutParams delayLp = countdownView.getLayoutParams();
            if (delayLp != null) {
                delayLp.width = dp2px(LaughTale_activity, 10f + Math.max(25f, sizeDp));
                delayLp.height = dp2px(LaughTale_activity, Math.max(25f, sizeDp));
                countdownView.setLayoutParams(delayLp);
            }
        }
    }

    /**
     * 半屏 b2x 卡片固定用历史 DEFAULT 尺寸（不再按 Meta 切换）。
     * media 230 / title 80 / body 45 / btn 78 / card≈434dp
     */
    private void applyHalfTemplateLayout(NativeAdView nativeAdView) {
        if (nativeAdView == null || LaughTale_activity == null) {
            return;
        }
        updateViewHeightDp(nativeAdView.findViewById(R.id.media_layout), 230f);
        updateViewHeightDp(nativeAdView.findViewById(R.id.title_layout), 80f);
        updateViewHeightDp(nativeAdView.findViewById(R.id.body_text_layout), 45f);
        updateViewHeightDp(nativeAdView.findViewById(R.id.cta_button_layout), 78f);
        updateViewHeightDp(nativeAdView.findViewById(R.id.bottom_layout), 1f);
        // media+title+body+btn+bottom+bottomM(10)
        updateViewHeightDp(nativeAdView.findViewById(R.id.native_layout), 230f + 80f + 45f + 78f + 1f + 10f);
        TextView cta = nativeAdView.findViewById(R.id.ad_call_to_action);
        if (cta != null) {
            cta.setTextSize(78f / 3.0f);
        }
        applyUnifiedCloseButtonSize(nativeAdView);
    }

    private void updateViewHeightDp(View view, float heightDp) {
        if (view == null || LaughTale_activity == null) {
            return;
        }
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) {
            return;
        }
        lp.height = dp2px(LaughTale_activity, heightDp);
        view.setLayoutParams(lp);
    }

    private void resetCloseCountdownUi() {
        LaughTale_countdownFinished = false;
        int seconds = LaughTale_displayMode == DisplayMode.HALF
                ? LaughTaleNativeAdConstants.COUNTDOWN_HALF_SEC
                : LaughTaleNativeAdConstants.COUNTDOWN_FULLSCREEN_SEC;
        if (countdownView != null) {
            countdownView.setVisibility(View.VISIBLE);
            countdownView.setText(seconds + "s");
        }
        if (LaughTale_displayMode == DisplayMode.HALF) {
            // b2x：倒计时结束前隐藏关闭钮
            if (closeFrame != null) {
                closeFrame.setVisibility(View.GONE);
            }
        } else if (closeIcon != null) {
            closeIcon.setVisibility(View.GONE);
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
        LaughTale_nativeRegistered = false;
        LaughTale_loadState = LoadState.LOADED;
        // 仅预填文案；registerNativeAd 必须在 View 附着并完成 layout 后调用，
        // 否则校验器会报 Advertiser assets outside native ad view。
        if (LaughTale_displayMode == DisplayMode.HALF) {
            bindHalfAssetTexts(nativeAd, LaughTale_nativeAdView);
        } else {
            bindFullscreenAssetTexts(nativeAd, LaughTale_nativeAdView);
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
        if (LaughTale_displayMode == DisplayMode.HALF) {
            populateHalfNativeAdView(nativeAd, nativeAdView);
        } else {
            populateFullscreenNativeAdView(nativeAd, nativeAdView);
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
            nativeAdView.setIconView(iconView);
        }
        if (mediaView != null) {
            mediaView.setImageScaleType(ImageView.ScaleType.CENTER_CROP);
        }
        // 竞品不 setAdChoicesView：由 SDK 按 AdChoicesPlacement 自动插入
        nativeAdView.registerNativeAd(nativeAd, mediaView);
        LaughTale_nativeRegistered = true;
    }

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
        LaughTale_nativeRegistered = true;
    }

    public boolean LaughTaleCanShowNativeAd() {
        if (LaughTale_loadState != LoadState.LOADED || LaughTale_isOpen) {
            return false;
        }
        // registerNativeAd 在展示流程中、View attach/layout 后执行；不能把它作为
        // 加载 ready 条件，否则会出现“未 register -> 不展示 -> 永远无法 register”的死循环。
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

    /** 半屏展示：b2x 居中卡片 + 半透明遮罩。 */
    public void LaughTaleShowNativeAdHalf() {
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
        resetCloseCountdownUi();

        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        attachOrReuseOverlay(overlayParams);
        LaughTale_overlayRoot.setBackgroundColor(Color.TRANSPARENT);
        attachNativeAdViewToOverlay(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        applyFullscreenSafeRootInsets();
        registerNativeAdAfterLayout();
        startCloseCountdown();
    }

    /**
     * Overlay 挂到当前 Activity content。
     * 已在当前 content 上则只改 LayoutParams；否则先卸旧 parent 再 addView，避免 already has a parent。
     */
    private void attachOrReuseOverlay(FrameLayout.LayoutParams overlayParams) {
        LaughTaleInitNativeAdapter();
        if (LaughTale_overlayRoot == null || LaughTale_activity == null) {
            return;
        }
        ViewGroup content = LaughTale_activity.findViewById(android.R.id.content);
        ViewParent overlayParent = LaughTale_overlayRoot.getParent();
        if (overlayParent == content) {
            LaughTale_overlayRoot.setLayoutParams(overlayParams);
        } else {
            safeRemoveFromParent(LaughTale_overlayRoot);
            if (LaughTale_overlayRoot.getParent() != null) {
                return;
            }
            if (content != null) {
                content.addView(LaughTale_overlayRoot, overlayParams);
            } else {
                LaughTale_activity.addContentView(LaughTale_overlayRoot, overlayParams);
            }
        }
        LaughTale_overlayRoot.setVisibility(View.VISIBLE);
        LaughTale_overlayRoot.bringToFront();
    }

    /**
     * 防止 IllegalStateException: The specified child already has a parent。
     * 多重展示 / 顶掉再 show 时，NativeAdView 可能仍挂在旧 parent 上。
     */
    private void attachNativeAdViewToOverlay(FrameLayout.LayoutParams childParams) {
        if (LaughTale_overlayRoot == null || LaughTale_nativeAdView == null) {
            return;
        }
        safeRemoveFromParent(LaughTale_nativeAdView);
        // 清掉 overlay 里其它残留 child（不含已卸下的 nativeAdView）
        LaughTale_overlayRoot.removeAllViews();
        if (LaughTale_nativeAdView.getParent() != null) {
            return;
        }
        LaughTale_overlayRoot.addView(LaughTale_nativeAdView, childParams);
    }

    /** 若已有 parent 则先 remove，避免 addView crash。 */
    private static void safeRemoveFromParent(View view) {
        if (view == null) {
            return;
        }
        ViewParent parent = view.getParent();
        if (parent instanceof ViewGroup) {
            try {
                ((ViewGroup) parent).removeView(view);
            } catch (RuntimeException ignored) {
                // 极端情况下 parent 已销毁，忽略后由 getParent()==null 守卫
            }
        }
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
        resetCloseCountdownUi();
        applyHalfTemplateLayout(LaughTale_nativeAdView);
        startHalfCtaAnimation(LaughTale_nativeAdView);

        // 对齐历史 b2x：底部留出 Banner 高度，#b3000000 不盖住 Banner
        int bannerReservePx = LaughTaleMediationManager.getInstance().LaughTaleGetBannerReserveHeightPx();
        FrameLayout.LayoutParams overlayParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        overlayParams.bottomMargin = Math.max(0, bannerReservePx);
        attachOrReuseOverlay(overlayParams);
        // 遮罩色在 half layout 根上（#b3000000），overlay 保持透明
        LaughTale_overlayRoot.setBackgroundColor(Color.TRANSPARENT);
        attachNativeAdViewToOverlay(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        // Banner 提到最前，底部露出来的区域可点
        LaughTaleMediationManager.getInstance().LaughTaleBringBannerToFront();
        registerNativeAdAfterLayout();
        startCloseCountdown();
    }

    private void startHalfCtaAnimation(NativeAdView nativeAdView) {
        if (nativeAdView == null) {
            return;
        }
        View streamer = nativeAdView.findViewById(R.id.xa_inters_streamer_img);
        if (streamer == null || !(streamer.getBackground() instanceof AnimationDrawable)) {
            return;
        }
        AnimationDrawable anim = (AnimationDrawable) streamer.getBackground();
        anim.stop();
        anim.start();
    }

    private void startCloseCountdown() {
        cancelCountdown();
        resetCloseCountdownUi();
        final boolean half = LaughTale_displayMode == DisplayMode.HALF;
        final int seconds = half
                ? LaughTaleNativeAdConstants.COUNTDOWN_HALF_SEC
                : LaughTaleNativeAdConstants.COUNTDOWN_FULLSCREEN_SEC;
        countdownTimer = new CountDownTimer(seconds * 1000L, 1000L) {
            @Override
            public void onTick(long millisUntilFinished) {
                if (countdownView != null) {
                    long sec = Math.max(1, (millisUntilFinished + 999) / 1000);
                    countdownView.setText(sec + "s");
                }
            }

            @Override
            public void onFinish() {
                LaughTale_countdownFinished = true;
                if (countdownView != null) {
                    countdownView.setVisibility(View.GONE);
                }
                if (half) {
                    if (closeFrame != null) {
                        closeFrame.setVisibility(View.VISIBLE);
                    }
                } else if (closeIcon != null) {
                    closeIcon.setVisibility(View.VISIBLE);
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
        safeRemoveFromParent(view);
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
        LaughTale_nativeRegistered = false;
        tryDestroyAd();
        LaughTale_nativeAd = null;
        LaughTale_nativeAdView = null;
    }

    private void onNativeAdClosed(boolean clickedCloseButton, boolean silentClose) {
        clearLoadedInventory();
        LaughTale_loadState = LoadState.UNLOAD;
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
