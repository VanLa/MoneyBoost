package com.joyboost.moneyboost;
//import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;


import org.json.JSONObject;

import java.util.List;
import java.util.ArrayList;

public class MoneyBoostMediationManager implements MoneyBoostInterstitialListener, MoneyBoostRewardVideoListener, MoneyBoostSplashListener {

    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private static volatile MoneyBoostMediationManager instance;

    private Activity MoneyBoost_activity;

    private MoneyBoostInterstitialAdapter MoneyBoost_interAdapter;

    private MoneyBoostRewardVideoAdapter MoneyBoost_rewardAdapter;


    private MoneyBoostBannerAdapter MoneyBoost_bannerAdapter;
    private MoneyBoostBannerAdapter MoneyBoost_collapsibleAdapter;

    /** 标准 App Open 开屏，来自 MoneyBoost_SPLASH_ID */
    private MoneyBoostSplashAdapter MoneyBoost_splashAdapter;

    /** Legacy full-screen inventory is disabled; interstitials use the AdMob interstitial format. */
    /**
     * 竞品 CanShowAoaResume：切过后台才允许热启动开屏；
     * 展示全屏广告时清掉，避免插屏/激励刚关就立刻出开屏。
     */
    private boolean MoneyBoost_canShowAoaResume = false;

    private long MoneyBoost_sessionLaunchCnt = 0;

    private boolean MoneyBoost_isInitSuccess = false;
    private boolean MoneyBoost_isInitializing = false;
    private boolean MoneyBoost_initFailed = false;
    private int MoneyBoost_initGeneration = 0;
    private static final String INIT_TAG = "MoneyBoostInit";
    private static final long INIT_TIMEOUT_MS = 45000L;
    private final List<ADInitListener> MoneyBoost_initListeners = new ArrayList<>();
    private Runnable MoneyBoost_initTimeout;

    private String MoneyBoost_interKey = "";
    private String MoneyBoost_rewardKey = "";
    private String MoneyBoost_bannerKey = "";
    private String MoneyBoost_splashKey = "";

    private static final long LOAD_DELAY_INTER_MS = 800L;
    private static final long LOAD_DELAY_REWARD_MS = 1200L;
    private static final long LOAD_DELAY_BANNER_MS = 1500L;
    private static final long LOAD_DELAY_INTER_LOW_END_MS = 1400L;
    private static final long LOAD_DELAY_REWARD_LOW_END_MS = 2000L;
    private static final long LOAD_DELAY_BANNER_LOW_END_MS = 2500L;
    private static final long AD_TICK_INTERVAL_MS = 5000L;
    private static final long COLD_SPLASH_WAIT_TIMEOUT_MS = 12000L;

    private boolean MoneyBoost_needPopAD = true;

    private boolean MoneyBoost_isInPlaying = false;

    private boolean MoneyBoost_isAppForeground = true;
    private boolean MoneyBoost_isDestroyed = false;

    /** REWARD_COMPLETE 已发给 Unity，避免重复 */
    private boolean MoneyBoost_rewardCompleteDispatched = false;

    /** 开屏 App Open 正在展示或 show 请求已发出 */
    private boolean MoneyBoost_splashSessionActive = false;
    /** 已向 Unity 发送 SPLASH_OPEN（展示失败时需配对 SPLASH_CLOSE） */
    private boolean MoneyBoost_splashOpenDispatched = false;
    /** 本进程冷启动开屏已处理（Unity 只应调一次；防止重复走冷启动流程） */
    private boolean MoneyBoost_coldSplashHandled = false;
    /** 冷启动开屏流程结束后，允许 SDK 在回前台触发热启动开屏 */
    private boolean MoneyBoost_allowHotStartSplash = false;
    /** 用户已切到后台（onStop），回前台时可尝试热启动开屏 */
    private boolean MoneyBoost_wentToBackground = false;
    /** 热启动开屏等待 App Open 加载中 */
    private boolean MoneyBoost_hotStartSplashPending = false;
    private static final String MONEYBOOST_HOT_START_SPLASH_SCENE = "background";


    private boolean MoneyBoost_rewardVideoInSession = false;
    private boolean MoneyBoost_rewardShowPending = false;
    private String MoneyBoost_rewardShowScene = "";
    private boolean MoneyBoost_rewardCloseDispatched = false;
    private boolean MoneyBoost_interstitialInSession = false;
    private boolean MoneyBoost_interstitialShowPending = false;
    private boolean MoneyBoost_interstitialCloseDispatched = false;

    /** Unity 冷启动开屏请求中（ShowSplashADWithUnity） */
    private boolean MoneyBoost_unityColdSplashPending = false;
    private String MoneyBoost_unityColdSplashScene = "launch";
    private long MoneyBoost_coldSplashDeadline = 0L;

    private int MoneyBoost_fullscreenAdRefCount = 0;
    /** Unity 已调用 showBanner，后续由 SDK 自行管理 Banner 显示/隐藏 */
    private boolean MoneyBoost_bannerEnabled = false;
    private boolean MoneyBoost_collapsibleActive = false;

