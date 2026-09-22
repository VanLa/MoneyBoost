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

import com.dream.moneyboost.MoneyBoostFirebaseManager;
import com.dream.moneyboost.MoneyBoostMediationManager;
import com.dream.moneyboost.MoneyBoostToolsManager;

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
    private Button btnMrec1;
    private Button btnMrec2;
    private Button btnMrec3;
    private Button btnMrec4;
    private TextView tvAdLoadStatus;
    private int colorNotReady;
    private int colorShowing;
    private int colorReady;

    private boolean splashFlowFinished = false;

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            refreshAdButtonStatus();
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
        btnMrec1 = findViewById(R.id.btn_mrec_1);
        btnMrec2 = findViewById(R.id.btn_mrec_2);
        btnMrec3 = findViewById(R.id.btn_mrec_3);
        btnMrec4 = findViewById(R.id.btn_mrec_4);
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
            }
        });

        mediationManager.MoneyBoostInit(this, this, () -> {
            Log.i(TAG, "Init ok, request cold splash. " + mediationManager.MoneyBoostDebugSplashStateText());
            // 先开屏，Banner 等开屏结束/超时后再出（避免干扰观察开屏）
            mediationManager.MoneyBoostShowSplashADWithUnity("launch");
            statusHandler.removeCallbacks(splashWaitTimeoutRunnable);
            statusHandler.postDelayed(splashWaitTimeoutRunnable, SPLASH_WAIT_TIMEOUT_MS);
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
            mediationManager.MoneyBoostOnAppDestroy(this);
        }
        super.onDestroy();
    }

    private void setupButtons() {
        btnCollapsibleBanner.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowCollapsibleBannerView(true);
        });

        btnInterstitial.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowInterstitialUnity("test");
        });

        btnRewarded.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowRewardAdUnity("test");
        });

        btnMrec1.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowMRECBannerViewPublic(1, false, "test");
        });

        btnMrec2.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowMRECBannerViewPublic(2, false, "test");
        });

        btnMrec3.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowMRECBannerViewPublic(3, false, "test");
        });

        btnMrec4.setOnClickListener(v -> {
            cancelSplashIfUserStartedPlaying();
            mediationManager.MoneyBoostShowMRECBannerViewPublic(4, false, "test");
        });

        findViewById(R.id.btn_hide_mrec).setOnClickListener(v ->
                mediationManager.MoneyBoostHideMRECBannerViewPublic("test_hide"));
    }

    private void refreshAdButtonStatus() {
        refreshAdLoadStatus();
        updateAdButton(btnCollapsibleBanner,
                mediationManager.MoneyBoostDebugIsCollapsibleReady(),
                mediationManager.MoneyBoostDebugIsCollapsibleShowing());
        updateAdButton(btnInterstitial,
                mediationManager.MoneyBoostDebugIsInterReady(),
                mediationManager.MoneyBoostDebugIsInterShowing());
        updateAdButton(btnRewarded,
                mediationManager.MoneyBoostDebugIsRewardReady(),
                mediationManager.MoneyBoostDebugIsRewardShowing());
        updateAdButton(btnMrec1,
                mediationManager.MoneyBoostDebugIsMrecReady(),
                mediationManager.MoneyBoostDebugIsMrecShowing());
        updateAdButton(btnMrec2,
                mediationManager.MoneyBoostDebugIsMrecReady(),
                mediationManager.MoneyBoostDebugIsMrecShowing());
        updateAdButton(btnMrec3,
                mediationManager.MoneyBoostDebugIsMrecReady(),
                mediationManager.MoneyBoostDebugIsMrecShowing());
        updateAdButton(btnMrec4,
                mediationManager.MoneyBoostDebugIsMrecReady(),
                mediationManager.MoneyBoostDebugIsMrecShowing());
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
