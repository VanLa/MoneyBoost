package com.dream.laughtale;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
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
    private boolean LaughTale_isMetaMode = false;

    // --- Meta 倒计时 ---
    private final Handler metaHandler = new Handler(Looper.getMainLooper());
    private Runnable metaTick;
    private boolean isMetaCounting = false;
    private int delayTime;
    private TextView delayView;
    private View controlBtn;

    // --- 动画避免叠加 ---
    private ValueAnimator scaleAnimator;

    public void LaughTaleInitNativeAdapter() {
        LinearLayout linearLayout = new LinearLayout(LaughTale_activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.LaughTale_root = linearLayout;

        LayoutInflater from = LayoutInflater.from(LaughTale_activity);
        this.adView = from.inflate(R.layout.laughtale_collapsible_native_half_style, (ViewGroup) null);

        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(this.adView)
                .setTitleTextViewId(R.id.laughtale_inters_title)
                .setBodyTextViewId(R.id.laughtale_inters_text)
                .setIconImageViewId(R.id.laughtale_inters_icon_img)
                .setMediaContentViewGroupId(R.id.laughtale_media_content)
                .setCallToActionButtonId(R.id.laughtale_inters_click_btn)
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

                // 判断 Meta 模式（包含 50% 随机）
                String lowerCase = ad.getNetworkName() != null ? ad.getNetworkName().toLowerCase() : "";
                boolean randomMeta = false;
                if (LaughTaleToolsManager.instance().LaughTale_isTestMetaNative) {
                    Random random = new Random();
                    randomMeta = random.nextBoolean(); // 关键：赋值给变量
                }
                LaughTale_isMetaMode = lowerCase.contains("facebook") || lowerCase.contains("meta") || randomMeta;
            }

            @Override
            public void onNativeAdLoadFailed(final String adUnitId, final MaxError error) {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADNativeFailed " + error.getMessage());
                LaughTale_nativeRetryAttempt++;
                long delay = (long) Math.pow(2, Math.min(6, LaughTale_nativeRetryAttempt));
                scheduler.schedule(LaughTaleNativeAdapter.this::LaughTaleLoadNativeAdView, delay, TimeUnit.SECONDS);
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
        View findViewById = this.adView.findViewById(R.id.laughtale_close_btn);
        findViewById.setOnClickListener(v -> LaughTaleHideNativeAd(v));

        // 关闭按钮：MOD
        View findViewById2 = this.adView.findViewById(R.id.laughtale_mod_close_btn);
        findViewById2.setOnClickListener(v -> {
            findViewById2.setVisibility(View.GONE);
            ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
            if (parentViewGroup != null) {
                parentViewGroup.removeView(LaughTale_root);
            }
            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
            }
        });

        // 关闭按钮：Meta
        View findViewById3 = this.adView.findViewById(R.id.laughtale_meta_close_btn);
        findViewById3.setOnClickListener(v -> LaughTaleHideNativeAd(v));
        this.controlBtn = findViewById3;

        // 倒计时 Text
        this.delayView = this.adView.findViewById(R.id.laughtale_meta_delay_time);
    }

    private void startExpandAnimation() {
        LinearLayout closeArea = this.adView.findViewById(R.id.close_area);
        LaughTale_activity.runOnUiThread(() ->
                closeArea.postDelayed(() -> closeArea.setVisibility(View.VISIBLE), 1000L)
        );
    }

    private void startCollapseAnimation() {
        LinearLayout closeArea = this.adView.findViewById(R.id.close_area);
        closeArea.setVisibility(View.GONE);
        ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
        if (parentViewGroup != null) {
            parentViewGroup.removeView(LaughTale_root);
        }
        if (LaughTale_nativeListener != null) {
            LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
        }
    }

    // 避免叠加：保存引用并在下一次前 cancel
    private void startScaleAnimation() {
        final View adContentView = this.adView.findViewById(R.id.laughtale_inters_click_btn);
        if (scaleAnimator != null) scaleAnimator.cancel();

        scaleAnimator = ValueAnimator.ofFloat(1.0f, 1.1f, 1.0f);
        scaleAnimator.setDuration(1000);
        scaleAnimator.setRepeatMode(ValueAnimator.RESTART);
        scaleAnimator.setRepeatCount(ValueAnimator.INFINITE);
        scaleAnimator.addUpdateListener(a -> {
            float v = (float) a.getAnimatedValue();
            adContentView.setScaleX(v);
            adContentView.setScaleY(v);
        });
        scaleAnimator.start();
    }

    public void LaughTaleLoadNativeAdView() {
        LaughTale_nativeAdLoader.loadAd(LaughTale_nativeAdView);
    }

    public double LaughTaleGetADPrice() {
        return LaughTale_adPrice;
    }

    public void LaughTaleShowNativeAd(boolean isModMode) {
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

            if (LaughTale_isMetaMode) {
                // Meta 模式：展示关闭区域 & 开始倒计时
                LinearLayout closeArea = adView.findViewById(R.id.meta_close_area);
                closeArea.setVisibility(View.VISIBLE);

                // 统一重置 UI
                delayTime = 3;
                delayView.setVisibility(View.VISIBLE);
                delayView.setText(delayTime + "s");
                controlBtn.setVisibility(View.GONE);

                startMetaCountdown(); // 稳定倒计时
            } else {
                // 非 Meta
                if (!isModMode) {
                    startExpandAnimation();
                } else {
                    View closeBtn = adView.findViewById(R.id.laughtale_mod_close_btn);
                    closeBtn.setVisibility(View.VISIBLE);
                }
            }
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
            LaughTale_isOpen = false;

            // 停止倒计时与动画（先停，再移除视图）
            if (scaleAnimator != null) {
                scaleAnimator.cancel();
                scaleAnimator = null;
            }
            stopMetaCountdown();

            if (LaughTale_isMetaMode) {
                LinearLayout closeArea = adView.findViewById(R.id.meta_close_area);
                closeArea.setVisibility(View.GONE);
                // 不在 hide 时改“3s”，避免与下次 show 冲突
                controlBtn.setVisibility(View.GONE);

                ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
                if (parentViewGroup != null) {
                    parentViewGroup.removeView(LaughTale_root);
                }
                if (LaughTale_nativeListener != null) {
                    LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
                }
            } else {
                startCollapseAnimation();
            }
        });

        // 提前预加载下一条
        LaughTaleLoadNativeAdView();
    }

    // --- Meta 倒计时：统一入口 ---
    private void startMetaCountdown() {
        stopMetaCountdown(); // 先清旧的，避免叠加

        isMetaCounting = true;
        metaTick = new Runnable() {
            @Override
            public void run() {
                if (!isMetaCounting) return;

                delayTime--;
                if (delayTime <= 0) {
                    delayView.setVisibility(View.GONE);
                    controlBtn.setVisibility(View.VISIBLE);
                    Animation fadeIn = AnimationUtils.loadAnimation(LaughTale_activity, R.anim.laughtale_fade_in);
                    controlBtn.startAnimation(fadeIn);

                    isMetaCounting = false; // 结束
                    metaTick = null;
                } else {
                    delayView.setText(delayTime + "s");
                    metaHandler.postDelayed(this, 1000L);
                }
            }
        };
        metaHandler.postDelayed(metaTick, 1000L);
    }

    private void stopMetaCountdown() {
        isMetaCounting = false;
        if (metaTick != null) {
            metaHandler.removeCallbacks(metaTick);
            metaTick = null;
        }
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
        // 安全清理：避免 Activity 销毁后仍有回调
        stopMetaCountdown();
        if (scaleAnimator != null) {
            scaleAnimator.cancel();
            scaleAnimator = null;
        }
        if (LaughTale_nativeAdLoader != null && LaughTale_nativeAd != null) {
            LaughTale_nativeAdLoader.destroy(LaughTale_nativeAd);
            LaughTale_nativeAd = null;
        }
        super.onDestroy();
    }
}
