package com.dream.laughtale;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    // --- 调度与广告相关 ---
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
        InitViewStyle();
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder(this.adView)
                .setTitleTextViewId(R.id.ad_headline)
                .setBodyTextViewId(R.id.ad_body)
                .setIconImageViewId(R.id.ad_app_icon)
                .setMediaContentViewGroupId(R.id.ad_media)
                .setCallToActionButtonId(R.id.ad_call_to_action)
                .setOptionsContentViewGroupId(R.id.options_view)
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
                LaughTaleLoadNativeAdView();
            }
        });

        // 关闭按钮：仅用代码绑定，勿在 layout 里写 android:onClick（否则会要求 Activity 提供同名 public 方法）
        View findViewById = this.adView.findViewById(R.id.btn_close);
        findViewById.setOnClickListener(this::onCloseButtonClicked);

        LinearLayout.LayoutParams nativeLayoutParams = new LinearLayout.LayoutParams(-1, -1);
        LaughTale_root.addView(LaughTale_nativeAdView, nativeLayoutParams);
        LaughTale_root.setVisibility(View.GONE);
        LinearLayout.LayoutParams rootLayoutParams = new LinearLayout.LayoutParams(-1, -1);
        LaughTale_activity.addContentView(LaughTale_root, rootLayoutParams);
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

    public boolean LaughTaleCanShowNativeAd() {
        if (LaughTale_nativeAd == null) {
            return false;
        }
        MaxNativeAd nativeAd = LaughTale_nativeAd.getNativeAd();
        return nativeAd != null && !nativeAd.isExpired();
    }

    public void LaughTaleShowNativeAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) return;
        if (LaughTale_nativeAdView == null || LaughTale_root == null) return;
        if (!LaughTaleCanShowNativeAd()) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "NativeShowSkipped not ready or expired");
            LaughTale_adPrice = 0;
            LaughTaleLoadNativeAdView();
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            LaughTale_isOpen = true;
            LaughTale_adPrice = 0;
            delayTime = closeStyle.delayTime;
            startCountdown();
            LaughTale_root.setGravity(16);
            LaughTale_root.setClickable(true);
            LaughTale_root.setVisibility(View.VISIBLE);
            LaughTale_root.bringToFront();

            if (LaughTale_nativeListener != null) {
                LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
            }

        });
    }

    private void setNativeClose() {
        View findViewById = this.adView.findViewById(R.id.btn_close);
        findViewById.setVisibility(View.GONE);
        this.controlBtn = findViewById;
        this.controlBtn.setOnClickListener(this::onCloseButtonClicked);
        TextView textView = (TextView) this.adView.findViewById(R.id.delay_time);
        this.delayView = textView;
        this.delayView.setVisibility(View.VISIBLE);
        textView.setText(this.closeStyle.delayTime + "s");
    }

    private void onCloseButtonClicked(View view) {
        LaughTaleHideView();
    }

    public void LaughTaleAutoHideNativeAd() {
        LaughTaleHideView();
    }

    private void LaughTaleHideView() {
        LaughTale_activity.runOnUiThread(() -> {
            if (!LaughTale_isOpen) return; // 如果已经是在关闭过程中，直接拦截
            LaughTale_isOpen = false;
            LaughTale_root.setVisibility(View.GONE);
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
}
