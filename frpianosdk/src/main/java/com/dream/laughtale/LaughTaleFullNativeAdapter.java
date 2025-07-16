package com.dream.laughtale;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
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

import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class LaughTaleFullNativeAdapter extends Activity implements MaxAdRevenueListener {
    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();  // 创建一个共享的调度线程池
    private MaxNativeAdLoader LaughTale_nativeAdLoader;
    private MaxNativeAdView LaughTale_nativeAdView;
    private MaxAd LaughTale_nativeAd;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private int LaughTale_nativeRetryAttempt;
    public LaughTaleFullNativeListener LaughTale_fullNativeListener;
    private double LaughTale_adPrice = 0;

    private CloseStyle closeStyle;
    private View controlBtn;
    private int delayTime;
    private TextView delayView;

    private View adView;
    private LinearLayout LaughTale_root;
    public boolean LaughTale_isOpen = false;

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

    public void LaughTaleInitNativeAdapter(){
        LinearLayout linearLayout = new LinearLayout(LaughTale_activity);
        linearLayout.setOrientation(LinearLayout.VERTICAL);
        this.LaughTale_root = linearLayout;

        LayoutInflater from = LayoutInflater.from(LaughTale_activity);
        this.adView = from.inflate(R.layout.laughtale_native_full_style, (ViewGroup) null);
        InitViewStyle();
        MaxNativeAdViewBinder binder = new MaxNativeAdViewBinder.Builder( this.adView )
                .setTitleTextViewId( R.id.native_ad_title )
                .setBodyTextViewId( R.id.native_ad_body )
                .setIconImageViewId( R.id.native_ad_icon )
                .setMediaContentViewGroupId( R.id.native_ad_media )
                .setStarRatingContentViewGroupId(R.id.native_ad_rating)
                .setCallToActionButtonId( R.id.native_ad_action )
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
                scheduler.schedule(LaughTaleFullNativeAdapter.this::LaughTaleLoadNativeAdView, delay, TimeUnit.SECONDS);
            }

            @Override
            public void onNativeAdClicked(final MaxAd ad)
            {
                if (null != LaughTale_fullNativeListener){
                    LaughTale_fullNativeListener.LaughTaleOnFullNativeAdClick();
                }
            }

            @Override
            public void onNativeAdExpired(final MaxAd ad)
            {

            }
        } );
    }


    public void LaughTaleLoadNativeAdView(){
        LaughTale_nativeAdLoader.loadAd( LaughTale_nativeAdView );
    }

    public double LaughTaleGetADPrice(){
        return LaughTale_adPrice;
    }

    public void showDelayTime() {
        new Handler().postDelayed(new Runnable() { // from class: com.b2x.max.NativeIntersAd.3
            @Override // java.lang.Runnable
            public void run() {
                if (delayTime <= 0) {
                    delayView.setVisibility(View.GONE);
                    controlBtn.setVisibility(View.VISIBLE);
                    Animation fadeInAnimation = AnimationUtils.loadAnimation(LaughTale_activity, R.anim.fade_in);
                    controlBtn.startAnimation(fadeInAnimation);
                    return;
                }
                delayTime = delayTime - 1;
                delayView.setText(delayTime + "s");
                showDelayTime();
            }
        }, 1000L);
    }

    private void setNativeClose() {
        View findViewById = this.adView.findViewById(R.id.native_ad_close);
        findViewById.setVisibility(View.GONE);
        this.controlBtn = findViewById;
        this.controlBtn.setOnClickListener(new View.OnClickListener() { // from class: com.b2x.max.NativeIntersAd.2
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                LaughTaleHideNativeAd(view);
            }
        });
        TextView textView = (TextView) this.adView.findViewById(R.id.native_dis_time);
        this.delayView = textView;
        this.delayView.setVisibility(View.VISIBLE);
        textView.setText(this.closeStyle.delayTime + "s");
    }

    public void LaughTaleShowNativeAd(){
        LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                LaughTale_isOpen = true;
                LaughTale_root.setVisibility(View.VISIBLE);
                LaughTale_adPrice = 0;
                delayTime = closeStyle.delayTime;
                showDelayTime();

                ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
                if (parentViewGroup != null){
                    parentViewGroup.removeView(LaughTale_root);
                }

                LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(-1, -1);
                layoutParams.setMargins(0,0,0,0);
                LinearLayout.LayoutParams layoutParams1 = new LinearLayout.LayoutParams(-1, -1);

                LaughTale_activity.addContentView(LaughTale_root,layoutParams);
                LaughTale_root.removeAllViews();
                LaughTale_root.setGravity(Gravity.CENTER);
                LaughTale_root.bringToFront();
                LaughTale_root.addView(LaughTale_nativeAdView,layoutParams1);

                if (null != LaughTale_fullNativeListener){
                    LaughTale_fullNativeListener.LaughTaleOnFullNativeAdDisplayed();
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
                ViewGroup parentViewGroup = (ViewGroup) LaughTale_root.getParent();
                if (parentViewGroup != null){
                    parentViewGroup.removeView(LaughTale_root);
                }
                if (null != LaughTale_fullNativeListener){
                    LaughTale_fullNativeListener.LaughTaleOnFullNativeAdClosed();
                }
                InitViewStyle();
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
    }
}
