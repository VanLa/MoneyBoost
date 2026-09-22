package com.a4g.googlesdkadapter;

import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.AdSize;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationBannerAd;
import com.google.android.gms.ads.mediation.MediationBannerAdCallback;
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

final class AdFullBannerCustomEventLoader implements MediationBannerAd {
    private static final String FORMAT = "banner";

    private final MediationBannerAdConfiguration configuration;
    private final MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback> loadCallback;
    private AdView adView;
    private BannerAd bannerAd;
    private MediationBannerAdCallback adCallback;

    AdFullBannerCustomEventLoader(
            @NonNull MediationBannerAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback> callback) {
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

        AdSize requestedSize = configuration.getAdSize();
        com.google.android.libraries.ads.mobile.sdk.banner.AdSize nextGenSize =
                new com.google.android.libraries.ads.mobile.sdk.banner.AdSize(
                        requestedSize.getWidth(), requestedSize.getHeight());
        adView = new AdView(configuration.getContext());
        adView.resize(nextGenSize);
        BannerAdRequest request = new BannerAdRequest.Builder(adUnitId, nextGenSize).build();
        adView.loadAd(request, new AdLoadCallback<BannerAd>() {
            @Override
            public void onAdLoaded(@NonNull BannerAd ad) {
                AdFullAdapterUtils.endRequest(FORMAT, adUnitId);
                bannerAd = ad;
                ad.setAdEventCallback(new BannerAdEventCallback() {
                    @Override
                    public void onAdClicked() {
                        if (adCallback != null) adCallback.reportAdClicked();
                    }

                    @Override
                    public void onAdImpression() {
                        if (adCallback != null) adCallback.reportAdImpression();
                    }

                    @Override
                    public void onAdShowedFullScreenContent() {
                        if (adCallback != null) adCallback.onAdOpened();
                    }

                    @Override
                    public void onAdDismissedFullScreenContent() {
                        if (adCallback != null) adCallback.onAdClosed();
                    }
                });
                adCallback = loadCallback.onSuccess(AdFullBannerCustomEventLoader.this);
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                AdFullAdapterUtils.endRequest(FORMAT, adUnitId);
                loadCallback.onFailure(AdFullAdapterUtils.fromLoadError(error));
            }
        });
    }

    @NonNull
    @Override
    public View getView() {
        return adView;
    }
}
