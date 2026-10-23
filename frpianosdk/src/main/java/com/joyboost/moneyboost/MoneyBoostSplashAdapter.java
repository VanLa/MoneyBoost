package com.joyboost.moneyboost;

import android.app.Activity;
import android.util.Log;
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

/** 标准 App Open（GMA Next-Gen Preloader），供冷/热启动开屏使用。 */
public class MoneyBoostSplashAdapter {
    public String MoneyBoost_ad_unit;
    public Activity MoneyBoost_activity;
    public MoneyBoostSplashListener MoneyBoost_splashListener;
    private volatile boolean MoneyBoost_isShowing = false;
    private volatile boolean MoneyBoost_isLoading = false;
    private boolean MoneyBoost_preloadingStarted;
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
            if (MoneyBoost_activity == null) return;
            MoneyBoostAdDiagnostics.load("splash", MoneyBoost_ad_unit, "success", null);
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADSPLASHLoaded");
            MoneyBoost_isLoading = false;

            MoneyBoostPostToMain(() -> {
                if (MoneyBoost_splashListener != null) {
                    MoneyBoost_splashListener.MoneyBoostOnSplashAdLoaded();
                }
            });
        }

        @Override
        public void onAdsExhausted(@NonNull String preloadId) {
            // The SDK replenishes the registered preload configuration automatically.

            MoneyBoost_isLoading = false;
        }

        @Override
        public void onAdFailedToPreload(@NonNull String preloadId, @NonNull LoadAdError adError) {
            if (MoneyBoost_activity == null) return;
            MoneyBoostAdDiagnostics.load("splash", MoneyBoost_ad_unit, "failed", adError);
            Log.w("MoneyBoostAds", "Splash load failed unit=" + preloadId + " code=" + adError.getCode() + " message=" + adError.getMessage());
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADSPLASHFailed");
            MoneyBoost_isLoading = false;

            // The registered SDK preloader owns retries and replenishment.
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

    public synchronized void MoneyBoostLoadSplashAd() {
        if (!MoneyBoostMediationManager.getInstance().MoneyBoostCanLoadAds()) return;
        if (MoneyBoost_activity == null || MoneyBoost_activity.isFinishing() || MoneyBoost_activity.isDestroyed()) {
            return;
        }
        String unitId = MoneyBoostUnitId();
        if (unitId == null) {
            return;
        }
        if (MoneyBoost_preloadingStarted) {
            return;
        }

        MoneyBoost_isLoading = true;
        MoneyBoostAdDiagnostics.load("splash", MoneyBoost_ad_unit, "requested", null);
        Log.i("MoneyBoostAds", "Splash load requested unit=" + MoneyBoost_ad_unit);
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=========", "LOADSPLASH");
        MoneyBoost_preloadingStarted = true;
        try {
            if (!MoneyBoostStartSplashPreloading(unitId)) {
                MoneyBoost_preloadingStarted = false;
                MoneyBoostAdDiagnostics.load("splash", MoneyBoost_ad_unit, "preloader_rejected", null);
                MoneyBoost_isLoading = false;
            }
        } catch (RuntimeException error) {
            MoneyBoost_preloadingStarted = false;
            MoneyBoost_isLoading = false;
            throw error;
        }
    }

    private boolean MoneyBoostStartSplashPreloading(String unitId) {
        AdRequest adRequest = new AdRequest.Builder(unitId).build();
        PreloadConfiguration preloadConfig = new PreloadConfiguration(adRequest, 1);
        return AppOpenAdPreloader.start(unitId, preloadConfig, MoneyBoost_preloadCallback);
    }

    public boolean MoneyBoostIsReady() {
        String unitId = MoneyBoostUnitId();
        return unitId != null && AppOpenAdPreloader.isAdAvailable(unitId);
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
            if (!MoneyBoostMediationManager.getInstance().MoneyBoostCanLoadAds()
                    || MoneyBoost_activity == null || MoneyBoost_activity.isFinishing()
                    || MoneyBoost_activity.isDestroyed()) {
                if (MoneyBoost_splashListener != null) MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
                return;
            }
            if (MoneyBoost_isShowing) return;
            final AppOpenAd appOpenAd = AppOpenAdPreloader.pollAd(unitId);
            if (appOpenAd == null) {
                MoneyBoostLoadSplashAd();
                if (MoneyBoost_splashListener != null) {
                    MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
                }
                return;
            }

            MoneyBoost_isShowing = true;
            final java.util.concurrent.atomic.AtomicBoolean finished =
                    new java.util.concurrent.atomic.AtomicBoolean();
            appOpenAd.setAdEventCallback(new AppOpenAdEventCallback() {
                private boolean displayed;
                @Override
                public synchronized void onAdShowedFullScreenContent() {
                    if (finished.get() || displayed) return;
                    displayed = true;
                    MoneyBoost_isShowing = true;
                    MoneyBoostPostToMain(() -> {
                        if (MoneyBoost_splashListener != null) {
                            MoneyBoost_splashListener.MoneyBoostOnSplashAdDisplayed();
                        }
                    });
                }

                @Override
                public synchronized void onAdDismissedFullScreenContent() {
                    if (!finished.compareAndSet(false, true)) return;
                    MoneyBoost_isShowing = false;

                    appOpenAd.destroy();
                    MoneyBoostPostToMain(() -> {
                        if (MoneyBoost_splashListener != null) {
                            MoneyBoost_splashListener.MoneyBoostOnSplashAdClosed();
                        }
                    });
                }

                @Override
                public synchronized void onAdFailedToShowFullScreenContent(
                        @NonNull FullScreenContentError fullScreenContentError) {
                    if (!finished.compareAndSet(false, true)) return;
                    MoneyBoost_isShowing = false;

                    appOpenAd.destroy();
                    MoneyBoostPostToMain(() -> {
                        if (MoneyBoost_splashListener != null) {
                            MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
                        }
                    });
                }

                @Override
                public void onAdClicked() {
                }

                @Override
                public void onAdPaid(@NonNull AdValue adValue) {
                    // Keep Taichi paid revenue separate from Firebase automatic ad_impression.
                    ResponseInfo info = appOpenAd.getResponseInfo();
                    String network = "AdMob";
                    if (info != null && info.getLoadedAdSourceResponseInfo() != null) {
                        network = info.getLoadedAdSourceResponseInfo().getName();
                    }
                    MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseRevenue(
                            adValue.getValueMicros() / 1_000_000.0, "APP_OPEN", network, unitId);
                }
            });
            try {
                appOpenAd.show(MoneyBoost_activity);
            } catch (RuntimeException error) {
                if (finished.compareAndSet(false, true)) {
                    MoneyBoost_isShowing = false;
                    appOpenAd.destroy();
                    if (MoneyBoost_splashListener != null) MoneyBoost_splashListener.MoneyBoostOnSplashAdFailedToShow();
                }
            }
        });
    }

    public synchronized void MoneyBoostOnDestroy() {

        MoneyBoost_isLoading = false;
        MoneyBoost_isShowing = false;

        String unitId = MoneyBoostUnitId();
        if (unitId != null) {
            AppOpenAdPreloader.destroy(unitId);
        }
        MoneyBoost_preloadingStarted = false;
        MoneyBoost_activity = null;
        MoneyBoost_splashListener = null;
    }

    private String MoneyBoostUnitId() {
        if (MoneyBoost_ad_unit == null) {
            return null;
        }
        String unitId = MoneyBoost_ad_unit.trim();
        return unitId.isEmpty() ? null : unitId;
    }
}
