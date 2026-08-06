package com.freddie.frmusicsdk;

import android.content.Intent;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.dream.laughtale.LaughTaleFirebaseManager;
import com.dream.laughtale.LaughTaleMediationManager;
import com.dream.laughtale.LaughTaleToolsManager;

public class MainActivity extends AppCompatActivity {

    private static final long STATUS_POLL_INTERVAL_MS = 500L;

    private LaughTaleMediationManager mediationManager;
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

    private final Runnable statusUpdater = new Runnable() {
        @Override
        public void run() {
            refreshAdButtonStatus();
            statusHandler.postDelayed(this, STATUS_POLL_INTERVAL_MS);
        }
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

        LaughTaleToolsManager.instance().LaughTale_isDebug = BuildConfig.DEBUG;
        LaughTaleFirebaseManager.instance().LaughTaleInitFirebaseAnalytics(this);

        mediationManager = LaughTaleMediationManager.getInstance();
        mediationManager.LaughTaleInit(this, this, () -> {
            mediationManager.LaughTaleShowSplashADWithUnity("launch");
            mediationManager.LaughTaleShowBannerView();
        });

        setupButtons();
        refreshAdButtonStatus();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mediationManager.LaughTaleOnAppStart(this);
        statusHandler.post(statusUpdater);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mediationManager.LaughTaleOnAppResume();
    }

    @Override
    protected void onPause() {
        mediationManager.LaughTaleOnAppPause();
        super.onPause();
    }

    @Override
    protected void onStop() {
        statusHandler.removeCallbacks(statusUpdater);
        mediationManager.LaughTaleOnAppStop();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (mediationManager != null) {
            mediationManager.LaughTaleOnAppDestroy(this);
        }
        super.onDestroy();
    }

    private void setupButtons() {
        btnCollapsibleBanner.setOnClickListener(v ->
                mediationManager.LaughTaleShowCollapsibleBannerView(true));

        btnInterstitial.setOnClickListener(v ->
                mediationManager.LaughTaleShowInterstitialUnity("test"));

        btnRewarded.setOnClickListener(v ->
                mediationManager.LaughTaleShowRewardAdUnity("test"));

        btnMrec1.setOnClickListener(v ->
                mediationManager.LaughTaleShowMRECBannerViewPublic(1, false, "test"));

        btnMrec2.setOnClickListener(v ->
                mediationManager.LaughTaleShowMRECBannerViewPublic(2, false, "test"));

        btnMrec3.setOnClickListener(v ->
                mediationManager.LaughTaleShowMRECBannerViewPublic(3, false, "test"));

        btnMrec4.setOnClickListener(v ->
                mediationManager.LaughTaleShowMRECBannerViewPublic(4, false, "test"));

        findViewById(R.id.btn_hide_mrec).setOnClickListener(v ->
                mediationManager.LaughTaleHideMRECBannerViewPublic("test_hide"));
    }

    private void refreshAdButtonStatus() {
        refreshAdLoadStatus();
        updateAdButton(btnCollapsibleBanner,
                mediationManager.LaughTaleDebugIsCollapsibleReady(),
                mediationManager.LaughTaleDebugIsCollapsibleShowing());
        updateAdButton(btnInterstitial,
                mediationManager.LaughTaleDebugIsInterReady(),
                mediationManager.LaughTaleDebugIsInterShowing());
        updateAdButton(btnRewarded,
                mediationManager.LaughTaleDebugIsRewardReady(),
                mediationManager.LaughTaleDebugIsRewardShowing());
        updateAdButton(btnMrec1,
                mediationManager.LaughTaleDebugIsMrecReady(),
                mediationManager.LaughTaleDebugIsMrecShowing());
        updateAdButton(btnMrec2,
                mediationManager.LaughTaleDebugIsMrecReady(),
                mediationManager.LaughTaleDebugIsMrecShowing());
        updateAdButton(btnMrec3,
                mediationManager.LaughTaleDebugIsMrecReady(),
                mediationManager.LaughTaleDebugIsMrecShowing());
        updateAdButton(btnMrec4,
                mediationManager.LaughTaleDebugIsMrecReady(),
                mediationManager.LaughTaleDebugIsMrecShowing());
    }

    private void updateAdButton(Button button, boolean ready, boolean showing) {
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
                "Native: " + loadStatusText(mediationManager.LaughTaleDebugIsNativeReady()) + "\n"
                        + "Inter: " + loadStatusText(mediationManager.LaughTaleDebugIsInterReady()) + "\n"
                        + "Rewarded: " + loadStatusText(mediationManager.LaughTaleDebugIsRewardVideoReady()) + "\n"
                        + "Splash: " + loadStatusText(mediationManager.LaughTaleDebugIsSplashReady()));
    }

    private String loadStatusText(boolean ready) {
        return ready ? "已加载" : "未加载";
    }
}
