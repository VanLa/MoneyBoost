package com.a4g.googlesdkadapter;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationNativeAdCallback;
import com.google.android.gms.ads.mediation.MediationNativeAdConfiguration;
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest;

import java.util.Collections;

final class AdFullNativeCustomEventLoader {
    private static final String FORMAT = "native";

    private final MediationNativeAdConfiguration configuration;
    private final MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> loadCallback;
    private MediationNativeAdCallback adCallback;

    AdFullNativeCustomEventLoader(
            @NonNull MediationNativeAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> callback) {
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

        NativeAdRequest request = new NativeAdRequest.Builder(adUnitId,
                Collections.singletonList(NativeAd.NativeAdType.NATIVE)).build();
        NativeAdLoader.load(request, new NativeAdLoaderCallback() {
            @Override
            public void onNativeAdLoaded(@NonNull NativeAd ad) {
                AdFullAdapterUtils.endRequest(FORMAT, adUnitId);
                ad.setAdEventCallback(new NativeAdEventCallback() {
                    @Override
                    public void onAdClicked() {
                        if (adCallback != null) adCallback.reportAdClicked();
                    }

                    @Override
                    public void onAdImpression() {
                        if (adCallback != null) adCallback.reportAdImpression();
                    }
                });
                MediaView mediaView = new MediaView(configuration.getContext());
                adCallback = loadCallback.onSuccess(new AdFullUnifiedNativeAdMapper(ad, mediaView));
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError error) {
                AdFullAdapterUtils.endRequest(FORMAT, adUnitId);
                loadCallback.onFailure(AdFullAdapterUtils.fromLoadError(error));
            }
        });
    }
}
