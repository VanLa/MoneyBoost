package com.ad4game.admobadapter;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationInterstitialAd;
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback;
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;

final class AdFullInterstitialCustomEventLoader implements MediationInterstitialAd {
    private static final String FORMAT = "interstitial";

    private final MediationInterstitialAdConfiguration configuration;
    private final MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback> loadCallback;
    private InterstitialAd interstitialAd;
    private MediationInterstitialAdCallback adCallback;

    AdFullInterstitialCustomEventLoader(
            @NonNull MediationInterstitialAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback> callback) {
        this.configuration = configuration;
        this.loadCallback = callback;
    }

    void loadAd() {
        String adUnitId = AdFullAdapterUtils.getAdUnitId(configuration);
        if (adUnitId == null) {
            loadCallback.onFailure(AdFullAdapterUtils.invalidParameterError());
            return;
        }
        if (!AdFullAdapterUtils.beginRequest(FORMAT, adUnitId)) {
            loadCallback.onFailure(AdFullAdapterUtils.recursiveRequestError(adUnitId));
            return;
        }
        InterstitialAd.load(new AdRequest.Builder(adUnitId).build(),
                new AdLoadCallback<InterstitialAd>() {
                    @Override
                    public void onAdLoaded(@NonNull InterstitialAd ad) {
                        AdFullAdapterUtils.endRequest(FORMAT, adUnitId);
                        interstitialAd = ad;
                        ad.setAdEventCallback(new InterstitialAdEventCallback() {
                            @Override
                            public void onAdShowedFullScreenContent() {
                                if (adCallback != null) adCallback.onAdOpened();
                            }

                            @Override
                            public void onAdImpression() {
                                if (adCallback != null) adCallback.reportAdImpression();
                            }

                            @Override
                            public void onAdClicked() {
                                if (adCallback != null) adCallback.reportAdClicked();
                            }

                            @Override
                            public void onAdDismissedFullScreenContent() {
                                if (adCallback != null) adCallback.onAdClosed();
                                destroyAd();
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(
                                    @NonNull FullScreenContentError error) {
                                if (adCallback != null) {
                                    adCallback.onAdFailedToShow(AdFullAdapterUtils.fromShowError(error));
                                }
                                destroyAd();
                            }
                        });
                        adCallback = loadCallback.onSuccess(AdFullInterstitialCustomEventLoader.this);
                    }

                    @Override
                    public void onAdFailedToLoad(@NonNull LoadAdError error) {
                        AdFullAdapterUtils.endRequest(FORMAT, adUnitId);
                        loadCallback.onFailure(AdFullAdapterUtils.fromLoadError(error));
                    }
                });
    }

    @Override
    public void showAd(@NonNull Context context) {
        Activity activity = AdFullAdapterUtils.findActivity(context);
        if (activity == null) {
            if (adCallback != null) adCallback.onAdFailedToShow(AdFullAdapterUtils.noActivityError());
            return;
        }
        if (interstitialAd == null) {
            if (adCallback != null) adCallback.onAdFailedToShow(AdFullAdapterUtils.notReadyError());
            return;
        }
        interstitialAd.show(activity);
    }

    private void destroyAd() {
        if (interstitialAd != null) {
            interstitialAd.destroy();
            interstitialAd = null;
        }
    }
}