    private SharedPreferences MoneyBoost_dataPrefs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable coldSplashTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!MoneyBoost_unityColdSplashPending) {
                return;
            }
            Log.w(INIT_TAG, "Cold splash wait timed out; continue game");
            MoneyBoost_splashSessionActive = false;
            MoneyBoostFinishColdStartSplashFlow();
        }
    };
    private final Runnable hotStartSplashTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!MoneyBoost_hotStartSplashPending) {
                return;
            }
            MoneyBoostFinishHotStartSplashAttemptNotReady();
        }
    };
    private final Runnable adTickRunnable = new Runnable() {
        @Override
        public void run() {
            try {
                MoneyBoostTickAdReload();
            } catch (Exception | LinkageError e) {
                Log.e(INIT_TAG, "Ad reload failed", e);
            }
            if (!MoneyBoost_isDestroyed && MoneyBoost_isInitSuccess) {
                mainHandler.postDelayed(this, AD_TICK_INTERVAL_MS);
            }
        }
    };
    private final Runnable interLoadRunnable = new Runnable() {
        @Override
        public void run() {
            if (!MoneyBoost_isInitSuccess || MoneyBoost_isDestroyed) return;
            try {
                MoneyBoostLoadInterstitialAd();
            } catch (Exception | LinkageError e) {
                Log.e(INIT_TAG, "MoneyBoostLoadInterstitialAd failed", e);
            }
        }
    };
    private final Runnable rewardLoadRunnable = new Runnable() {
        @Override
        public void run() {
            if (!MoneyBoost_isInitSuccess || MoneyBoost_isDestroyed) return;
            try {
                MoneyBoostLoadRewardAd();
            } catch (Exception | LinkageError e) {
                Log.e(INIT_TAG, "MoneyBoostLoadRewardAd failed", e);
            }
        }
    };
    private final Runnable bannerLoadRunnable = new Runnable() {
        @Override
        public void run() {
            if (!MoneyBoost_isInitSuccess || MoneyBoost_isDestroyed) return;
            try {
                MoneyBoostLoadBannerView();
            } catch (Exception | LinkageError e) {
                Log.e(INIT_TAG, "MoneyBoostLoadBannerView failed", e);
            }
        }
    };

    public interface ADInitListener {
        void onAdInitSuccess();
        default void onAdInitFailed(String reason) { }
        /** Always called after success/failure/timeout. Game startup must not depend on success. */
        default void onAdInitFinished(boolean success, String reason) { }
    }

    private void MoneyBoostNotifyInitListener(ADInitListener listener, boolean success, String reason) {
        if (listener == null) return;
        try {
            if (success) listener.onAdInitSuccess();
            else listener.onAdInitFailed(reason);
        } catch (Exception | LinkageError e) {
            Log.e(INIT_TAG, "Host initialization callback failed", e);
        } finally {
            try {
                listener.onAdInitFinished(success, reason);
            } catch (Exception | LinkageError e) {
                Log.e(INIT_TAG, "Host initialization finished callback failed", e);
            }
        }
    }

    private boolean MoneyBoostIsCurrentInit(int generation) {
        // A timed-out attempt may recover later, provided no retry/lifecycle superseded it.
        return !MoneyBoost_isDestroyed && !MoneyBoost_isInitSuccess
                && generation == MoneyBoost_initGeneration;
    }

    /** Runs on the main thread; stale callbacks cannot complete a newer attempt. */
    private void MoneyBoostCompleteInit(int generation, boolean success, String reason) {
        if (!MoneyBoostIsCurrentInit(generation)) return;
        if (!success && !MoneyBoost_isInitializing) return;
        if (MoneyBoost_initTimeout != null) mainHandler.removeCallbacks(MoneyBoost_initTimeout);
        MoneyBoost_initTimeout = null;
        MoneyBoost_isInitializing = false;
        MoneyBoost_isInitSuccess = success;
        MoneyBoost_initFailed = !success;
        Log.i(INIT_TAG, "Initialization finished: success=" + success + " reason=" + reason);
        List<ADInitListener> listeners = new ArrayList<>(MoneyBoost_initListeners);
        MoneyBoost_initListeners.clear();
        try {
            if (!success && MoneyBoost_unityColdSplashPending) MoneyBoostFinishColdStartSplashFlow();
            MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback",
                    success ? "MoneyBoost_INIT_SUCCESS" : "MoneyBoost_INIT_FAILED");
        } catch (Exception | LinkageError e) {
            Log.e(INIT_TAG, "Unity initialization notification failed", e);
        }
        for (ADInitListener listener : listeners) {
            MoneyBoostNotifyInitListener(listener, success, reason);
        }
    }

    public interface DebugCallbackListener {
        void onCallback(String callback);
    }

    private DebugCallbackListener MoneyBoost_debugCallbackListener;

    public void MoneyBoostSetDebugCallbackListener(DebugCallbackListener listener) {
        MoneyBoost_debugCallbackListener = listener;
    }

    public static MoneyBoostMediationManager getInstance() {
        MoneyBoostMediationManager current = instance;
        if (current == null) {
            synchronized (MoneyBoostMediationManager.class) {
                current = instance;
                if (current == null) {
                    current = new MoneyBoostMediationManager();
                    instance = current;
                }
            }
        }
        return current;
    }
    private MoneyBoostMediationManager() {
    }

    //////////////////////////////////////////////////////////////////////初始化////////////////////////////////////////////////////////////////////////////////

    /** Non-blocking entry point, including when called from Unity's render thread. */
    public void MoneyBoostInit(Activity activity, ADInitListener initListener) {
        mainHandler.post(() -> MoneyBoostBeginInit(activity, initListener));
    }

    private void MoneyBoostBeginInit(Activity activity, ADInitListener initListener) {
        if (MoneyBoost_isInitSuccess) {
            MoneyBoostNotifyInitListener(initListener, true, "Already initialized");
            return;
        }
        if (initListener != null) MoneyBoost_initListeners.add(initListener);
        if (MoneyBoost_isInitializing) return;
        // An explicitly supplied Activity starts a new lifecycle after destruction.
        MoneyBoost_isDestroyed = false;
        MoneyBoost_isInitializing = true;
        MoneyBoost_initFailed = false;
        final int generation = ++MoneyBoost_initGeneration;
        MoneyBoost_initTimeout = () -> MoneyBoostCompleteInit(generation, false,
                "Initialization timed out (UMP or GMA)");
        mainHandler.postDelayed(MoneyBoost_initTimeout, INIT_TIMEOUT_MS);
        Log.i(INIT_TAG, "Initialization started");
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            MoneyBoostCompleteInit(generation, false, "Activity unavailable");
            return;
        }
        this.MoneyBoost_activity = activity;
        final String admobAppId;
        try {
            MoneyBoost_dataPrefs = activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
            MoneyBoostGetUserNoPopAd();
            ApplicationInfo appInfo = activity.getPackageManager().getApplicationInfo(
                    activity.getPackageName(), PackageManager.GET_META_DATA);
            if (appInfo.metaData == null) throw new IllegalStateException("Manifest metadata missing");
            MoneyBoost_interKey = appInfo.metaData.getString("MoneyBoost_INTERSTITIAL_ID");
            MoneyBoost_rewardKey = appInfo.metaData.getString("MoneyBoost_REWARDED_ID");
            MoneyBoost_splashKey = appInfo.metaData.getString("MoneyBoost_SPLASH_ID");
            MoneyBoost_bannerKey = appInfo.metaData.getString("MoneyBoost_BANNER_ID");
            admobAppId = appInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID");
            if (admobAppId == null || admobAppId.trim().isEmpty()) {
                throw new IllegalStateException("AdMob App ID missing");
            }
        } catch (Exception | LinkageError e) {
            Log.e(INIT_TAG, "Read initialization configuration failed", e);
            MoneyBoostCompleteInit(generation, false, e.toString());
            return;
        }
        try {
            ConsentRequestParameters params = new ConsentRequestParameters.Builder().build();
            consentInformation = UserMessagingPlatform.getConsentInformation(activity);
            Log.i(INIT_TAG, "UMP update started");
            consentInformation.requestConsentInfoUpdate(activity, params,
                    () -> mainHandler.post(() -> {
                        if (!MoneyBoostIsCurrentInit(generation)) return;
                        try {
                            Log.i(INIT_TAG, "UMP update complete; checking consent form");
                            UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity,
                                    error -> mainHandler.post(() -> {
                                        if (!MoneyBoostIsCurrentInit(generation)) return;
                                        if (error != null) Log.w(INIT_TAG, "UMP form: " + error.getMessage());
                                        MoneyBoostInitGmaSdk(activity, admobAppId, false, generation);
                                    }));
                        } catch (Exception | LinkageError e) {
                            Log.e(INIT_TAG, "UMP form failed", e);
                            MoneyBoostCompleteInit(generation, false, e.toString());
                        }
                    }),
                    error -> mainHandler.post(() -> {
                        if (!MoneyBoostIsCurrentInit(generation)) return;
                        Log.w(INIT_TAG, "UMP update failed: " + error.getMessage());
                        // Preserve the existing UMP failure fallback; never bypass UMP on timeout.
                        MoneyBoostInitGmaSdk(activity, admobAppId, true, generation);
                    }));
        } catch (Exception | LinkageError e) {
            Log.e(INIT_TAG, "UMP setup failed", e);
            MoneyBoostInitGmaSdk(activity, admobAppId, true, generation);
        }
    }

    private void MoneyBoostInitAdAdapters(Activity activity) {
        // Retry after partial setup: discard any adapters left by the previous attempt.
        if (MoneyBoost_bannerAdapter != null) MoneyBoost_bannerAdapter.MoneyBoostOnDestroy();
        if (MoneyBoost_interAdapter != null) MoneyBoost_interAdapter.MoneyBoostOnDestroy();
        if (MoneyBoost_rewardAdapter != null) MoneyBoost_rewardAdapter.MoneyBoostOnDestroy();
        if (MoneyBoost_splashAdapter != null) MoneyBoost_splashAdapter.MoneyBoostOnDestroy();
        MoneyBoost_bannerAdapter = new MoneyBoostBannerAdapter();
        MoneyBoost_bannerAdapter.MoneyBoost_activity = activity;
        MoneyBoost_bannerAdapter.MoneyBoost_ad_unit = MoneyBoost_bannerKey;
        MoneyBoost_bannerAdapter.MoneyBoostInitBannerAdapter();

        MoneyBoost_interAdapter = new MoneyBoostInterstitialAdapter();
        MoneyBoost_interAdapter.MoneyBoost_activity = activity;
        MoneyBoost_interAdapter.MoneyBoost_ad_unit = MoneyBoost_interKey;
        MoneyBoost_interAdapter.MoneyBoost_InterstitialListener = this;
        MoneyBoost_interAdapter.MoneyBoostInitInterstitialAdapter();

        MoneyBoost_rewardAdapter = new MoneyBoostRewardVideoAdapter();
        MoneyBoost_rewardAdapter.MoneyBoost_activity = activity;
        MoneyBoost_rewardAdapter.MoneyBoost_ad_unit = MoneyBoost_rewardKey;
        MoneyBoost_rewardAdapter.MoneyBoost_rewardVideoListener = this;
        MoneyBoost_rewardAdapter.MoneyBoostInitRewardVideoAdapter();

        MoneyBoost_splashAdapter = new MoneyBoostSplashAdapter();
        MoneyBoost_splashAdapter.MoneyBoost_activity = activity;
        MoneyBoost_splashAdapter.MoneyBoost_ad_unit = MoneyBoost_splashKey;
        MoneyBoost_splashAdapter.MoneyBoost_splashListener = this;
        MoneyBoost_splashAdapter.MoneyBoostInitSplashAdapter();

    }

    private boolean MoneyBoostCanRequestAdsFromUmp() {
        return consentInformation == null || consentInformation.canRequestAds();
    }

    public boolean MoneyBoostIsPrivacyOptionsRequired() {
        return consentInformation != null
                && consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public void MoneyBoostShowPrivacyOptionsForm(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        UserMessagingPlatform.showPrivacyOptionsForm(
                activity,
                formError -> MoneyBoostApplyPrivacySettings(activity, false)
        );
    }

    /** 非 GDPR 或用户已明确同意个性化时为 true。 */
    private boolean MoneyBoostHasPersonalizedConsentFromUmp() {
        if (consentInformation == null) {
            return true;
        }
        int status = consentInformation.getConsentStatus();
        return status == ConsentInformation.ConsentStatus.NOT_REQUIRED
                || status == ConsentInformation.ConsentStatus.OBTAINED;
    }

    /**
     * @param umpPermissiveFallback UMP 拉取失败时沿用宽松策略，避免非 GDPR 地区因网络问题阻断广告。
     */
    private void MoneyBoostApplyPrivacySettings(Activity activity, boolean umpPermissiveFallback) {
        boolean canRequestAds = umpPermissiveFallback || MoneyBoostCanRequestAdsFromUmp();
        boolean personalizedAllowed = umpPermissiveFallback || MoneyBoostHasPersonalizedConsentFromUmp();
        MoneyBoostFirebaseManager.instance().MoneyBoostApplyConsentFromUmp(canRequestAds, personalizedAllowed);
        int status = consentInformation != null
                ? consentInformation.getConsentStatus()
                : ConsentInformation.ConsentStatus.UNKNOWN;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====UMP",
                "canRequestAds=" + canRequestAds
                        + " personalized=" + personalizedAllowed
                        + " status=" + status
                        + " fallback=" + umpPermissiveFallback);
    }

    /** GMA Next-Gen initializes on a worker thread; adapters and UI stay on the main thread. */
    private void MoneyBoostInitGmaSdk(Activity activity, String admobAppId,
                                     boolean umpPermissiveFallback, int generation) {
        if (!MoneyBoostIsCurrentInit(generation)) return;
        try {
            MoneyBoostApplyPrivacySettings(activity, umpPermissiveFallback);
            if (activity.isFinishing() || activity.isDestroyed()) {
                MoneyBoostCompleteInit(generation, false, "Activity unavailable");
                return;
            }
            new Thread(() -> {
                try {
                    Log.i(INIT_TAG, "GMA initialize started on worker thread");
                    MobileAds.initialize(activity,
                            new InitializationConfig.Builder(admobAppId.trim()).build(),
                            status -> mainHandler.post(() -> {
                                if (!MoneyBoostIsCurrentInit(generation)) return;
                                try {
                                    if (activity.isFinishing() || activity.isDestroyed()) {
                                        MoneyBoostCompleteInit(generation, false, "Activity unavailable");
                                        return;
                                    }
                                    Log.i(INIT_TAG, "GMA callback received; creating adapters");
                                    MoneyBoostInitAdAdapters(activity);
                                } catch (Exception | LinkageError e) {
                                    Log.e(INIT_TAG, "Adapter setup failed", e);
                                    MoneyBoostCompleteInit(generation, false, e.toString());
                                    return;
                                }
                                // Host callbacks cannot prevent scheduling advertising work.
                                MoneyBoostStartAdTick();
                                mainHandler.post(() -> {
                                    if (!MoneyBoost_isInitSuccess || MoneyBoost_isDestroyed
                                            || generation != MoneyBoost_initGeneration) return;
                                    try {
                                        MoneyBoostScheduleStaggeredAdLoads();
                                        if (MoneyBoost_unityColdSplashPending && !MoneyBoost_coldSplashHandled) {
                                            MoneyBoostTryShowUnityColdSplash(MoneyBoost_unityColdSplashScene);
                                        }
                                        MoneyBoostSyncBannerVisibility();
                                    } catch (Exception | LinkageError e) {
                                        Log.e(INIT_TAG, "Initial ad load failed", e);
                                    }
                                });
                                MoneyBoostCompleteInit(generation, true, "GMA and adapters ready");
                            }));
                } catch (Exception | LinkageError e) {
                    Log.e(INIT_TAG, "GMA initialization failed", e);
                    mainHandler.post(() -> MoneyBoostCompleteInit(generation, false, e.toString()));
                }
            }, "MoneyBoost-GMA-Init").start();
        } catch (Exception | LinkageError e) {
            Log.e(INIT_TAG, "GMA initialization setup failed", e);
            MoneyBoostCompleteInit(generation, false, e.toString());
        }
    }

    private void MoneyBoostStartAdTick() {
        mainHandler.removeCallbacks(adTickRunnable);
        mainHandler.postDelayed(adTickRunnable, AD_TICK_INTERVAL_MS);
    }

    private void MoneyBoostStopAdTick() {
        mainHandler.removeCallbacks(adTickRunnable);
    }

    /** 每 5 秒补货；已在 loading / 已有库存则跳过，避免与失败重试叠加重载。 */
    private void MoneyBoostTickAdReload() {
        if (!MoneyBoost_isAppForeground || MoneyBoost_isDestroyed || !MoneyBoost_isInitSuccess) {
            return;
        }
        if (MoneyBoost_interAdapter != null
                && !MoneyBoost_interAdapter.MoneyBoostIsShowing()
                && !MoneyBoost_interAdapter.MoneyBoostIsLoading()
                && !MoneyBoost_interAdapter.MoneyBoostIsAdAvailable()) {
            MoneyBoost_interAdapter.MoneyBoostLoadInterstitialAd();
        }
        if (MoneyBoost_rewardAdapter != null
                && !MoneyBoost_rewardAdapter.MoneyBoostIsShowing()
                && !MoneyBoost_rewardAdapter.MoneyBoostIsLoading()
                && !MoneyBoost_rewardAdapter.MoneyBoostIsAdAvailable()) {
            MoneyBoost_rewardAdapter.MoneyBoostLoadRewardVideoAd();
        }
        if (MoneyBoost_splashAdapter != null
                && !MoneyBoost_splashAdapter.MoneyBoostIsShowing()
                && !MoneyBoost_splashAdapter.MoneyBoostIsLoading()
                && !MoneyBoost_splashAdapter.MoneyBoostIsReady()) {
            MoneyBoost_splashAdapter.MoneyBoostLoadSplashAd();
        }
        // Banner 失败后无自动退避，靠 tick 补货
        if (MoneyBoost_bannerAdapter != null
                && !MoneyBoost_bannerAdapter.MoneyBoostIsLoading()
                && !MoneyBoost_bannerAdapter.MoneyBoostIsLoaded()) {
            MoneyBoost_bannerAdapter.MoneyBoostLoadBannerView();
        }
    }

    /**
     * 冷启动错峰：Splash 立刻；Inter → Reward → Banner。
     * 低端机再拉长间隔，减轻主线程与网络并发。
     */
    private void MoneyBoostScheduleStaggeredAdLoads() {
        boolean lowEnd = MoneyBoostToolsManager.instance().MoneyBoostIsLowEndDevice();
        long interDelay = lowEnd ? LOAD_DELAY_INTER_LOW_END_MS : LOAD_DELAY_INTER_MS;
        long rewardDelay = lowEnd ? LOAD_DELAY_REWARD_LOW_END_MS : LOAD_DELAY_REWARD_MS;
        long bannerDelay = lowEnd ? LOAD_DELAY_BANNER_LOW_END_MS : LOAD_DELAY_BANNER_MS;

        mainHandler.postDelayed(interLoadRunnable, interDelay);
        mainHandler.postDelayed(rewardLoadRunnable, rewardDelay);
        mainHandler.postDelayed(bannerLoadRunnable, bannerDelay);
        Log.i(INIT_TAG, "Scheduling splash/interstitial/rewarded/banner requests");
        MoneyBoostLoadSplashAd();
    }

    private void MoneyBoostCancelStaggeredAdLoads() {
        mainHandler.removeCallbacks(interLoadRunnable);
        mainHandler.removeCallbacks(rewardLoadRunnable);
        mainHandler.removeCallbacks(bannerLoadRunnable);
    }

    //////////////////////////////////////////////////////////////////////Interstitial////////////////////////////////////////////////////////////////////////////////


    private void MoneyBoostLoadInterstitialAd(){
        if (MoneyBoost_interAdapter == null) {
            return;
        }
        MoneyBoost_interAdapter.MoneyBoostLoadInterstitialAd();
    }

    /** 竞品：全屏广告展示后清掉 CanShowAoaResume。 */
    private void MoneyBoostClearAoaResumeEligibility() {
        MoneyBoost_canShowAoaResume = false;
    }

    private boolean MoneyBoostIsInterFormatReady() {
        return MoneyBoost_interAdapter != null && MoneyBoost_interAdapter.MoneyBoostIsReady();
    }

    ///unity用
    public boolean MoneyBoostIsIntertitialADReady(String scene){
        return MoneyBoostIsInterFormatReady();
    }

    public boolean MoneyBoostIsIntertitialADReadyLog(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("inter_should_show",object.toString());
        } catch (Exception ignored) { }
        return MoneyBoostIsInterFormatReady();
    }

    public void MoneyBoostShowInterstitialUnity(String scene) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> MoneyBoostShowInterstitialUnity(scene));
            return;
        }
        if (!MoneyBoost_needPopAD || !MoneyBoost_isAppForeground || MoneyBoost_isDestroyed
                || MoneyBoost_interstitialShowPending || MoneyBoost_interstitialInSession
                || MoneyBoost_rewardShowPending
                || MoneyBoostIsFullscreenAdShowing() || MoneyBoostIsSplashAdShowing()) return;
        if (!MoneyBoostIsIntertitialADReadyLog(scene) || MoneyBoost_interAdapter == null) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("scene", scene);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("inter_show", object.toString());
        } catch (Exception ignored) { }
        MoneyBoost_interstitialCloseDispatched = false;
        MoneyBoost_interstitialShowPending = true;
        MoneyBoost_interAdapter.MoneyBoostShowInterstitialAd();
    }

    @Override
    public void MoneyBoostOnInterstitialAdClosed() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostOnInterstitialAdClosed);
            return;
        }
        MoneyBoost_interstitialShowPending = false;
        MoneyBoostOnFullscreenAdClosed();
        MoneyBoostClearAoaResumeEligibility();
        MoneyBoost_interstitialInSession = false;
        MoneyBoostDispatchInterstitialCloseCallbackNow();
        MoneyBoostRetryPendingColdSplashIfNeeded();
    }

    @Override
    public void MoneyBoostOnInterstitialAdFailedToShow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostOnInterstitialAdFailedToShow);
            return;
        }
        MoneyBoost_interstitialShowPending = false;
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                "=====MoneyBoostMediatonManager", "===InterFailedToShow");
        MoneyBoostOnFullscreenAdClosed();
        MoneyBoostClearAoaResumeEligibility();
        MoneyBoost_interstitialInSession = false;
        MoneyBoostDispatchInterstitialCloseCallbackNow();
        MoneyBoostRetryPendingColdSplashIfNeeded();
    }

    @Override
    public void MoneyBoostOnInterstitialAdDisplayed() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostOnInterstitialAdDisplayed);
            return;
        }
        MoneyBoost_interstitialShowPending = false;
        if (MoneyBoost_interstitialInSession) {
            return;
        }
        MoneyBoost_interstitialInSession = true;
        MoneyBoost_interstitialCloseDispatched = false;
        MoneyBoostOnFullscreenAdShow();
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_INTERSTITIAL_OPEN");
    }

    @Override
    public void MoneyBoostOnInterstitialAdClicked() {
    }

    //////////////////////////////////////////////////////////////////////RewardedVideo////////////////////////////////////////////////////////////////////////////////

    private void MoneyBoostLoadRewardAd(){
        if (MoneyBoost_rewardAdapter == null) {
            return;
        }
        MoneyBoost_rewardAdapter.MoneyBoostLoadRewardVideoAd();
    }

    public boolean MoneyBoostIsRewardADReady(String scene){
        boolean rewardReady = MoneyBoost_rewardAdapter != null && MoneyBoost_rewardAdapter.MoneyBoostIsReady();
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostMediatonManager",
                "===IsRewardADReady=" + rewardReady);
        return rewardReady;
    }

    public void MoneyBoostShowRewardAdUnity(String scene){
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> MoneyBoostShowRewardAdUnity(scene));
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("reward_should_show",object.toString());
        }catch (Exception e){
        }
        if (!MoneyBoost_needPopAD || !MoneyBoost_isAppForeground || MoneyBoost_isDestroyed
                || MoneyBoost_rewardShowPending || MoneyBoost_rewardVideoInSession
                || MoneyBoost_interstitialShowPending || MoneyBoostIsFullscreenAdShowing()
                || MoneyBoostIsSplashAdShowing()
                || MoneyBoost_rewardAdapter == null || !MoneyBoost_rewardAdapter.MoneyBoostIsReady()) {
            return;
        }
        MoneyBoost_rewardCloseDispatched = false;
        MoneyBoost_rewardShowScene = scene;
        MoneyBoost_rewardShowPending = true;
        MoneyBoost_rewardAdapter.MoneyBoostShowRewardVideoAd();
    }

    @Override
    public void MoneyBoostOnRewardVideoAdClosed() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostOnRewardVideoAdClosed);
            return;
        }
        MoneyBoost_rewardShowPending = false;
        MoneyBoostOnFullscreenAdClosed();
        MoneyBoostClearAoaResumeEligibility();
        MoneyBoost_rewardVideoInSession = false;
        MoneyBoostDispatchRewardCloseCallbacksNow();
        MoneyBoostRetryPendingColdSplashIfNeeded();
    }

    @Override
    public void MoneyBoostOnRewardVideoAdFailedToShow() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostOnRewardVideoAdFailedToShow);
            return;
        }
        MoneyBoost_rewardShowPending = false;
        MoneyBoost_rewardVideoInSession = false;
        MoneyBoostDispatchRewardCloseCallbacksNow();
        MoneyBoostRetryPendingColdSplashIfNeeded();
    }

    @Override
    public void MoneyBoostOnRewardVideoAdDisplayed() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostOnRewardVideoAdDisplayed);
            return;
        }
        if (MoneyBoost_rewardVideoInSession) {
            return;
        }
        MoneyBoost_rewardShowPending = false;
        MoneyBoost_rewardVideoInSession = true;
        MoneyBoost_rewardCompleteDispatched = false;
        MoneyBoost_rewardCloseDispatched = false;
        MoneyBoostOnFullscreenAdShow();
        try {
            JSONObject object = new JSONObject();
            object.put("scene", MoneyBoost_rewardShowScene);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("reward_show", object.toString());
        } catch (Exception ignored) { }
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_REWARD_OPEN");
    }

    @Override
    public void MoneyBoostOnRewardVideoAdClicked() {
    }

    @Override
    public void MoneyBoostOnRewardVideoAdCompleted() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostOnRewardVideoAdCompleted);
            return;
        }
        if (MoneyBoost_rewardCompleteDispatched) {
            return;
        }
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_REWARD_COMPLETE");
        MoneyBoost_rewardCompleteDispatched = true;
    }

    //////////////////////////////////////////////////////////////////////Banner////////////////////////////////////////////////////////////////////////////////

    private void MoneyBoostLoadBannerView(){
        if (MoneyBoost_bannerAdapter == null) {
            return;
        }
        MoneyBoost_bannerAdapter.MoneyBoostLoadBannerView();
    }

    public void MoneyBoostShowBannerView(){
        MoneyBoost_bannerEnabled = true;
        MoneyBoostSyncBannerVisibility();
    }

    public void MoneyBoostHideBannerView(){
        MoneyBoost_bannerEnabled = false;
        MoneyBoostHideCollapsibleBannerView();
        MoneyBoostSyncBannerVisibility();
    }

    /** Banner 加载完成后再按 bannerEnabled / 全屏状态 Sync，避免 Init 前 ShowBanner 永久不显示 */
    public void MoneyBoostOnBannerAdLoaded() {
        MoneyBoostSyncBannerVisibility();
    }

    private void MoneyBoostReconcileFullscreenAdRefCount() {
        if (MoneyBoost_fullscreenAdRefCount <= 0) {
            return;
        }
        if (!MoneyBoostIsFullscreenAdShowing() && !MoneyBoostIsSplashAdShowing()) {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                    "=====MoneyBoostMediatonManager",
                    "===reconcile fullscreenAdRefCount " + MoneyBoost_fullscreenAdRefCount + " -> 0");
            MoneyBoost_fullscreenAdRefCount = 0;
        }
    }

    private boolean MoneyBoostShouldBlockBannerDisplay() {
        if (!MoneyBoost_bannerEnabled || !MoneyBoost_needPopAD || MoneyBoost_isDestroyed) {
            return true;
        }
        if (!MoneyBoost_isAppForeground) {
            return true;
        }
        if (MoneyBoost_fullscreenAdRefCount > 0) {
            return true;
        }
        // 半屏 b2x 居中卡片不挡 Banner
        return MoneyBoostIsFullscreenAdShowing() || MoneyBoostIsSplashAdShowing();
    }

    private void MoneyBoostSyncBannerVisibility() {
        // 全屏广告 dismiss 可能在 GMA 后台线程回调，Banner 显隐必须回主线程
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostSyncBannerVisibility);
            return;
        }
        if (MoneyBoost_bannerAdapter == null) {
            return;
        }
        MoneyBoostReconcileFullscreenAdRefCount();
        boolean blocked = MoneyBoostShouldBlockBannerDisplay();
        boolean collapsibleReady = MoneyBoost_collapsibleActive
                && MoneyBoost_collapsibleAdapter != null && MoneyBoost_collapsibleAdapter.MoneyBoostIsLoaded();
        // Keep the regular banner cached. Only one banner view is visible at a time.
        if (blocked || collapsibleReady) {
            MoneyBoost_bannerAdapter.MoneyBoostHideBannerView();
        } else {
            MoneyBoost_bannerAdapter.MoneyBoostShowBannerView();
        }
        if (MoneyBoost_collapsibleAdapter != null) {
            if (!blocked && collapsibleReady) MoneyBoost_collapsibleAdapter.MoneyBoostShowBannerView();
            else MoneyBoost_collapsibleAdapter.MoneyBoostHideBannerView();
        }
    }

    private void MoneyBoostOnFullscreenAdShow() {
        MoneyBoostHideCollapsibleBannerView();
        MoneyBoost_fullscreenAdRefCount++;
        MoneyBoostSyncBannerVisibility();
    }

    private void MoneyBoostOnFullscreenAdClosed() {
        if (MoneyBoost_fullscreenAdRefCount > 0) {
            MoneyBoost_fullscreenAdRefCount--;
        }
        MoneyBoostSyncBannerVisibility();
    }

    //////////////////////////////////////////////////////////////////////Collapsible Banner////////////////////////////////////////////////////////////////////////////////

    /** Explicit bottom-anchored expansion; prevent duplicate requests while active. */
    public void MoneyBoostShowCollapsibleBannerView(){
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostShowCollapsibleBannerView);
            return;
        }
        if (MoneyBoost_bannerAdapter == null || MoneyBoost_activity == null
                || MoneyBoost_isDestroyed || !MoneyBoost_needPopAD || !MoneyBoost_isAppForeground
                || MoneyBoostIsFullscreenAdShowing() || MoneyBoostIsSplashAdShowing()) {
            return;
        }
        if (MoneyBoost_collapsibleActive) {
            return;
        }
        // Preserve the normal banner so Hide can restore it without a new network load.
        MoneyBoost_collapsibleAdapter = new MoneyBoostBannerAdapter();
        MoneyBoost_collapsibleAdapter.MoneyBoost_activity = MoneyBoost_activity;
        MoneyBoost_collapsibleAdapter.MoneyBoost_ad_unit = MoneyBoost_bannerKey;
        MoneyBoost_collapsibleAdapter.MoneyBoostInitBannerAdapter();
        MoneyBoost_collapsibleActive = true;
        MoneyBoost_bannerEnabled = true;
        try {
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("collapsibleBanner_should_show", null);
        } catch (Exception ignored) { }
        MoneyBoost_collapsibleAdapter.MoneyBoostLoadCollapsibleBannerView();
        MoneyBoostSyncBannerVisibility();
    }

    public void MoneyBoostHideCollapsibleBannerView(){
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostHideCollapsibleBannerView);
            return;
        }
        MoneyBoost_collapsibleActive = false;
        if (MoneyBoost_collapsibleAdapter != null) {
            MoneyBoost_collapsibleAdapter.MoneyBoostOnDestroy();
            MoneyBoost_collapsibleAdapter = null;
        }
        // Keep the persistent banner enabled when the expanded placement ends.
        MoneyBoostSyncBannerVisibility();
    }

    public void MoneyBoostOnBannerAdFailedToLoad(MoneyBoostBannerAdapter adapter) {
        if (adapter == MoneyBoost_collapsibleAdapter) {
            MoneyBoostHideCollapsibleBannerView();
        }
    }

    //////////////////////////////////////////////////////////////////////Splash////////////////////////////////////////////////////////////////////////////////

    /**
     * 冷启动开屏开关：needPopAD + open_ad_startup_on_off。
     * 次数限制由本进程内存 flag {@link #MoneyBoost_coldSplashHandled} 保证（每次冷启动最多 1 次，不落盘）。
     */
    private boolean MoneyBoostCanShowColdStartSplash() {
        return MoneyBoost_needPopAD && MoneyBoostAdConstants.OPEN_AD_STARTUP_ON;
    }

    /**
     * 竞品热启动：open_ad_on_off + open_ad_resume_on_off + CanShowAoaResume。
     * CanShowAoaResume 在真正切后台后置 true，全屏广告关闭后清 false。
     */
    private boolean MoneyBoostCanShowResumeSplash() {
        return MoneyBoost_needPopAD
                && MoneyBoostAdConstants.OPEN_AD_RESUME_ON
                && MoneyBoost_canShowAoaResume;
    }

    private boolean MoneyBoostHasSplashCandidateReady() {
        return MoneyBoost_splashAdapter != null && MoneyBoost_splashAdapter.MoneyBoostIsReady();
    }

    private void MoneyBoostLoadSplashAd() {
        if (MoneyBoost_splashAdapter == null) {
            return;
        }
        MoneyBoost_splashAdapter.MoneyBoostLoadSplashAd();
    }

    private void MoneyBoostNotifySplashOpen() {
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_SPLASH_OPEN");
    }

    private void MoneyBoostNotifySplashClose() {
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_SPLASH_CLOSE");
    }

    private void MoneyBoostDispatchRewardCloseCallbacksNow() {
        if (MoneyBoost_rewardCloseDispatched) {
            return;
        }
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_REWARD_CLOSE");
        MoneyBoost_rewardCloseDispatched = true;
    }

    private void MoneyBoostDispatchInterstitialCloseCallbackNow() {
        if (MoneyBoost_interstitialCloseDispatched) {
            return;
        }
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_INTERSTITIAL_CLOSE");
        MoneyBoost_interstitialCloseDispatched = true;
    }

    /** 激励/插屏/开屏展示中时不播开屏 */
    private boolean MoneyBoostIsBlockingSplash() {
        if (MoneyBoostIsSplashAdShowing()) {
            return true;
        }
        if (MoneyBoost_rewardShowPending || MoneyBoost_rewardVideoInSession
                || MoneyBoost_interstitialShowPending || MoneyBoost_interstitialInSession) {
            return true;
        }
        if (MoneyBoost_interAdapter != null && MoneyBoost_interAdapter.MoneyBoostIsShowing()) {
            return true;
        }
        if (MoneyBoost_rewardAdapter != null && MoneyBoost_rewardAdapter.MoneyBoostIsShowing()) {
            return true;
        }
        return false;
    }

    private void MoneyBoostScheduleSplashWaitTimeout() {
        if (MoneyBoost_coldSplashDeadline == 0L) {
            MoneyBoost_coldSplashDeadline = SystemClock.uptimeMillis() + COLD_SPLASH_WAIT_TIMEOUT_MS;
        }
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.postDelayed(coldSplashTimeoutRunnable,
                Math.max(0L, MoneyBoost_coldSplashDeadline - SystemClock.uptimeMillis()));
    }

    private void MoneyBoostFinishColdStartSplashFlow() {
        boolean notifyClose = !MoneyBoost_coldSplashHandled;
        MoneyBoost_coldSplashDeadline = 0L;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        MoneyBoost_unityColdSplashPending = false;
        MoneyBoost_coldSplashHandled = true;
        MoneyBoost_allowHotStartSplash = true;
        try {
            if (notifyClose) MoneyBoostNotifySplashClose();
            MoneyBoostSyncBannerVisibility();
        } catch (Exception | LinkageError e) {
            Log.e(INIT_TAG, "Finish cold splash failed; game flow remains released", e);
        }
    }

    private boolean MoneyBoostShouldBlockHotStartByPlayingState() {
        return MoneyBoost_isInPlaying;
    }

    private void MoneyBoostFinishHotStartSplashAttemptNotReady() {
        MoneyBoost_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
    }

    private void MoneyBoostScheduleHotStartSplashWaitTimeout() {
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        mainHandler.postDelayed(hotStartSplashTimeoutRunnable, COLD_SPLASH_WAIT_TIMEOUT_MS);
    }

    /** SDK 热启动：回前台时由生命周期触发；仅 AOA 全屏。 */
    private void MoneyBoostTryShowHotStartSplash() {
        if (!MoneyBoost_allowHotStartSplash || !MoneyBoost_needPopAD || MoneyBoost_isDestroyed
                || !MoneyBoost_isAppForeground || !MoneyBoost_wentToBackground) {
            return;
        }
        if (MoneyBoost_unityColdSplashPending || MoneyBoostShouldBlockHotStartByPlayingState()) {
            return;
        }
        if (MoneyBoostIsBlockingSplash()) {
            MoneyBoost_wentToBackground = false;
            return;
        }
        if (!MoneyBoostCanShowResumeSplash()) {
            MoneyBoost_wentToBackground = false;
            return;
        }
        MoneyBoost_wentToBackground = false;
        if (MoneyBoostHasSplashCandidateReady()) {
            MoneyBoostShowStandardSplash(MONEYBOOST_HOT_START_SPLASH_SCENE);
            return;
        }
        MoneyBoost_hotStartSplashPending = true;
        MoneyBoostBeginWaitingForSplashInventory(MONEYBOOST_HOT_START_SPLASH_SCENE);
    }

    private void MoneyBoostTryShowHotStartSplashAfterLoad(String scene) {
        if (!MoneyBoost_hotStartSplashPending || !MoneyBoost_allowHotStartSplash
                || !MoneyBoost_needPopAD || MoneyBoost_isDestroyed) {
            MoneyBoostFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (MoneyBoostIsBlockingSplash() || !MoneyBoostCanShowResumeSplash()) {
            MoneyBoost_wentToBackground = false;
            MoneyBoostFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (!MoneyBoostHasSplashCandidateReady()) {
            MoneyBoostFinishHotStartSplashAttemptNotReady();
            return;
        }
        MoneyBoost_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        MoneyBoostShowStandardSplash(scene != null ? scene : MONEYBOOST_HOT_START_SPLASH_SCENE);
    }

    private void MoneyBoostTryShowUnityColdSplash(String scene) {
        if (!MoneyBoost_unityColdSplashPending || MoneyBoost_coldSplashHandled
                || !MoneyBoost_needPopAD || MoneyBoost_isDestroyed) {
            return;
        }
        if (MoneyBoostIsBlockingSplash()) {
            // 被半屏/插屏挡住时不要结束冷启动，等对方关闭后再试
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                    "=====MoneyBoostMediatonManager",
                    "===ColdSplash blocked, keep pending");
            return;
        }
        if (!MoneyBoostCanShowColdStartSplash()) {
            MoneyBoostFinishColdStartSplashFlow();
            return;
        }
        if (MoneyBoostHasSplashCandidateReady()) {
            MoneyBoostShowStandardSplash(scene != null ? scene : MoneyBoost_unityColdSplashScene);
            return;
        }
        MoneyBoostBeginWaitingForSplashInventory(scene != null ? scene : MoneyBoost_unityColdSplashScene);
    }

    /** 挡住冷启动开屏的全屏/半屏关掉后重试。 */
    private void MoneyBoostRetryPendingColdSplashIfNeeded() {
        if (!MoneyBoost_unityColdSplashPending || MoneyBoost_coldSplashHandled) {
            return;
        }
        mainHandler.post(() -> MoneyBoostTryShowUnityColdSplash(MoneyBoost_unityColdSplashScene));
    }

    private void MoneyBoostResumeSplashOnForeground() {
        if (MoneyBoost_unityColdSplashPending && !MoneyBoost_coldSplashHandled) {
            MoneyBoostTryShowUnityColdSplash(MoneyBoost_unityColdSplashScene);
            return;
        }
        MoneyBoostTryShowHotStartSplash();
    }

    /** 等待后台状态稳定后，再决策热启动开屏。 */
    private void MoneyBoostResumeSplashOnForegroundDeferred() {
        mainHandler.post(this::MoneyBoostResumeSplashOnForeground);
    }

    /** 冷/热启动开屏：标准 App Open（MoneyBoost_SPLASH_ID）。 */
    private void MoneyBoostShowStandardSplash(String scene) {
        if (!MoneyBoost_needPopAD || MoneyBoost_isDestroyed) {
            if (MoneyBoost_unityColdSplashPending) {
                MoneyBoostFinishColdStartSplashFlow();
            }
            MoneyBoostFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (MoneyBoostIsBlockingSplash()) {
            if (MoneyBoost_unityColdSplashPending) {
                // 冷启动排队中被挡住：保留 pending，不 Finish
                MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                        "=====MoneyBoostMediatonManager",
                        "===ShowStandardSplash blocked while cold pending");
                return;
            }
            MoneyBoost_wentToBackground = false;
            MoneyBoostFinishHotStartSplashAttemptNotReady();
            return;
        }
        boolean isCold = MoneyBoost_unityColdSplashPending;
        if (isCold) {
            if (!MoneyBoostCanShowColdStartSplash()) {
                MoneyBoostFinishColdStartSplashFlow();
                return;
            }
        } else if (!MoneyBoostCanShowResumeSplash()) {
            MoneyBoost_wentToBackground = false;
            MoneyBoostFinishHotStartSplashAttemptNotReady();
            return;
        }

        if (!MoneyBoostHasSplashCandidateReady()) {
            if (MoneyBoost_unityColdSplashPending || MoneyBoost_hotStartSplashPending) {
                MoneyBoostBeginWaitingForSplashInventory(scene);
            } else {
                MoneyBoostFinishHotStartSplashAttemptNotReady();
            }
            return;
        }

        try {
            JSONObject object = new JSONObject();
            object.put("scene", scene);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("splash_show", object.toString());
        } catch (Exception ignored) {
        }

        MoneyBoost_unityColdSplashPending = false;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        MoneyBoost_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        MoneyBoost_splashSessionActive = true;
        MoneyBoostClearAoaResumeEligibility();

        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                "=====MoneyBoostMediatonManager", "===ShowSplash AppOpen");
        MoneyBoost_splashAdapter.MoneyBoostShowSplashAd();
    }

    private void MoneyBoostBeginWaitingForSplashInventory(String scene) {
        MoneyBoostLoadSplashAd();
        if (MoneyBoost_unityColdSplashPending) {
            MoneyBoostScheduleSplashWaitTimeout();
        } else if (MoneyBoost_hotStartSplashPending) {
            MoneyBoostScheduleHotStartSplashWaitTimeout();
        }
    }

    private void MoneyBoostFinishSplashSession() {
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        MoneyBoostClearAoaResumeEligibility();
        MoneyBoost_unityColdSplashPending = false;
        MoneyBoost_coldSplashHandled = true;
        MoneyBoost_allowHotStartSplash = true;
        MoneyBoost_hotStartSplashPending = false;
        MoneyBoost_splashSessionActive = false;
        MoneyBoost_splashOpenDispatched = false;
        MoneyBoostSyncBannerVisibility();
    }

    /**
     * Unity 冷启动开屏。理想时机：Init 成功之后；若 Init 前调用会排队，等 Init 后再播。
     * 等待最多 12 秒；失败/超时发 SPLASH_CLOSE，让 Unity 继续加载，迟到广告不再打断游戏。
     */
    public void MoneyBoostShowSplashADWithUnity(String scene) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(() -> MoneyBoostShowSplashADWithUnity(scene));
            return;
        }
        if (MoneyBoost_coldSplashHandled || !MoneyBoost_needPopAD) {
            MoneyBoostFinishColdStartSplashFlow();
            return;
        }
        MoneyBoost_unityColdSplashScene = scene != null ? scene : "launch";
        if (MoneyBoost_initFailed) {
            MoneyBoostFinishColdStartSplashFlow();
            return;
        }
        // Initialization is asynchronous; waiting for an ad must have a bounded deadline.
        if (!MoneyBoost_isInitSuccess || MoneyBoost_splashAdapter == null) {
            MoneyBoost_unityColdSplashPending = true;
            MoneyBoostScheduleSplashWaitTimeout();
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                    "=====MoneyBoostMediatonManager",
                    "===ShowSplash queued until init (isInit=" + MoneyBoost_isInitSuccess + ")");
            return;
        }
        if (MoneyBoost_splashKey == null || MoneyBoost_splashKey.trim().isEmpty()) {
            MoneyBoostFinishColdStartSplashFlow();
            return;
        }
        if (MoneyBoostIsBlockingSplash()) {
            MoneyBoostFinishColdStartSplashFlow();
            return;
        }
        if (!MoneyBoostCanShowColdStartSplash()) {
            MoneyBoostFinishColdStartSplashFlow();
            return;
        }
        MoneyBoost_unityColdSplashPending = true;
        if (MoneyBoostHasSplashCandidateReady()) {
            MoneyBoostShowStandardSplash(MoneyBoost_unityColdSplashScene);
            return;
        }
        MoneyBoostBeginWaitingForSplashInventory(MoneyBoost_unityColdSplashScene);
    }

    public void MoneyBoostCancelSplashADWithUnity() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::MoneyBoostCancelSplashADWithUnity);
            return;
        }
        MoneyBoost_unityColdSplashPending = false;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        // 用户主动进入玩法时，失败/取消回调可能尚未到达；不能让残留的
        // splashSessionActive 或全屏引用继续把 Banner 隐藏起来。
        MoneyBoost_splashSessionActive = false;
        MoneyBoost_splashOpenDispatched = false;
        if (MoneyBoost_fullscreenAdRefCount > 0
                && !MoneyBoostIsFullscreenAdShowing()) {
            MoneyBoost_fullscreenAdRefCount = 0;
        }
        MoneyBoostFinishColdStartSplashFlow();
    }

    /** 兼容 Unity 旧接口；热启动开屏由 SDK 生命周期 onResume 托管。 */
    public void MoneyBoostShowSplashADWithLifeTime() {
    }

    public boolean MoneyBoostIsSplashADReady() {
        return MoneyBoostHasSplashCandidateReady();
    }

    @Override
    public void MoneyBoostOnSplashAdLoaded() {
        if (MoneyBoost_unityColdSplashPending) {
            MoneyBoostTryShowUnityColdSplash(MoneyBoost_unityColdSplashScene);
            return;
        }
        if (MoneyBoost_hotStartSplashPending) {
            MoneyBoostTryShowHotStartSplashAfterLoad(MONEYBOOST_HOT_START_SPLASH_SCENE);
        }
    }

    @Override
    public void MoneyBoostOnSplashAdDisplayed() {
        MoneyBoostOnFullscreenAdShow();
        MoneyBoost_splashOpenDispatched = true;
        MoneyBoostNotifySplashOpen();
    }

    @Override
    public void MoneyBoostOnSplashAdClosed() {
        MoneyBoostOnFullscreenAdClosed();
        if (MoneyBoost_splashOpenDispatched) {
            MoneyBoostNotifySplashClose();
        }
        MoneyBoostFinishSplashSession();
    }

    @Override
    public void MoneyBoostOnSplashAdFailedToShow() {
        MoneyBoost_splashSessionActive = false;
        if (MoneyBoost_splashOpenDispatched) {
            MoneyBoostNotifySplashClose();
            MoneyBoostFinishSplashSession();
            return;
        }
        MoneyBoostFinishHotStartSplashAttemptNotReady();
        if (MoneyBoost_unityColdSplashPending) {
            MoneyBoostFinishColdStartSplashFlow();
        } else if (!MoneyBoost_coldSplashHandled) {
            MoneyBoost_allowHotStartSplash = true;
        }
    }

    public void MoneyBoostReportIsPlaying(boolean isPlaying) {
        MoneyBoost_isInPlaying = isPlaying;
    }

    //////////////////////////////////////////////////////////////////////Lifecycle////////////////////////////////////////////////////////////////////////////////

    public void MoneyBoostOnUserLeaveHint() {
    }

    public void MoneyBoostOnAppStart(Activity activity) {
        MoneyBoost_activity = activity;
        MoneyBoost_isDestroyed = false;
        MoneyBoost_isAppForeground = true;
        if (MoneyBoost_interAdapter != null) {
            MoneyBoost_interAdapter.MoneyBoost_activity = activity;
        }
        if (MoneyBoost_rewardAdapter != null) {
            MoneyBoost_rewardAdapter.MoneyBoost_activity = activity;
        }
        if (MoneyBoost_bannerAdapter != null) {
            MoneyBoost_bannerAdapter.MoneyBoost_activity = activity;
        }
        if (MoneyBoost_collapsibleAdapter != null) {
            MoneyBoost_collapsibleAdapter.MoneyBoost_activity = activity;
        }
        if (MoneyBoost_splashAdapter != null) {
            MoneyBoost_splashAdapter.MoneyBoost_activity = activity;
        }
        if (MoneyBoost_isInitSuccess && !MoneyBoost_isDestroyed) {
            MoneyBoostStartAdTick();
        }
        if (MoneyBoost_sessionLaunchCnt == 0) {
            MoneyBoost_sessionLaunchCnt++;
            return;
        }
        MoneyBoostSyncBannerVisibility();
        MoneyBoostResumeSplashOnForegroundDeferred();
    }

    public void MoneyBoostOnAppResume() {
        MoneyBoost_isAppForeground = true;
        MoneyBoostSyncBannerVisibility();
        if (MoneyBoost_isInitSuccess && !MoneyBoost_isDestroyed) {
            MoneyBoostStartAdTick();
        }
        MoneyBoostResumeSplashOnForegroundDeferred();
    }


    public void MoneyBoostOnAppPause() {
        if (MoneyBoost_isDestroyed) {
            return;
        }
    }

    private void MoneyBoostCancelPendingHotSplashOnBackground() {
        if (!MoneyBoost_hotStartSplashPending) {
            return;
        }
        MoneyBoostFinishHotStartSplashAttemptNotReady();
    }

    private void MoneyBoostCancelPendingColdSplashOnBackground() {
        if (!MoneyBoost_unityColdSplashPending) {
            return;
        }
        MoneyBoost_unityColdSplashPending = false;
        MoneyBoost_coldSplashHandled = true;
        MoneyBoost_allowHotStartSplash = true;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
    }

    private boolean MoneyBoostIsSplashAdShowing() {
        return MoneyBoost_splashSessionActive
                || (MoneyBoost_splashAdapter != null && MoneyBoost_splashAdapter.MoneyBoostIsShowing());
    }

    private boolean MoneyBoostIsFullscreenAdShowing() {
        if (MoneyBoost_splashAdapter != null && MoneyBoost_splashAdapter.MoneyBoostIsShowing()) {
            return true;
        }
        if (MoneyBoost_interAdapter != null && MoneyBoost_interAdapter.MoneyBoostIsShowing()) {
            return true;
        }
        if (MoneyBoost_rewardAdapter != null && MoneyBoost_rewardAdapter.MoneyBoostIsShowing()) {
            return true;
        }
        return false;
    }

    public void MoneyBoostOnAppStop() {
        MoneyBoost_isAppForeground = false;
        MoneyBoost_wentToBackground = true;
        // 竞品 CanShowAoaResume：真正切后台后允许下次回前台热启动开屏
        if (MoneyBoostAdConstants.OPEN_AD_RESUME_ON) {
            MoneyBoost_canShowAoaResume = true;
        }
        MoneyBoostStopAdTick();
        MoneyBoostCancelPendingColdSplashOnBackground();
        MoneyBoostCancelPendingHotSplashOnBackground();
        MoneyBoostSyncBannerVisibility();
    }

    public void MoneyBoostOnAppDestroy() {
        MoneyBoost_interstitialShowPending = false;
        MoneyBoost_interstitialInSession = false;
        MoneyBoost_interstitialCloseDispatched = false;
        MoneyBoost_rewardShowPending = false;
        MoneyBoost_rewardVideoInSession = false;
        MoneyBoost_rewardCloseDispatched = false;
        MoneyBoost_rewardCompleteDispatched = false;
        MoneyBoost_splashSessionActive = false;
        MoneyBoost_splashOpenDispatched = false;
        MoneyBoost_fullscreenAdRefCount = 0;
        MoneyBoost_collapsibleActive = false;
        MoneyBoost_isAppForeground = false;
        MoneyBoost_isDestroyed = true;
        MoneyBoost_isInitializing = false;
        MoneyBoost_coldSplashDeadline = 0L;
        MoneyBoost_initGeneration++;
        if (MoneyBoost_initTimeout != null) mainHandler.removeCallbacks(MoneyBoost_initTimeout);
        MoneyBoost_initTimeout = null;
        List<ADInitListener> listeners = new ArrayList<>(MoneyBoost_initListeners);
        MoneyBoost_initListeners.clear();
        for (ADInitListener listener : listeners) {
            MoneyBoostNotifyInitListener(listener, false, "Activity destroyed");
        }
        MoneyBoost_wentToBackground = false;
        MoneyBoostCancelStaggeredAdLoads();
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        MoneyBoostStopAdTick();
        MoneyBoost_unityColdSplashPending = false;
        if (MoneyBoost_interAdapter != null) {
            MoneyBoost_interAdapter.MoneyBoostOnDestroy();
            MoneyBoost_interAdapter = null;
        }
        if (MoneyBoost_rewardAdapter != null) {
            MoneyBoost_rewardAdapter.MoneyBoostOnDestroy();
            MoneyBoost_rewardAdapter = null;
        }
        if (MoneyBoost_splashAdapter != null) {
            MoneyBoost_splashAdapter.MoneyBoostOnDestroy();
            MoneyBoost_splashAdapter = null;
        }
        if (MoneyBoost_bannerAdapter != null) {
            MoneyBoost_bannerAdapter.MoneyBoostOnDestroy();
            MoneyBoost_bannerAdapter = null;
        }
        if (MoneyBoost_collapsibleAdapter != null) {
            MoneyBoost_collapsibleAdapter.MoneyBoostOnDestroy();
            MoneyBoost_collapsibleAdapter = null;
        }
        MoneyBoost_isInitSuccess = false;
        MoneyBoost_isInitializing = false;
        MoneyBoost_activity = null;
    }

    //////////////////////////////////////////////////////////////////////Unity交互////////////////////////////////////////////////////////////////////////////////

    private void MoneyBoostSendUnityMsg(String gamaObject, String methodName, String data) {
        if ("MoneyBoostCallback".equals(methodName) && MoneyBoost_debugCallbackListener != null && data != null) {
            try {
                MoneyBoost_debugCallbackListener.onCallback(data);
            } catch (Exception | LinkageError e) {
                Log.e(INIT_TAG, "Debug callback failed", e);
            }
        }
        MoneyBoostUnityBridge.send(gamaObject, methodName, data);
    }
    //////////////////////////////////////////////////////////////////////tools////////////////////////////////////////////////////////////////////////////////

    public void MoneyBoostSetUserNoPopAd(){
        if (MoneyBoost_dataPrefs == null && MoneyBoost_activity != null) {
            MoneyBoost_dataPrefs = MoneyBoost_activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
        }
        if (MoneyBoost_dataPrefs != null) {
            MoneyBoost_dataPrefs.edit().putInt("noPop", 1).apply();
        }
        MoneyBoost_needPopAD = false;
        MoneyBoost_bannerEnabled = false;
        //关闭现在的广告
        MoneyBoostHideCollapsibleBannerView();
        MoneyBoostHideBannerView();
    }

    public void MoneyBoostGetUserNoPopAd(){
        if (MoneyBoost_dataPrefs == null && MoneyBoost_activity != null) {
            MoneyBoost_dataPrefs = MoneyBoost_activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
        }
        int launch = MoneyBoost_dataPrefs != null ? MoneyBoost_dataPrefs.getInt("noPop", 0) : 0;
        if (launch != 0){
            MoneyBoost_needPopAD = false;
        }else {
            MoneyBoost_needPopAD = true;
        }
    }

    //////////////////////////////////////////////////////////////////////Debug status (test app) ////////////////////////////////////////////////////////////////

    public boolean MoneyBoostDebugIsCollapsibleReady() {
        return MoneyBoost_collapsibleAdapter != null && MoneyBoost_collapsibleAdapter.MoneyBoostIsLoaded();
    }

    public boolean MoneyBoostDebugIsCollapsibleShowing() {
        return MoneyBoost_collapsibleActive && MoneyBoost_collapsibleAdapter != null && MoneyBoost_collapsibleAdapter.MoneyBoostIsVisible();
    }

    public boolean MoneyBoostDebugIsRewardReady() {
        return MoneyBoostIsRewardADReady("debug");
    }

    public boolean MoneyBoostDebugIsRewardShowing() {
        return MoneyBoost_rewardAdapter != null && MoneyBoost_rewardAdapter.MoneyBoostIsShowing();
    }



    public boolean MoneyBoostDebugIsInterReady() {
        return MoneyBoostIsInterFormatReady();
    }

    public boolean MoneyBoostDebugIsInterShowing() {
        return MoneyBoost_interstitialInSession
                || (MoneyBoost_interAdapter != null && MoneyBoost_interAdapter.MoneyBoostIsShowing());
    }

    public boolean MoneyBoostDebugIsRewardVideoReady() {
        return MoneyBoost_rewardAdapter != null && MoneyBoost_rewardAdapter.MoneyBoostIsReady();
    }

    public boolean MoneyBoostDebugIsSplashReady() {
        return MoneyBoostHasSplashCandidateReady();
    }

    /**
     * Demo / 调试：重置本进程冷启动开屏状态，便于同一次运行内反复测开屏。
     * 正式包不要调用。
     */
    public void MoneyBoostDebugResetColdSplashState() {
        MoneyBoost_coldSplashHandled = false;
        MoneyBoost_unityColdSplashPending = false;
        MoneyBoost_hotStartSplashPending = false;
        MoneyBoost_splashSessionActive = false;
        MoneyBoost_allowHotStartSplash = false;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug(
                "=====MoneyBoostMediatonManager", "===DebugResetColdSplashState");
    }

    public String MoneyBoostDebugSplashStateText() {
        return "SplashReady=" + MoneyBoostHasSplashCandidateReady()
                + " pending=" + MoneyBoost_unityColdSplashPending
                + " handled=" + MoneyBoost_coldSplashHandled
                + " needPop=" + MoneyBoost_needPopAD
                + " init=" + MoneyBoost_isInitSuccess;
    }
}
