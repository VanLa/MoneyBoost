package com.dream.laughtale;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import com.applovin.mediation.MaxAd;
import com.applovin.mediation.MaxAdFormat;
import com.applovin.mediation.MaxAdRevenueListener;
import com.applovin.mediation.MaxError;
import com.applovin.mediation.nativeAds.MaxNativeAdListener;
import com.applovin.mediation.nativeAds.MaxNativeAdLoader;
import com.applovin.mediation.nativeAds.MaxNativeAdView;
import com.applovin.mediation.nativeAds.MaxNativeAdViewBinder;

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LaughTaleNativeAdapter extends Activity implements MaxAdRevenueListener {
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();  // 创建一个共享的调度线程池
    private MaxNativeAdLoader LaughTale_nativeAdLoader;
    private MaxNativeAdView   LaughTale_nativeAdView;
    private MaxAd LaughTale_nativeAd;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private int LaughTale_nativeRetryAttempt;
    public LaughTaleNativeListener LaughTale_nativeListener;
    private double LaughTale_adPrice = 0;
    private View adView;
    private LinearLayout LaughTale_root;
    public boolean LaughTale_isOpen = false;

    public void LaughTaleInitNativeAdapter(){
        LinearLayout linearLayout = new LinearLayout(LaughTale_activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.LaughTale_root = linearLayout;

        LayoutInflater from = LayoutInflater.from(LaughTale_activity);
        this.adView = from.inflate(R.layout.laughtale_collapsible_native_half_style, (ViewGroup) null);
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder( this.adView )
                .setTitleTextViewId( R.id.laughtale_inters_title )
                .setBodyTextViewId( R.id.laughtale_inters_text )
                .setIconImageViewId( R.id.laughtale_inters_icon_img )
                .setMediaContentViewGroupId( R.id.laughtale_media_content )
                .setCallToActionButtonId( R.id.laughtale_inters_click_btn )
                .build();
        LaughTale_nativeAdView = new MaxNativeAdView( binder, LaughTale_activity );
        LaughTale_nativeAdLoader = new MaxNativeAdLoader( LaughTale_ad_unit, LaughTale_activity );
        LaughTale_nativeAdLoader.setRevenueListener( this );
        LaughTale_nativeAdLoader.setNativeAdListener( new MaxNativeAdListener()
        {
            @Override
            public void onNativeAdLoaded(@Nullable final MaxNativeAdView nativeAdView, final MaxAd ad)
            {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========","NativeLoaded");
                // Cleanup any pre-existing native ad to prevent memory leaks.
                if ( LaughTale_nativeAd != null )
                {
                    LaughTale_nativeAdLoader.destroy( LaughTale_nativeAd );
                }
                LaughTale_nativeRetryAttempt = 0;
                // Save ad for cleanup.
                LaughTale_nativeAd = ad;
                LaughTale_adPrice = ad.getRevenue();
                if (LaughTale_adPrice == 0){
                    LaughTale_adPrice = 0.02;
                }
            }

            @Override
            public void onNativeAdLoadFailed(final String adUnitId, final MaxError error)
            {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========","LOADNativeFailed" + error.getMessage());
                LaughTale_nativeRetryAttempt++;
                long delay = (long) Math.pow(2, Math.min(6, LaughTale_nativeRetryAttempt));
                scheduler.schedule(LaughTaleNativeAdapter.this::LaughTaleLoadNativeAdView, delay, TimeUnit.SECONDS);
            }

            @Override
            public void onNativeAdClicked(final MaxAd ad)
            {
                if (null != LaughTale_nativeListener){
                    LaughTale_nativeListener.LaughTaleOnNativeAdClick();
                }
            }

            @Override
            public void onNativeAdExpired(final MaxAd ad)
            {

            }
        } );

        View findViewById = this.adView.findViewById(R.id.laughtale_close_btn);
        findViewById.setOnClickListener(new View.OnClickListener() { // from class: com.b2x.max.NativeIntersAd.2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LaughTaleHideNativeAd(view);
            }
        });

        View findViewById2 = this.adView.findViewById(R.id.laughtale_mod_close_btn);
        findViewById2.setOnClickListener(new View.OnClickListener() { // from class: com.b2x.max.NativeIntersAd.2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                findViewById2.setVisibility(View.GONE);
                ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
                if (parentViewGroup != null){
                    parentViewGroup.removeView(LaughTale_root);
                }
                if (null != LaughTale_nativeListener){
                    LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
                }
            }
        });
    }

    private void startExpandAnimation() {
        RelativeLayout closeArea = this.adView.findViewById(R.id.close_area);  // 获取 close_area
        LaughTale_activity.runOnUiThread(() -> {
            closeArea.postDelayed(() -> {
                closeArea.setVisibility(View.VISIBLE);
            }, 1000L);
        });


    }

    private void startCollapseAnimation() {
        RelativeLayout closeArea = this.adView.findViewById(R.id.close_area);  // 获取 close_area
        closeArea.setVisibility(View.GONE);  // 动画结束后隐藏 close_area
        ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
        if (parentViewGroup != null){
            parentViewGroup.removeView(LaughTale_root);
        }
        if (null != LaughTale_nativeListener){
            LaughTale_nativeListener.LaughTaleOnNativeAdClosed();
        }
    }

    // 持续的放大和缩小动画
    private void startScaleAnimation() {
        final View adContentView = this.adView.findViewById(R.id.laughtale_inters_click_btn);  // 获取广告的内容视图（或根据需求选择不同的视图）

        // 创建一个 ValueAnimator 让视图的 scaleX 和 scaleY 在 0.9 到 1.1 之间来回变化
        ValueAnimator scaleAnimator = ValueAnimator.ofFloat(1.0f, 1.1f, 1.0f); // 放大到 1.1，再缩小回 1.0
        scaleAnimator.setDuration(1000);  // 每个循环的动画时长为 1 秒
        scaleAnimator.setRepeatMode(ValueAnimator.RESTART);  // 设置重复模式为 RESTART，表示每次从头开始
        scaleAnimator.setRepeatCount(ValueAnimator.INFINITE);  // 无限循环

        // 设置动画更新监听器
        scaleAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float animatedValue = (float) animation.getAnimatedValue();
                // 动态设置视图的缩放比例
                adContentView.setScaleX(animatedValue);
                adContentView.setScaleY(animatedValue);
            }
        });

        // 启动动画
        scaleAnimator.start();
    }


    public void LaughTaleLoadNativeAdView(){
        LaughTale_nativeAdLoader.loadAd( LaughTale_nativeAdView );
    }

    public double LaughTaleGetADPrice(){
        return LaughTale_adPrice;
    }

    public void LaughTaleShowNativeAd(boolean isModMode){
        LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                LaughTale_isOpen = true;
                LaughTale_root.setVisibility(View.VISIBLE);
                LaughTale_adPrice = 0;

                ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
                if (parentViewGroup != null){
                    parentViewGroup.removeView(LaughTale_root);
                }

                // 设置父布局的LayoutParams
                FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);

                // 设置子视图的布局
                FrameLayout.LayoutParams layoutParams1 = new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
                layoutParams1.gravity = Gravity.BOTTOM;  // 只需要设置底部对齐

                LaughTale_activity.addContentView(LaughTale_root,layoutParams);
                LaughTale_root.removeAllViews();
                LaughTale_root.setGravity(Gravity.BOTTOM);
                LaughTale_root.bringToFront();
                LaughTale_root.addView(LaughTale_nativeAdView,layoutParams1);

                if (null != LaughTale_nativeListener){
                    LaughTale_nativeListener.LaughTaleOnNativeAdDisplayed();
                }
                if (isModMode == false){
                    startExpandAnimation();
                    startScaleAnimation();
                }else {
                    View closeBtn = adView.findViewById(R.id.laughtale_mod_close_btn);
                    closeBtn.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void LaughTaleHideNativeAd(View view){
        LaughTaleHideView();
    }

    public void LaughTaleAutoHideNativeAd(){
        LaughTaleHideView();
    }

    private void LaughTaleHideView(){
        LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                LaughTale_isOpen = false;
                startCollapseAnimation();
            }
        });
        LaughTaleLoadNativeAdView();
    }


    @Override
    public void onAdRevenuePaid(MaxAd maxAd) {
        double revenue = maxAd.getRevenue(); // In USD
        String networkName = maxAd.getNetworkName(); // Display name of the network that showed the ad (e.g. "AdColony")
        String adUnitId = maxAd.getAdUnitId(); // The MAX Ad Unit ID
        MaxAdFormat adFormat = maxAd.getFormat(); // The ad format of the ad (e.g. BANNER, MREC, INTERSTITIAL, REWARDED)
        LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue,adFormat.toString(),networkName,adUnitId);
        //上报Bank 收入数据
//        LaughTaleBankManager.instance().updateAdStats("native",revenue*1000);
    }

}
