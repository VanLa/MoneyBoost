package com.freddie.frmusicsdk;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.joyboost.moneyboost.MoneyBoostFirebaseManager;
import com.joyboost.moneyboost.MoneyBoostMediationManager;
import com.joyboost.moneyboost.MoneyBoostToolsManager;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MoneyBoostDemo";
    private static final long STATUS_POLL_INTERVAL_MS = 500L;
    /** 对齐 Quiz Loading：等开屏最多 12s，超时后仍显示 Banner */
    private static final long SPLASH_WAIT_TIMEOUT_MS = 12000L;

    private MoneyBoostMediationManager mediationManager;
    private final Handler statusHandler = new Handler(Looper.getMainLooper());
    private Button btnCollapsibleBanner;
    private Button btnInterstitial;
    private Button btnRewarded;
    private TextView tvAdLoadStatus;
    private int colorNotReady;
    private int colorShowing;
    private int colorReady;

    private boolean splashFlowFinished = false;
    private String nativePlacement;
    private boolean sdkInitialized;
    private static final String NATIVE_SCENE = "demo";

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            refreshAdButtonStatus();
            updateNativeDemo();
            statusHandler.postDelayed(this, STATUS_POLL_INTERVAL_MS);
        }
    };

    private final Runnable splashWaitTimeoutRunnable = () -> {
        Log.w(TAG, "冷启动开屏等待超时，显示 Banner");
        finishSplashFlowAndShowBanner();
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        colorNotReady = ContextCompat.getColor(this, R.color.ad_status_not_ready);
        colorShowing = ContextCompat.getColor(this, R.color.ad_status_showing);
        colorReady = ContextCompat.getColor(this, R.color.white);

        btnCollapsibleBanner = findViewById(R.id.btn_collapsible_banner);
        btnInterstitial = findViewById(R.id.btn_interstitial);
        btnRewarded = findViewById(R.id.btn_rewarded);
        tvAdLoadStatus = findViewById(R.id.tv_ad_load_status);

        MoneyBoostToolsManager.instance().MoneyBoost_isDebug = BuildConfig.DEBUG;
        MoneyBoostFirebaseManager.instance().MoneyBoostInitFirebaseAnalytics(this);

        mediationManager = MoneyBoostMediationManager.getInstance();

        mediationManager.MoneyBoostSetDebugCallbackListener(callback -> {
            Log.i(TAG, "UnityCallback: " + callback);
            if ("MoneyBoost_SPLASH_OPEN".equals(callback)) {
                statusHandler.removeCallbacks(splashWaitTimeoutRunnable);
            } else if ("MoneyBoost_SPLASH_CLOSE".equals(callback)) {
                finishSplashFlowAndShowBanner();
            } else if ("MoneyBoost_INTERSTITIAL_CLOSE".equals(callback)
                    || "MoneyBoost_REWARD_CLOSE".equals(callback)) {
                startNativePreloaders();
            }
        });

        mediationManager.MoneyBoostInit(this, () -> {
            sdkInitialized = true;
            Log.i(TAG, "Init ok, request cold splash. " + mediationManager.MoneyBoostDebugSplashStateText());
            // 先开屏，Banner 等开屏结束/超时后再出（避免干扰观察开屏）
            mediationManager.MoneyBoostShowSplashADWithUnity("launch");
            statusHandler.removeCallbacks(splashWaitTimeoutRunnable);
            statusHandler.postDelayed(splashWaitTimeoutRunnable, SPLASH_WAIT_TIMEOUT_MS);
            startNativePreloaders();
        });

        setupButtons();
        refreshAdButtonStatus();
    }

    private void finishSplashFlowAndShowBanner() {
        if (splashFlowFinished) {
            return;
        }
        splashFlowFinished = true;
        statusHandler.removeCallbacks(splashWaitTimeoutRunnable);
        mediationManager.MoneyBoostShowBannerView();
        startNativePreloaders();
        Log.i(TAG, "Banner shown after splash flow. " + mediationManager.MoneyBoostDebugSplashStateText());
    }

    /** 用户开始点测广告按钮 = 进入「玩法」，取消排队中的冷启动开屏 */
    private void cancelSplashIfUserStartedPlaying() {
        mediationManager.MoneyBoostCancelSplashADWithUnity();
        finishSplashFlowAndShowBanner();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mediationManager.MoneyBoostOnAppStart(this);
        statusHandler.post(statusUpdater);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mediationManager.MoneyBoostOnAppResume();
        startNativePreloaders();
    }

    @Override
    protected void onPause() {
        mediationManager.MoneyBoostOnAppPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        statusHandler.removeCallbacks(statusUpdater);
        mediationManager.MoneyBoostOnAppStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        statusHandler.removeCallbacks(splashWaitTimeoutRunnable);
        if (mediationManager != null) {
            mediationManager.MoneyBoostOnAppDestroy();
        }
        super.onDestroy();
    }

    private void setupButtons() {
        findViewById(R.id.btn_show_native).setOnClickListener(v -> showNativeDemo());
        findViewById(android.R.id.content).getViewTreeObserver().addOnScrollChangedListener(this::updateNativeDemo);
        findViewById(R.id.btn_hide_collapsible_banner).setOnClickListener(v -> {
            mediationManager.MoneyBoostHideCollapsibleBannerView();
            startNativePreloaders();
        });
        btnCollapsibleBanner.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            nativePlacement = null;
            mediationManager.MoneyBoostDestroyNativeAd(NATIVE_SCENE);
            // Each explicit demo click tests a fresh expansion, even if the previous ad was collapsed.
            mediationManager.MoneyBoostHideCollapsibleBannerView();
            mediationManager.MoneyBoostShowCollapsibleBannerView("demo_fold");
        });

        btnInterstitial.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowInterstitialUnity("test");
        });

        btnRewarded.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowRewardAdUnity("test");
        });





    }

    /** Registers official Google preloaders. Once registered, the SDK owns retries and replenishment. */
    private void startNativePreloaders() {
        if (!sdkInitialized || !splashFlowFinished || isFinishing() || isDestroyed()) return;
        mediationManager.MoneyBoostLoadNativeAd(NATIVE_SCENE, 0);
        mediationManager.MoneyBoostSetNativeContainer(NATIVE_SCENE, findViewById(R.id.native_demo_slot));
    }

    private void showNativeDemo() {
        cancelSplashIfUserStartedPlaying();
        mediationManager.MoneyBoostHideCollapsibleBannerView();
        nativePlacement = NATIVE_SCENE;
        mediationManager.MoneyBoostLoadNativeAd(NATIVE_SCENE, 0);
        mediationManager.MoneyBoostSetNativeContainer(NATIVE_SCENE, findViewById(R.id.native_demo_slot));
        updateNativeDemo();
    }

    private void updateNativeDemo() {
        TextView status = findViewById(R.id.tv_native_status);
        status.setText("Native: " + (mediationManager.MoneyBoostIsNativeAdReady(NATIVE_SCENE) ? "ready" : "preloading")
                + " · scroll the entire card into view · hidden during fold/fullscreen ads");
        if (nativePlacement == null) return;
        android.view.View root = findViewById(android.R.id.content), slot = findViewById(R.id.native_demo_slot);
        android.graphics.Rect full = new android.graphics.Rect(), visible = new android.graphics.Rect();
        int[] origin = new int[2]; root.getLocationOnScreen(origin);
        int[] at = new int[2]; slot.getLocationOnScreen(at);
        full.set(at[0], at[1], at[0] + slot.getWidth(), at[1] + slot.getHeight());
        if (root.getWidth() == 0 || root.getHeight() == 0 || !slot.getGlobalVisibleRect(visible)) {
            mediationManager.MoneyBoostHideNativeAd(nativePlacement); return;
        }
        mediationManager.MoneyBoostShowNativeAdClipped(nativePlacement,
                (at[0] - origin[0]) / (float)root.getWidth(), (at[1] - origin[1]) / (float)root.getHeight(),
                slot.getWidth() / (float)root.getWidth(), slot.getHeight() / (float)root.getHeight(),
                (visible.left-origin[0])/(float)root.getWidth(), (visible.top-origin[1])/(float)root.getHeight(),
                visible.width()/(float)root.getWidth(),visible.height()/(float)root.getHeight());
    }

    private void refreshAdButtonStatus() {
        refreshAdLoadStatus();
        btnCollapsibleBanner.setText("Show CollapsibleBanner\n" + mediationManager.MoneyBoostDebugCollapsibleStateText());
        updateAdButton(btnCollapsibleBanner,
                mediationManager.MoneyBoostDebugIsCollapsibleReady(),
                mediationManager.MoneyBoostDebugIsCollapsibleShowing());
        updateAdButton(btnInterstitial,
                mediationManager.MoneyBoostDebugIsInterReady(),
                mediationManager.MoneyBoostDebugIsInterShowing());
        updateAdButton(btnRewarded,
                mediationManager.MoneyBoostDebugIsRewardReady(),
                mediationManager.MoneyBoostDebugIsRewardShowing());
    }

    private void updateAdButton(Button button, boolean ready, boolean showing) {
        if (button == null) {
            return;
        }
        int color;
        if (showing) {
            color = colorShowing;
        } else if (!ready) {
            color = colorNotReady;
        } else {
            color = colorReady;
        }
        button.setBackground(new ColorDrawable(color));
    }

    private void refreshAdLoadStatus() {
        if (mediationManager == null || tvAdLoadStatus == null) {
            return;
        }
        tvAdLoadStatus.setText(
"Inter: " + loadStatusText(mediationManager.MoneyBoostDebugIsInterReady()) + "\n"
                        + "Rewarded: " + loadStatusText(mediationManager.MoneyBoostDebugIsRewardVideoReady()) + "\n"
                        + "Splash: " + loadStatusText(mediationManager.MoneyBoostDebugIsSplashReady()) + "\n"
                        + mediationManager.MoneyBoostDebugSplashStateText());
    }

    private String loadStatusText(boolean ready) {
        return ready ? "已加载" : "未加载";
    }
}
