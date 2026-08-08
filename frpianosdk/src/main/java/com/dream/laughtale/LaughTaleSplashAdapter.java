package com.dream.laughtale;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdPreloader;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.AdValue;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration;
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo;

import java.util.concurrent.ScheduledFuture;

/** 标准 App Open（GMA Next-Gen Preloader），供冷/热启动开屏使用。 */
public class LaughTaleSplashAdapter {
    private int LaughTale_splashRetryAttempt;
    public String LaughTale_ad_unit;
    public Activity LaughTale_activity;
    public LaughTaleSplashListener LaughTale_splashListener;
    private double LaughTale_adPrice = 0;
    private boolean LaughTale_isShowing = false;
    private boolean LaughTale_isLoading = false;
    private ScheduledFuture<?> LaughTale_retryFuture;
    private final Handler LaughTale_mainHandler = new Handler(Looper.getMainLooper());

    /** GMA Next-Gen 全屏回调常在后台线程，统一切回主线程再通知上层。 */
    private void LaughTalePostToMain(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
            return;
        }
        if (LaughTale_activity != null) {
            LaughTale_activity.runOnUiThread(runnable);
        } else {
            LaughTale_mainHandler.post(runnable);
        }
    }

    private final PreloadCallback LaughTale_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADSPLASHLoaded");
            LaughTale_isLoading = false;
            LaughTale_adPrice = 0.01;
            LaughTale_splashRetryAttempt = 0;
            LaughTaleCancelRetry();
            LaughTalePostToMain(() -> {
                if (LaughTale_splashListener != null) {
                    LaughTale_splashListener.LaughTaleOnSplashAdLoaded();
                }
            });
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADSPLASHFailed");
            LaughTale_isLoading = false;
            LaughTale_adPrice = 0;
            LaughTale_splashRetryAttempt++;
            long delay = (long) Math.pow(2, Math.min(6, LaughTale_splashRetryAttempt));
            LaughTaleCancelRetry();
            LaughTale_retryFuture = LaughTaleAdRetryScheduler.scheduleSeconds(
                    LaughTaleSplashAdapter.this::LaughTaleLoadSplashAd, delay);
        }
    };

    public boolean LaughTaleIsShowing() {
        return LaughTale_isShowing;
    }

    public boolean LaughTaleIsLoading() {
        return LaughTale_isLoading;
    }

    public void LaughTaleInitSplashAdapter() {
        // 预加载在 MobileAds.initialize 完成后由 Load 触发
    }

    public void LaughTaleLoadSplashAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            return;
        }
        String unitId = LaughTaleUnitId();
        if (unitId == null) {
            return;
        }
        if (LaughTale_isLoading || LaughTale_isShowing || AppOpenAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        LaughTaleCancelRetry();
        LaughTale_isLoading = true;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=========", "LOADSPLASH");
        if (!LaughTaleStartSplashPreloading(unitId)) {
            LaughTale_isLoading = false;
        }
    }

    private boolean LaughTaleStartSplashPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest);
        return AppOpenAdPreloader.start(unitId, preloadConfig, LaughTale_preloadCallback);
    }

    public double LaughTaleGetADPrice() {
        if (!LaughTaleIsReady()) {
            return 0;
        }
        return LaughTale_adPrice > 0 ? LaughTale_adPrice : 0.01;
    }

    public boolean LaughTaleIsReady() {
        String unitId = LaughTaleUnitId();
        return unitId != null && AppOpenAdPreloader.isAdAvailable(unitId);
    }

    public void LaughTaleShowSplashAd() {
        if (LaughTale_activity == null || LaughTale_activity.isFinishing() || LaughTale_activity.isDestroyed()) {
            if (LaughTale_splashListener != null) {
                LaughTale_splashListener.LaughTaleOnSplashAdFailedToShow();
            }
            return;
        }
        final String unitId = LaughTaleUnitId();
        if (unitId == null) {
            if (LaughTale_splashListener != null) {
                LaughTale_splashListener.LaughTaleOnSplashAdFailedToShow();
            }
            return;
        }
        if (!AppOpenAdPreloader.isAdAvailable(unitId)) {
            LaughTaleLoadSplashAd();
            if (LaughTale_splashListener != null) {
                LaughTale_splashListener.LaughTaleOnSplashAdFailedToShow();
            }
            return;
        }
        LaughTale_activity.runOnUiThread(() -> {
            final AppOpenAd appOpenAd = AppOpenAdPreloader.pollAd(unitId);
            if (appOpenAd == null) {
                LaughTaleLoadSplashAd();
                if (LaughTale_splashListener != null) {
                    LaughTale_splashListener.LaughTaleOnSplashAdFailedToShow();
                }
                return;
            }
            LaughTale_adPrice = 0;
            appOpenAd.setAdEventCallback(new AppOpenAdEventCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    LaughTale_isShowing = true;
                    LaughTalePostToMain(() -> {
                        if (LaughTale_splashListener != null) {
                            LaughTale_splashListener.LaughTaleOnSplashAdDisplayed();
                        }
                    });
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    LaughTale_isShowing = false;
                    appOpenAd.destroy();
                    LaughTalePostToMain(() -> {
                        if (LaughTale_splashListener != null) {
                            LaughTale_splashListener.LaughTaleOnSplashAdClosed();
                        }
                        LaughTaleLoadSplashAd();
                    });
                }

                @Override
                public void onAdFailedToShowFullScreenContent(
                        @NonNull FullScreenContentError fullScreenContentError) {
                    LaughTale_isShowing = false;
                    appOpenAd.destroy();
                    LaughTalePostToMain(() -> {
                        if (LaughTale_splashListener != null) {
                            LaughTale_splashListener.LaughTaleOnSplashAdFailedToShow();
                        }
                        LaughTaleLoadSplashAd();
                    });
                }

                @Override
                public void onAdClicked() {
                }

                @Override
                public void onAdPaid(@NonNull AdValue adValue) {
                    // 开屏不报 Firebase revenue（对齐原逻辑）
                }
            });
            appOpenAd.show(LaughTale_activity);
        });
    }

    public void LaughTaleOnDestroy() {
        LaughTaleCancelRetry();
        LaughTale_isLoading = false;
        LaughTale_isShowing = false;
        LaughTale_adPrice = 0;
        String unitId = LaughTaleUnitId();
        if (unitId != null) {
            AppOpenAdPreloader.destroy(unitId);
        }
        LaughTale_activity = null;
        LaughTale_splashListener = null;
    }

    private void LaughTaleCancelRetry() {
        LaughTaleAdRetryScheduler.cancel(LaughTale_retryFuture);
        LaughTale_retryFuture = null;
    }

    private String LaughTaleUnitId() {
        if (LaughTale_ad_unit == null) {
            return null;
        }
        String unitId = LaughTale_ad_unit.trim();
        return unitId.isEmpty() ? null : unitId;
    }
}
