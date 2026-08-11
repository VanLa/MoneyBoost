package com.ad4game.admobadapter;

import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.VersionInfo;
import com.google.android.gms.ads.mediation.Adapter;
import com.google.android.gms.ads.mediation.InitializationCompleteCallback;
import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationBannerAd;
import com.google.android.gms.ads.mediation.MediationBannerAdCallback;
import com.google.android.gms.ads.mediation.MediationBannerAdConfiguration;
import com.google.android.gms.ads.mediation.MediationConfiguration;
import com.google.android.gms.ads.mediation.MediationInterstitialAd;
import com.google.android.gms.ads.mediation.MediationInterstitialAdCallback;
import com.google.android.gms.ads.mediation.MediationInterstitialAdConfiguration;
import com.google.android.gms.ads.mediation.MediationNativeAdCallback;
import com.google.android.gms.ads.mediation.MediationNativeAdConfiguration;
import com.google.android.gms.ads.mediation.MediationRewardedAd;
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration;
import com.google.android.gms.ads.mediation.UnifiedNativeAdMapper;

import java.util.List;

public final class AdmobCustomEventManager extends Adapter {
    private static final VersionInfo ADAPTER_VERSION = new VersionInfo(2, 0, 0);
    private static final VersionInfo SDK_VERSION = new VersionInfo(1, 2, 1);

    @Override
    public void loadInterstitialAd(
            @NonNull MediationInterstitialAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<MediationInterstitialAd, MediationInterstitialAdCallback> callback) {
        new AdFullInterstitialCustomEventLoader(configuration, callback).loadAd();
    }

    @Override
    public void loadRewardedAd(
            @NonNull MediationRewardedAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> callback) {
        new AdFullRewardedCustomEventLoader(configuration, callback).loadAd();
    }

    @Override
    public void loadBannerAd(
            @NonNull MediationBannerAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<MediationBannerAd, MediationBannerAdCallback> callback) {
        new AdFullBannerCustomEventLoader(configuration, callback).loadAd();
    }

    @Override
    public void loadNativeAd(
            @NonNull MediationNativeAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<UnifiedNativeAdMapper, MediationNativeAdCallback> callback) {
        new AdFullNativeCustomEventLoader(configuration, callback).loadAd();
    }

    @NonNull
    @Override
    public VersionInfo getSDKVersionInfo() {
        return SDK_VERSION;
    }

    @NonNull
    @Override
    public VersionInfo getVersionInfo() {
        return ADAPTER_VERSION;
    }

    @Override
    public void initialize(
            @NonNull Context context,
            @NonNull InitializationCompleteCallback callback,
            @NonNull List<MediationConfiguration> configurations) {
        // The host app owns GMA Next-Gen SDK initialization.
        callback.onInitializationSucceeded();
    }
}
