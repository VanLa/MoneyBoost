package com.dream.laughtale;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;
import com.applovin.sdk.AppLovinSdkUtils;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LaughTaleNativeAdapter implements MaxAdRevenueListener {

    // --- 调度与广告相关 ---
    // 共享调度线程，避免每个 Native 实例创建独立线程
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private MaxNativeAdLoader LaughTale_nativeAdLoader;
    private MaxNativeAdView LaughTale_nativeAdView;
    private MaxAd LaughTale_nativeAd;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private int LaughTale_nativeRetryAttempt;
    public LaughTaleNativeListener LaughTale_nativeListener;
    private double LaughTale_adPrice = 0;
    private CloseStyle closeStyle;
    private View controlBtn;
    private LinearLayout viewLayout;
    private LinearLayout titleLayout;
    private FrameLayout topLayout;
    private FrameLayout mediaLayout;
    private TextView clickBtnText;
    private RelativeLayout bodyLayout;
    private FrameLayout bottomLayout;
    private FrameLayout buttonLayout;
    private int delayTime;
    private TextView delayView;
    private View adView;
    private LinearLayout LaughTale_root;
    public boolean LaughTale_isOpen = false;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable countdownRunnable = new Runnable() {
        @Override
        public void run() {
            if (!LaughTale_isOpen) return;
            if (delayTime <= 0) {
                delayView.setVisibility(View.GONE);
                controlBtn.setVisibility(View.VISIBLE);
            } else {
                delayView.setText(delayTime + "s");
                delayTime--;
                mainHandler.postDelayed(this, 1000L);
            }
        }
    };

    public class CloseStyle {
        int delayTime;
    }

    // 1. 数值配置类：将 Meta 和默认配置封装
    private static class LayoutConfig {
        float closeS, bottomM, mediaH, titleH, bodyH, btnH, bottomH;
        int delayShow;

        // 构造函数
        LayoutConfig(float cS, float bM, float mH, float tH, float bH, float btnH, float botH, int dS) {
            this.closeS = cS; this.bottomM = bM; this.mediaH = mH;
            this.titleH = tH; this.bodyH = bH; this.btnH = btnH;
            this.bottomH = botH; this.delayShow = dS;
        }
    }

    // 预设两套配置：Meta 微调版 vs 默认还原版
    private final LayoutConfig META_CONFIG = new LayoutConfig(20f, 20f, 220f, 70f, 60f, 85f, 5f, 1);
    private final LayoutConfig DEFAULT_CONFIG = new LayoutConfig(25f, 10f, 230f, 80f, 45f, 78f, 1f, 1);

    private void initViewReferences(View root) {
        // 在这里统一获取引用，避免 Resize 时重复寻找
        this.viewLayout =(LinearLayout) root.findViewById(R.id.native_layout); // 对应你 XML 里的容器
        this.mediaLayout = (FrameLayout) root.findViewById(R.id.media_layout);
        this.titleLayout =(LinearLayout) root.findViewById(R.id.title_layout);
        this.bodyLayout = (RelativeLayout) root.findViewById(R.id.body_text_layout);
        this.buttonLayout = (FrameLayout) root.findViewById(R.id.cta_button_layout);
        this.bottomLayout = (FrameLayout) root.findViewById(R.id.bottom_layout);
        this.topLayout = (FrameLayout) root.findViewById(R.id.top_layout);
        this.clickBtnText = (TextView) root.findViewById(R.id.xa_inters_click_btn);
    }

    // 2. 核心重构：统一的尺寸调整方法
    private void applyLayout(boolean isMeta) {
        LayoutConfig config = isMeta ? META_CONFIG : DEFAULT_CONFIG;

        // 批量更新高度
        updateViewHeight(mediaLayout, config.mediaH);
        updateViewHeight(titleLayout, config.titleH);
        updateViewHeight(bodyLayout, config.bodyH);
        updateViewHeight(buttonLayout, config.btnH);
        updateViewHeight(bottomLayout, config.bottomH);

        // 计算并设置总高度 (各部分之和 + margin)
        float totalH = config.mediaH + config.titleH + config.bodyH + config.btnH + config.bottomH + config.bottomM;
        updateViewHeight(viewLayout, totalH);

        // 按钮文字大小微调
        if (clickBtnText != null) {
            clickBtnText.setTextSize(config.btnH / 3.0f);
        }

        // 在 applyLayout 方法内的 topLayout 处理块中修改：
        if (topLayout != null) {
            float topH = isMeta ? (2.0f * config.bottomM + config.closeS) : Math.max(27f, (2.0f * config.bottomM + config.closeS));
            LinearLayout.LayoutParams tLp = (LinearLayout.LayoutParams) topLayout.getLayoutParams();
            tLp.height = dp2px(LaughTale_activity, topH);

            // 核心修改：非 Meta 时确保 margin 归零，防止布局污染
            tLp.bottomMargin = isMeta ? (dp2px(LaughTale_activity, 2.0f * config.bottomM) * (-1)) : 0;

            topLayout.setLayoutParams(tLp);
        }

        // 关闭按钮与倒计时位置
        updateViewSize(controlBtn, config.closeS, config.closeS, config.bottomM);

        float delayW = isMeta ? (1.5f * config.closeS) : (10.0f + Math.max(25f, config.closeS));
        updateViewSize(delayView, delayW, Math.max(25f, config.closeS), config.bottomM);

        // 显隐逻辑
        if (config.delayShow <= 0) {
            if (delayView != null) delayView.setVisibility(View.GONE);
            if (controlBtn != null) controlBtn.setVisibility(View.VISIBLE);
        }
    }

    private void InitViewStyle(){
        if (this.closeStyle == null){
            this.closeStyle = new CloseStyle();
        }
        this.closeStyle.delayTime = 3;
        setNativeClose();
    }

    public void LaughTaleInitNativeAdapter() {
        LinearLayout linearLayout = new LinearLayout(LaughTale_activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.LaughTale_root = linearLayout;

        LayoutInflater from = LayoutInflater.from(LaughTale_activity);
        this.adView = from.inflate(R.layout.laughtale_max_native, (ViewGroup) null);
        initViewReferences(this.adView);
        InitViewStyle();
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(this.adView)
                .setTitleTextViewId(R.id.xa_inters_title)
                .setBodyTextViewId(R.id.xa_inters_desc)
                .setIconImageViewId(R.id.xa_inters_icon_img)
                .setMediaContentViewGroupId(R.id.xa_media_content)
                .setCallToActionButtonId(R.id.xa_inters_click_btn)
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
                    if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) return; // 如果 Activity 已经销毁，不再执行
                    mainHandler.post(() -> LaughTaleLoadNativeAdView());
                }, delay, TimeUnit.SECONDS);
            }

            @Override
            public void onNativeAdClicked(final MaxAd ad) {
            }

            @Override
            public void onNativeAdExpired(final MaxAd ad) { }
        });

        // 关闭按钮：常规
        View findViewById = this.adView.findViewById(R.id.b2x_inters_close_left);
        findViewById.setOnClickListener(v -> LaughTaleHideNativeAd(v));
    }

    private void startCountdown() {
        // 仅清理倒计时任务，避免误清理其他延时逻辑
        mainHandler.removeCallbacks(countdownRunnable);
        mainHandler.post(countdownRunnable);
    }

    public void LaughTaleLoadNativeAdView() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) return;
        if (LaughTale_nativeAdLoader == null || LaughTale_nativeAdView == null) return;
        LaughTale_nativeAdLoader.loadAd(LaughTale_nativeAdView);
    }

    public double LaughTaleGetADPrice() {
        return LaughTale_adPrice;
    }

    public void LaughTaleShowNativeAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) return;
        if (LaughTale_nativeAdView == null || LaughTale_root == null) return;
        LaughTale_activity.runOnUiThread(() -> {
            applyLayout(IsMeta());
            LaughTale_isOpen = true;
            LaughTale_root.setVisibility(View.VISIBLE);
            LaughTale_adPrice = 0;
            delayTime = closeStyle.delayTime;
            startCountdown();

            ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
            if (parentViewGroup != null){
                parentViewGroup.removeView(LaughTale_root);
            }
            int heightDp = MaxAdFormat.BANNER.getAdaptiveSize(LaughTale_activity).getHeight();
            int heightPx = AppLovinSdkUtils.dpToPx(LaughTale_activity, heightDp) + 5;
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -1);
            layoutParams.setMargins(0,0,0,heightPx);
            LinearLayout.LayoutParams layoutParams1 = new LinearLayout.LayoutParams(-1, -1);
            LaughTale_activity.addContentView(LaughTale_root,layoutParams);
            LaughTale_root.removeAllViews();
            LaughTale_root.setGravity(16);
            LaughTale_root.setClickable(true);
            LaughTale_root.bringToFront();
            LaughTale_root.addView(LaughTale_nativeAdView,layoutParams1);

            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
            }

        });
    }

    private void setNativeClose() {
        View findViewById = this.adView.findViewById(R.id.b2x_inters_close_left);
        findViewById.setVisibility(View.GONE);
        this.controlBtn = findViewById;
        this.controlBtn.setOnClickListener(new View.OnClickListener() { // from class: com.b2x.max.NativeIntersAd.2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LaughTaleHideNativeAd(view);
            }
        });
        TextView textView = (TextView) this.adView.findViewById(R.id.delay_time);
        this.delayView = textView;
        this.delayView.setVisibility(View.VISIBLE);
        textView.setText(this.closeStyle.delayTime + "s");
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
            ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
            if (parentViewGroup != null){
                parentViewGroup.removeView(LaughTale_root);
            }
            if (null != LaughTale_nativeListener){
                LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
            }
            // 关闭后按业务需求重置状态
            InitViewStyle();
            mainHandler.removeCallbacks(countdownRunnable);
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

    public boolean IsMeta() {
        // --- 1. 测试逻辑：优先级最高 ---
        if (LaughTaleToolsManager.instance().LaughTale_isTestMetaNative) {
            boolean isSimulatedMeta = Math.random() < 0.5;
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("TEST_MODE", "模拟悬浮状态: " + isSimulatedMeta);
            return isSimulatedMeta;
        }

        // --- 2. 安全检查 ---
        if (LaughTale_nativeAd == null || LaughTale_nativeAd.getNetworkName() == null) {
            // 默认返回 true，因为你希望除了 Google 系以外都用这种逻辑
            return true;
        }
        // --- 3. 核心过滤逻辑 ---
        String networkName = LaughTale_nativeAd.getNetworkName().toLowerCase();

        // 如果包含 admob 或 google (包含 Google Ad Manager)，则返回 false（使用标准布局）
        if (networkName.contains("admob") || networkName.contains("google")) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("LayoutStyle", "识别为 Google 系，使用标准布局");
            return false;
        }

        // 其他所有平台（Meta, AppLovin, Mintegral, Pangle 等）全部返回 true（使用负 Margin 悬浮布局）
        return true;
    }

    private int dp2px(Context context, float f) {
        return (int) ((f * context.getResources().getDisplayMetrics().density) + 0.5f);
    }

    // 工具方法：设置高度
    private void updateViewHeight(View view, float dp) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        lp.height = dp2px(LaughTale_activity, dp);
        view.setLayoutParams(lp);
    }

    // 工具方法：设置大小与 Margin
    private void updateViewSize(View view, float wDp, float hDp, float marginBDp) {
        if (view == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        lp.width = dp2px(LaughTale_activity, wDp);
        lp.height = dp2px(LaughTale_activity, hDp);
        lp.bottomMargin = dp2px(LaughTale_activity, marginBDp);
        view.setLayoutParams(lp);
    }
}
