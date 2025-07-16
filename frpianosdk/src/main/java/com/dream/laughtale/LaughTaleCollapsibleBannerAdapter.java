package com.dream.laughtale;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;

import com.google.ads.mediation.admob.AdMobAdapter;
import com.google.android.gms.ads.AdListener;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.AdValue;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.OnPaidEventListener;

public class LaughTaleCollapsibleBannerAdapter extends Activity {
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    private AdView LaughTale_adView;
    public Boolean LaughTale_IsAdReady = false;
    private RelativeLayout adContainerView;
    private Boolean LaughTale_IsLoading = false;
    private boolean LaughTale_isCOBN = true;
    public Boolean LaughTale_IsShow = false;

    public void LaughTaleInitBannerAdapter() {
        adContainerView = new RelativeLayout(LaughTale_activity);
        int width = ViewGroup.LayoutParams.MATCH_PARENT;
        // Get the adaptive banner height.
        int heightDp = 50;
        int heightPx = dip2px( LaughTale_activity, heightDp );
        FrameLayout.LayoutParams frameLayout = new FrameLayout.LayoutParams(width, heightPx);
        frameLayout.gravity = Gravity.BOTTOM;
        adContainerView.setLayoutParams(frameLayout);
        adContainerView.setVisibility(View.GONE);
        ViewGroup rootView = (ViewGroup)this.LaughTale_activity.findViewById(android.R.id.content);
        rootView.addView(adContainerView);

        LaughTale_adView = new AdView(LaughTale_activity);
        LaughTale_adView.setAdSize(getAdSize(LaughTale_activity));
        LaughTale_adView.setAdUnitId(LaughTale_ad_unit);
        LaughTale_adView.setOnPaidEventListener(new OnPaidEventListener() {
            @Override
            public void onPaidEvent(AdValue adValue) {
                double revenue = adValue.getValueMicros() / 1000000.0 ; // In USD
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseRevenue(revenue,"BANNER","Admob",LaughTale_ad_unit);
            }
        });
        LaughTale_adView.setAdListener(new AdListener() {
            @Override
            public void onAdClicked() {
                // Code to be executed when the user clicks on an ad.
            }

            @Override
            public void onAdClosed() {
                // Code to be executed when the user is about to return
                // to the app after tapping on an ad.
            }

            @Override
            public void onAdFailedToLoad(LoadAdError adError) {
                // Code to be executed when an ad request fails.
                LaughTale_IsLoading = false;
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "CollapsideLoadFailed"+adError.getMessage());
            }

            @Override
            public void onAdImpression() {
                // Code to be executed when an impression is recorded
                // for an ad.
                if (LaughTale_isCOBN == false){
                    LaughTaleLoadBannerView();
                }
            }

            @Override
            public void onAdLoaded() {
                LaughTale_IsLoading = false;
                // Code to be executed when an ad finishes loading.
                if (LaughTale_adView.isCollapsible()){
                    LaughTale_isCOBN = true;
                    LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("COBN_IS_TRUE",null);
                }else {
                    LaughTale_isCOBN = false;
                    LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("COBN_IS_FALSE",null);
                }
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "CollapsideLoaded");
                LaughTale_IsAdReady = true;
            }

            @Override
            public void onAdOpened() {
                // Code to be executed when an ad opens an overlay that
                // covers the screen.
            }
        });
    }

    public void LaughTaleLoadBannerView() {
        if (LaughTale_IsLoading == false){
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "CollapsideLoadBannerView");
            LaughTale_IsLoading = true;
            Bundle extras = new Bundle();
            extras.putString("collapsible", "bottom");
            AdRequest adRequest = new AdRequest.Builder()
                    .addNetworkExtrasBundle(AdMobAdapter.class, extras)
                    .build();
            LaughTale_adView.loadAd(adRequest);
        }
    }

    public void LaughTaleShowBannerView() {
        LaughTale_IsShow = true;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "CollapsideShowBannerView");
        this.LaughTale_activity.runOnUiThread(new Runnable() {
            public void run() {
                if (null != LaughTaleCollapsibleBannerAdapter.this.adContainerView) {
                    ViewGroup parentViewGroup = (ViewGroup) LaughTale_adView.getParent();
                    if (parentViewGroup != null){
                        parentViewGroup.removeView(LaughTale_adView);
                    }
                    adContainerView.setVisibility(View.VISIBLE);
                    adContainerView.addView(LaughTale_adView);
                    LaughTale_IsAdReady = false;
                }
            }
        });
    }


    public void LaughTaleHideBannerView() {
        if (null != LaughTaleCollapsibleBannerAdapter.this.adContainerView){
            LaughTale_IsShow = false;
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "CollapsideHideBannerView");
            this.LaughTale_activity.runOnUiThread(new Runnable() {
                public void run() {
                    if (null != LaughTaleCollapsibleBannerAdapter.this.LaughTale_adView){
                        LaughTale_adView.destroy();
                    }
                    adContainerView.removeAllViews();
                    adContainerView.setVisibility(View.GONE);
                }
            });
            LaughTaleLoadBannerView();
        }
    }

    private AdSize getAdSize(Activity activity) {
        // Determine the screen width (less decorations) to use for the ad width.
        Display display = activity.getWindowManager().getDefaultDisplay();
        DisplayMetrics outMetrics = new DisplayMetrics();
        display.getMetrics(outMetrics);

        float density = outMetrics.density;

        float adWidthPixels = adContainerView.getWidth();

        // If the ad hasn't been laid out, default to the full screen width.
        if (adWidthPixels == 0) {
            adWidthPixels = outMetrics.widthPixels;
        }

        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
    }

    public static int dip2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

}
