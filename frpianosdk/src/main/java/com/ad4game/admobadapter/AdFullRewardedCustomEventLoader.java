package com.ad4game.admobadapter;

import android.app.Activity;
import android.content.Context;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.mediation.MediationAdLoadCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAd;
import com.google.android.gms.ads.mediation.MediationRewardedAdCallback;
import com.google.android.gms.ads.mediation.MediationRewardedAdConfiguration;
import com.google.android.gms.ads.rewarded.RewardItem;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;

final class AdFullRewardedCustomEventLoader implements MediationRewardedAd {
    private static final String FORMAT = "rewarded";

    private final MediationRewardedAdConfiguration configuration;
    private final MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> loadCallback;
    private RewardedAd rewardedAd;
    private MediationRewardedAdCallback adCallback;

    AdFullRewardedCustomEventLoader(
            @NonNull MediationRewardedAdConfiguration configuration,
            @NonNull MediationAdLoadCallback<MediationRewardedAd, MediationRewardedAdCallback> callback) {
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
        RewardedAd.load(new AdRequest.Builder(adUnitId).build(), new AdLoadCallback<RewardedAd>() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd ad) {
                AdFullAdapterUtils.endRequest(FORMAT, adUnitId);
                rewardedAd = ad;
                ad.setAdEventCallback(new RewardedAdEventCallback() {
                    @Override
                    public void onAdShowedFullScreenContent() {
                        if (adCallback != null) {
                            adCallback.onAdOpened();
                            adCallback.onVideoStart();
                        }
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
                        if (adCallback != null) {
                            adCallback.onVideoComplete();
                            adCallback.onAdClosed();
                        }
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
                adCallback = loadCallback.onSuccess(AdFullRewardedCustomEventLoader.this);
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
        if (rewardedAd == null) {
            if (adCallback != null) adCallback.onAdFailedToShow(AdFullAdapterUtils.notReadyError());
            return;
        }
        rewardedAd.show(activity, nextGenReward -> {
            if (adCallback == null) return;
            adCallback.onUserEarnedReward(new RewardItem() {
                @Override public String getType() { return nextGenReward.getType(); }
                @Override public int getAmount() { return nextGenReward.getAmount(); }
            });
        });
    }

    private void destroyAd() {
        if (rewardedAd != null) {
            rewardedAd.destroy();
            rewardedAd = null;
        }
    }
}
