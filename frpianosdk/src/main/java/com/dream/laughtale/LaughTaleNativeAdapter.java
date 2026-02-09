package com.dream.laughtale;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.OvershootInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;

import org.jetbrains.annotations.Nullable;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LaughTaleNativeAdapter extends Activity implements MaxAdRevenueListener {

    // --- 调度与广告相关 ---
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private MaxNativeAdLoader LaughTale_nativeAdLoader;
    private MaxNativeAdView LaughTale_nativeAdView;
    private MaxAd LaughTale_nativeAd;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private int LaughTale_nativeRetryAttempt;
    public LaughTaleNativeListener LaughTale_nativeListener;
    private double LaughTale_adPrice = 0;

    // --- 视图与状态 ---
    private View adView;
    private LinearLayout LaughTale_root;
    public boolean LaughTale_isOpen = false;
    private AnimatorSet pulseSet;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public void LaughTaleInitNativeAdapter() {
        LinearLayout linearLayout = new LinearLayout(LaughTale_activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.LaughTale_root = linearLayout;

        LayoutInflater from = LayoutInflater.from(LaughTale_activity);
        this.adView = from.inflate(R.layout.laughtale_collapse_native_ad_layout, (ViewGroup) null);

        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(this.adView)
                .setTitleTextViewId(R.id.title_text_view)
                .setBodyTextViewId(R.id.body_text_view)
                .setStarRatingContentViewGroupId(R.id.star_rating_view)
                .setAdvertiserTextViewId(R.id.advertiser_text_view)
                .setIconImageViewId(R.id.icon_image_view)
                .setOptionsContentViewGroupId(R.id.ad_options_view)
                .setMediaContentViewGroupId(R.id.media_view)
                .setCallToActionButtonId(R.id.cta_button)
                .build();

        LaughTale_nativeAdView = new MaxNativeAdView(binder, LaughTale_activity);
        LaughTale_nativeAdLoader = new MaxNativeAdLoader(LaughTale_ad_unit, LaughTale_activity);
        LaughTale_nativeAdLoader.setRevenueListener(this);
        LaughTale_nativeAdLoader.setNativeAdListener(new MaxNativeAdListener() {
            @Override
            public void onNativeAdLoaded(@Nullable final MaxNativeAdView nativeAdView, final MaxAd ad) {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "NativeLoaded");

                // 清理上一个广告，避免泄漏
                if (LaughTale_nativeAd != null) {
                    LaughTale_nativeAdLoader.destroy(LaughTale_nativeAd);
                }
                LaughTale_nativeRetryAttempt = 0;

                // 保存引用
                LaughTale_nativeAd = ad;
                LaughTale_adPrice = ad.getRevenue();
                if (LaughTale_adPrice == 0) {
                    LaughTale_adPrice = 0.02;
                }
            }

            @Override
            public void onNativeAdLoadFailed(final String adUnitId, final MaxError error) {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADNativeFailed " + error.getMessage());
                LaughTale_nativeRetryAttempt++;
                long delay = (long) Math.pow(2, Math.min(6, LaughTale_nativeRetryAttempt));
                scheduler.schedule(() -> {
                    if (LaughTale_activity.isFinishing() || isDestroyed()) return; // 如果 Activity 已经销毁，不再执行
                    mainHandler.post(() -> LaughTaleLoadNativeAdView());
                }, delay, TimeUnit.SECONDS);
            }

            @Override
            public void onNativeAdClicked(final MaxAd ad) {
                if (LaughTale_nativeListener != null) {
                    LaughTale_nativeListener.LaughTaleOnNativeAdClick();
                }
            }

            @Override
            public void onNativeAdExpired(final MaxAd ad) { }
        });

        // 关闭按钮：常规
        View findViewById = this.adView.findViewById(R.id.close_btn);
        findViewById.setOnClickListener(v -> LaughTaleHideNativeAd(v));

    }

    private void startExpandAnimation() {
        // 强制先让 View 不可见，防止闪现
        LaughTale_nativeAdView.setAlpha(0f);

        LaughTale_nativeAdView.post(() -> {
            int height = LaughTale_nativeAdView.getHeight();
            if (height == 0) height = 1000; // 保底高度

            LaughTale_nativeAdView.setTranslationY(height);
            LaughTale_nativeAdView.setAlpha(1.0f); // 准备好了，显示并开始动画

            LaughTale_nativeAdView.animate()
                    .translationY(0)
                    .setDuration(250)
                    .setInterpolator(new OvershootInterpolator(0.8f)) // 0.8f 控制回弹力度，更接近竞品
                    .withStartAction(() -> LaughTale_nativeAdView.setLayerType(View.LAYER_TYPE_HARDWARE, null)) // 开启 GPU 加速
                    .withEndAction(() -> {
                        LaughTale_nativeAdView.setLayerType(View.LAYER_TYPE_NONE, null); // 结束后释放内存
                    })
                    .start();
        });

        // 关闭按钮逻辑：使用 XML 里的 ID
        final View closeBtn = this.adView.findViewById(R.id.close_btn);
        if (closeBtn != null) {
            closeBtn.setVisibility(View.GONE);
            closeBtn.setAlpha(0f);
            // --- 商业优化 2: 缩短按钮出现延迟至 1.5s ---
            mainHandler.postDelayed(() -> {
                if (LaughTale_isOpen && closeBtn != null) {
                    closeBtn.setVisibility(View.VISIBLE);
                    closeBtn.animate()
                            .alpha(1.0f)
                            .scaleX(1.0f)
                            .scaleY(1.0f)
                            .setDuration(300)
                            .setInterpolator(new OvershootInterpolator(1.5f)) // 明显的Q弹入场
                            .start();
                }
            }, 1500);
        }
    }

    private void startCollapseAnimation() {
        if (LaughTale_nativeAdView == null) return;

        int height = LaughTale_nativeAdView.getHeight();

        // 执行向下折叠收起的动画
        LaughTale_nativeAdView.animate()
                .translationY(height) // 移动到屏幕下方
                .setDuration(200)
                .withStartAction(() -> LaughTale_nativeAdView.setLayerType(View.LAYER_TYPE_HARDWARE, null)) // 开启 GPU 加速
                .setInterpolator(new AccelerateDecelerateInterpolator())
                .withEndAction(() -> {
                    LaughTale_nativeAdView.setLayerType(View.LAYER_TYPE_NONE, null); // 结束后释放内存
                    // 动画结束后，执行原本的清理逻辑
                    ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
                    if (parentViewGroup != null) {
                        parentViewGroup.removeView(LaughTale_root);
                    }
                    // 恢复位置偏移，方便下次展示
                    LaughTale_nativeAdView.setTranslationY(0);

                    if (LaughTale_nativeListener != null) {
                        LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
                    }
                })
                .start();
    }

    // 避免叠加：保存引用并在下一次前 cancel
    private void startScaleAnimation() {
        final View adContentView = this.adView.findViewById(R.id.cta_button);
        if (adContentView == null) return;
        adContentView.setLayerType(View.LAYER_TYPE_HARDWARE, null); // 持续加速
        // 如果已经在跑，先停掉旧的
        if (pulseSet != null) {
            pulseSet.cancel();
        }

        ObjectAnimator scaleX = ObjectAnimator.ofFloat(adContentView, "scaleX", 1.0f, 1.1f, 1.0f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(adContentView, "scaleY", 1.0f, 1.1f, 1.0f);

        scaleX.setRepeatCount(ValueAnimator.INFINITE);
        scaleY.setRepeatCount(ValueAnimator.INFINITE);

        pulseSet = new AnimatorSet();
        pulseSet.playTogether(scaleX, scaleY);
        pulseSet.setDuration(1000);
        pulseSet.setInterpolator(new AccelerateDecelerateInterpolator());
        pulseSet.start();
    }

    public void LaughTaleLoadNativeAdView() {
        LaughTale_nativeAdLoader.loadAd(LaughTale_nativeAdView);
    }

    public double LaughTaleGetADPrice() {
        return LaughTale_adPrice;
    }

    public void LaughTaleShowNativeAd() {
        LaughTale_activity.runOnUiThread(() -> {
            LaughTale_isOpen = true;
            LaughTale_root.setVisibility(View.VISIBLE);
            LaughTale_adPrice = 0;

            // 防叠加：先从旧父容器移除
            ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
            if (parentViewGroup != null) {
                parentViewGroup.removeView(LaughTale_root);
            }

            // 父布局入场
            FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

            // 子视图：底部对齐
            FrameLayout.LayoutParams layoutParams1 = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
            layoutParams1.gravity = Gravity.BOTTOM;

            LaughTale_activity.addContentView(LaughTale_root, layoutParams);
            LaughTale_root.removeAllViews();
            LaughTale_root.setGravity(Gravity.BOTTOM);
            LaughTale_root.bringToFront();
            LaughTale_root.addView(LaughTale_nativeAdView, layoutParams1);

            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
            }

            // 缩放动画避免叠加
            startScaleAnimation();
            startExpandAnimation();

        });
    }

    public void LaughTaleHideNativeAd(View view) {
        LaughTaleHideView();
    }

    public void LaughTaleAutoHideNativeAd() {
        LaughTaleHideView();
    }

    private void LaughTaleHideView() {
        LaughTale_activity.runOnUiThread(() -> {
            if (!LaughTale_isOpen) return; // 如果已经是在关闭过程中，直接拦截
            LaughTale_isOpen = false;

            // --- 核心优化：停止呼吸动画 ---
            if (pulseSet != null) {
                pulseSet.cancel(); // 立即停止
                pulseSet = null;   // 释放引用

                // 恢复按钮状态，防止下次显示时按钮停留在“放大”或“缩小”的状态
                View ctaButton = this.adView.findViewById(R.id.cta_button);
                if (ctaButton != null) {
                    ctaButton.setScaleX(1.0f);
                    ctaButton.setScaleY(1.0f);
                }
            }
            startCollapseAnimation();
            // 建议：在动画开始后再触发加载，或者给一个微小的延迟
            // 避免加载广告的网络/IO请求抢占 UI 动画的 CPU 资源
            mainHandler.postDelayed(this::LaughTaleLoadNativeAdView, 500);
        });
    }

    @Override
    public void onAdRevenuePaid(MaxAd maxAd) {
        double revenue = maxAd.getRevenue(); // USD
        String networkName = maxAd.getNetworkName();
        String adUnitId = maxAd.getAdUnitId();
        MaxAdFormat adFormat = maxAd.getFormat();
        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue, adFormat.toString(), networkName, adUnitId);
        // LaughTaleBankManager.instance().updateAdStats("native", revenue * 1000);
    }

    @Override
    protected void onDestroy() {
        mainHandler.removeCallbacksAndMessages(null);
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow(); // 立即停止所有重试任务
        }
        // 2. 核心优化：彻底销毁动画引用
        if (pulseSet != null) {
            pulseSet.removeAllListeners(); // 移除监听器
            pulseSet.cancel();             // 取消动画
            pulseSet = null;               // 设为空，方便 GC
        }
        if (LaughTale_nativeAdLoader != null && LaughTale_nativeAd != null) {
            LaughTale_nativeAdLoader.destroy(LaughTale_nativeAd);
            LaughTale_nativeAd = null;
        }
        super.onDestroy();
    }
}
