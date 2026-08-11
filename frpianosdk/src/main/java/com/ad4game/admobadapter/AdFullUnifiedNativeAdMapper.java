package com.ad4game.admobadapter;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper;
import com.google.android.libraries.ads.mobile.sdk.common.AdChoicesInfo;
import com.google.android.libraries.ads.mobile.sdk.common.Image;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaContent;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd;

import java.util.Collections;
import java.util.List;

/** Maps a Next-Gen native ad into the native custom-event contract used by AdMob. */
final class AdFullUnifiedNativeAdMapper extends UnifiedNativeAdMapper {
    private NativeAd nativeAd;

    AdFullUnifiedNativeAdMapper(@NonNull NativeAd ad, @NonNull MediaView mediaView) {
        nativeAd = ad;
        setHeadline(ad.getHeadline());
        setBody(ad.getBody());
        setCallToAction(ad.getCallToAction());
        setAdvertiser(ad.getAdvertiser());
        setStarRating(ad.getStarRating());
        setStore(ad.getStore());
        setPrice(ad.getPrice());
        setExtras(ad.getExtras());
        setMediatedAd(ad);

        if (ad.getIcon() != null) {
            setIcon(new AdFullNativeMappedImage(ad.getIcon()));
        }
        if (ad.getImage() != null) {
            setImages(Collections.singletonList(new AdFullNativeMappedImage(ad.getImage())));
        }
        if (ad.getAdChoicesInfo() != null) {
            setAttributionInfo(new MappedAdChoicesInfo(ad.getAdChoicesInfo()));
        }

        MediaContent mediaContent = ad.getMediaContent();
        if (mediaContent != null) {
            mediaView.setMediaContent(mediaContent);
            setMediaView(mediaView);
            setMediaContentAspectRatio(mediaContent.getAspectRatio());
            setHasVideoContent(mediaContent.getHasVideoContent());
        }

        setOverrideClickHandling(true);
        setOverrideImpressionRecording(true);
    }

    @Override
    public void recordImpression() {
        if (nativeAd != null) nativeAd.recordImpression(new Bundle());
    }

    @Override
    public void handleClick(@NonNull View view) {
        if (nativeAd != null) nativeAd.performClick(new Bundle());
    }

    @Override
    public void destroy() {
        if (nativeAd != null) {
            nativeAd.destroy();
            nativeAd = null;
        }
    }

    private static final class MappedAdChoicesInfo
            extends com.google.android.gms.ads.formats.NativeAd.AdChoicesInfo {
        private final AdChoicesInfo source;

        MappedAdChoicesInfo(@NonNull AdChoicesInfo source) {
            this.source = source;
        }

        @NonNull
        @Override
        public CharSequence getText() {
            return source.getText();
        }

        @NonNull
        @Override
        public List<com.google.android.gms.ads.formats.NativeAd.Image> getImages() {
            java.util.ArrayList<com.google.android.gms.ads.formats.NativeAd.Image> result =
                    new java.util.ArrayList<>();
            for (Image image : source.getImages()) {
                result.add(new AdFullNativeMappedImage(image));
            }
            return result;
        }
    }
}
