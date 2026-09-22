package com.dream.moneyboost;

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
public class MoneyBoostSplashAdapter {
    private int MoneyBoost_splashRetryAttempt;
    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    public MoneyBoostSplashListener MoneyBoost_splashListener;
    private boolean MoneyBoost_adReady = false;
    private boolean MoneyBoost_isShowing = false;
    private boolean MoneyBoost_isLoading = false;
    private ScheduledFuture<?> MoneyBoost_retryFuture;
    private final Handler MoneyBoost_mainHandler = new Handler(Looper.getMainLooper());

    /** GMA Next-Gen 全屏回调常在后台线程，统一切回主线程再通知上层。 */
    private void MoneyBoostPostToMain(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
            return;
        }
        if (MoneyBoost_activity != null) {
            MoneyBoost_activity.runOnUiThread(runnable);
        } else {
            MoneyBoost_mainHandler.post(runnable);
        }
    }

    private final PreloadCallback MoneyBoost_preloadCallback = new PreloadCallback() {
        @Override
        public void onAdPreloaded(@NonNull String preloadId, @NonNull ResponseInfo responseInfo) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADSPLASHLoaded");
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = true;
            MoneyBoost_splashRetryAttempt = 0;
            MoneyBoostCancelRetry();
            MoneyBoostPostToMain(() -> {
                if (MoneyBoost_splashListener != null) {
                    MoneyBoost_splashListener.MoneyBoostOnSplashAdLoaded();
                }
            });
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADSPLASHFailed");
            MoneyBoost_isLoading = false;
            MoneyBoost_adReady = false;
            MoneyBoost_splashRetryAttempt++;
            long delay = (long) Math.pow(2, Math.min(6, MoneyBoost_splashRetryAttempt));
            MoneyBoostCancelRetry();
            MoneyBoost_retryFuture = MoneyBoostAdRetryScheduler.scheduleSeconds(
                    MoneyBoostSplashAdapter.this::MoneyBoostLoadSplashAd, delay);
        }
    };

    public boolean MoneyBoostIsShowing() {
        return MoneyBoost_isShowing;
    }

    public boolean MoneyBoostIsLoading() {
        return MoneyBoost_isLoading;
    }

    public void MoneyBoostInitSplashAdapter() {
        // 预加载在 MobileAds.initialize 完成后由 Load 触发
    }

    public void MoneyBoostLoadSplashAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            return;
        }
        if (MoneyBoost_isLoading || MoneyBoost_isShowing || AppOpenAdPreloader.isAdAvailable(unitId)) {
            return;
        }
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = true;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADSPLASH");
        if (!MoneyBoostStartSplashPreloading(unitId)) {
            MoneyBoost_isLoading = false;
        }
    }

    private boolean MoneyBoostStartSplashPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest);
        return AppOpenAdPreloader.start(unitId, preloadConfig, MoneyBoost_preloadCallback);
    }

    public boolean MoneyBoostIsReady() {
        String unitId = MoneyBoostUnitId();
        return MoneyBoost_adReady && unitId != null && AppOpenAdPreloader.isAdAvailable(unitId);
    }

    public void MoneyBoostShowSplashAd() {
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            if (MoneyBoost_splashListener != null) {
                MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
            }
            return;
        }
        final String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            if (MoneyBoost_splashListener != null) {
                MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
            }
            return;
        }
        if (!AppOpenAdPreloader.isAdAvailable(unitId)) {
            MoneyBoostLoadSplashAd();
            if (MoneyBoost_splashListener != null) {
                MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
            }
            return;
        }
        MoneyBoost_activity.runOnUiThread(() -> {
            final AppOpenAd appOpenAd = AppOpenAdPreloader.pollAd(unitId);
            if (appOpenAd == null) {
                MoneyBoostLoadSplashAd();
                if (MoneyBoost_splashListener != null) {
                    MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
                }
                return;
            }
            MoneyBoost_adReady = false;
            appOpenAd.setAdEventCallback(new AppOpenAdEventCallback() {
                @Override
                public void onAdShowedFullScreenContent() {
                    MoneyBoost_isShowing = true;
                    MoneyBoostPostToMain(() -> {
                        if (MoneyBoost_splashListener != null) {
                            MoneyBoost_splashListener.MoneyBoostOnSplashAdDisplayed();
                        }
                    });
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    MoneyBoost_isShowing = false;
                    MoneyBoost_adReady = false;
                    appOpenAd.destroy();
                    MoneyBoostPostToMain(() -> {
                        if (MoneyBoost_splashListener != null) {
                            MoneyBoost_splashListener.MoneyBoostOnSplashAdClosed();
                        }
                        MoneyBoostLoadSplashAd();
                    });
                }

                @Override
                public void onAdFailedToShowFullScreenContent(
                        @NonNull FullScreenContentError fullScreenContentError) {
                    MoneyBoost_isShowing = false;
                    MoneyBoost_adReady = false;
                    appOpenAd.destroy();
                    MoneyBoostPostToMain(() -> {
                        if (MoneyBoost_splashListener != null) {
                            MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
                        }
                        MoneyBoostLoadSplashAd();
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
            appOpenAd.show(MoneyBoost_activity);
        });
    }

    public void MoneyBoostOnDestroy() {
        MoneyBoostCancelRetry();
        MoneyBoost_isLoading = false;
        MoneyBoost_isShowing = false;
        MoneyBoost_adReady = false;
        String unitId = MoneyBoostUnitId();
        if (unitId != null) {
            AppOpenAdPreloader.destroy(unitId);
        }
        MoneyBoost_activity = null;
        MoneyBoost_splashListener = null;
    }

    private void MoneyBoostCancelRetry() {
        MoneyBoostAdRetryScheduler.cancel(MoneyBoost_retryFuture);
        MoneyBoost_retryFuture = null;
    }

    private String MoneyBoostUnitId() {
        if (MoneyBoost_ad_unit == null) {
            return null;
        }
        String unitId = MoneyBoost_ad_unit.trim();
        return unitId.isEmpty() ? null : unitId;
    }
}
