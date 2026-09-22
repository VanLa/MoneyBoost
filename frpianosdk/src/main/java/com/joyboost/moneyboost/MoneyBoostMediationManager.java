package com.joyboost.moneyboost;
//import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;

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

public class MoneyBoostMediationManager implements MoneyBoostInterstitialListener, MoneyBoostRewardVideoListener, MoneyBoostSplashListener {

    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private static volatile MoneyBoostMediationManager instance;

    private Activity MoneyBoost_activity;

    private MoneyBoostInterstitialAdapter MoneyBoost_interAdapter;

    private MoneyBoostRewardVideoAdapter MoneyBoost_rewardAdapter;

    private MoneyBoostMRECBannerAdapter MoneyBoost_mrecAdapter;

    private MoneyBoostBannerAdapter MoneyBoost_bannerAdapter;

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

    private String MoneyBoost_interKey = "";
    private String MoneyBoost_rewardKey = "";
    private String MoneyBoost_mrecKey = "";
    private String MoneyBoost_bannerKey = "";
    private String MoneyBoost_splashKey = "";

    private static final long LOAD_DELAY_INTER_MS = 800L;
    private static final long LOAD_DELAY_REWARD_MS = 1200L;
    private static final long LOAD_DELAY_BANNER_MS = 1500L;
    private static final long LOAD_DELAY_MREC_MS = 3000L;
    private static final long LOAD_DELAY_INTER_LOW_END_MS = 1400L;
    private static final long LOAD_DELAY_REWARD_LOW_END_MS = 2000L;
    private static final long LOAD_DELAY_BANNER_LOW_END_MS = 2500L;
    private static final long LOAD_DELAY_MREC_LOW_END_MS = 5000L;
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
    private boolean MoneyBoost_rewardCloseDispatched = false;
    private boolean MoneyBoost_interstitialInSession = false;
    private boolean MoneyBoost_interstitialCloseDispatched = false;

    /** Unity 冷启动开屏请求中（ShowSplashADWithUnity） */
    private boolean MoneyBoost_unityColdSplashPending = false;
    private String MoneyBoost_unityColdSplashScene = "launch";

    private int MoneyBoost_fullscreenAdRefCount = 0;
    /** Unity 已调用 showBanner，后续由 SDK 自行管理 Banner 显示/隐藏 */
    private boolean MoneyBoost_bannerEnabled = false;

    private SharedPreferences MoneyBoost_dataPrefs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mrecHideRunnable = new Runnable() {
        @Override
        public void run() {
            MoneyBoostHideMRECBannerView();
        }
    };
    private final Runnable coldSplashTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!MoneyBoost_unityColdSplashPending) {
                return;
            }
            MoneyBoost_unityColdSplashPending = false;
            MoneyBoost_splashSessionActive = false;
            MoneyBoost_coldSplashHandled = true;
            MoneyBoost_allowHotStartSplash = true;
            // 超时未出开屏时也要恢复 Banner（Unity 常在 Init 前就 ShowBanner）
            MoneyBoostSyncBannerVisibility();
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
            MoneyBoostTickAdReload();
            mainHandler.postDelayed(this, AD_TICK_INTERVAL_MS);
        }
    };
    private final Runnable interLoadRunnable = new Runnable() {
        @Override
        public void run() {
            MoneyBoostLoadInterstitialAd();
        }
    };
    private final Runnable rewardLoadRunnable = new Runnable() {
        @Override
        public void run() {
            MoneyBoostLoadRewardAd();
        }
    };
    private final Runnable bannerLoadRunnable = new Runnable() {
        @Override
        public void run() {
            MoneyBoostLoadBannerView();
        }
    };
    private final Runnable mrecLoadRunnable = new Runnable() {
        @Override
        public void run() {
            MoneyBoostLoadMRECBannerView();
        }
    };

    public interface ADInitListener {
        void onAdInitSuccess();
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

    public void MoneyBoostInit(Activity activity, ADInitListener initListener){
        this.MoneyBoost_activity = activity;
        MoneyBoost_dataPrefs = activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
        MoneyBoostGetUserNoPopAd();

        String admobAppId = "";
        try {
            ApplicationInfo appInfo = activity.getPackageManager()
                    .getApplicationInfo(activity.getPackageName(),
                            PackageManager.GET_META_DATA);
            MoneyBoost_interKey = appInfo.metaData.getString("MoneyBoost_INTERSTITIAL_ID");
            MoneyBoost_rewardKey = appInfo.metaData.getString("MoneyBoost_REWARDED_ID");
            MoneyBoost_splashKey = appInfo.metaData.getString("MoneyBoost_SPLASH_ID");
            MoneyBoost_bannerKey = appInfo.metaData.getString("MoneyBoost_BANNER_ID");
            MoneyBoost_mrecKey = appInfo.metaData.getString("MoneyBoost_MREC_ID");
            admobAppId = appInfo.metaData.getString("com.google.android.gms.ads.APPLICATION_ID");
        }catch (Exception e){
        }

        try {
            ConsentRequestParameters params = new ConsentRequestParameters
                    .Builder()
                    .build();
            consentInformation = UserMessagingPlatform.getConsentInformation(activity);
            String finalAdmobAppId = admobAppId;
            consentInformation.requestConsentInfoUpdate(
                    activity,
                    params,
                    (ConsentInformation.OnConsentInfoUpdateSuccessListener) () -> {
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                                activity,
                                (ConsentForm.OnConsentFormDismissedListener) loadAndShowError -> {
                                    if (loadAndShowError != null) {
                                        Log.w("=====UMP", String.format("%s: %s",
                                                loadAndShowError.getErrorCode(),
                                                loadAndShowError.getMessage()));
                                    }
                                    MoneyBoostInitGmaSdk(activity, initListener, finalAdmobAppId, false);
                                }
                        );
                    },
                    (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                        Log.w("=====UMP", String.format("%s: %s",
                                requestConsentError.getErrorCode(),
                                requestConsentError.getMessage()));
                        MoneyBoostInitGmaSdk(activity, initListener, finalAdmobAppId, true);
                    });
        }catch (Exception e){
            MoneyBoostInitGmaSdk(activity, initListener, admobAppId, true);
        }
    }

    private void MoneyBoostInitAdAdapters(Activity activity) {
        if (MoneyBoost_interAdapter != null) {
            return;
        }
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

        MoneyBoost_mrecAdapter = new MoneyBoostMRECBannerAdapter();
        MoneyBoost_mrecAdapter.MoneyBoost_activity = activity;
        MoneyBoost_mrecAdapter.MoneyBoost_ad_unit = MoneyBoost_mrecKey;
        MoneyBoost_mrecAdapter.MoneyBoostInitBannerAdapter();
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

    /** 初始化 GMA Next-Gen SDK（主线程），完成后再加载广告。 */
    private void MoneyBoostInitGmaSdk(Activity activity, ADInitListener initListener, String admobAppId,
                                     boolean umpPermissiveFallback) {
        if (MoneyBoost_isInitSuccess || MoneyBoost_isInitializing) {
            return;
        }
        MoneyBoostApplyPrivacySettings(activity, umpPermissiveFallback);

        if (admobAppId == null || admobAppId.trim().isEmpty()) {
            Log.e("=====GMA", "com.google.android.gms.ads.APPLICATION_ID not configured, skip MobileAds init");
            MoneyBoost_isInitializing = false;
            MoneyBoost_isInitSuccess = false;
            return;
        }

        MoneyBoost_isInitializing = true;
        final String appId = admobAppId.trim();

        // GMA SDK 的初始化入口必须从主线程调用。后台线程调用时，部分版本会直接
        // 抛出主线程校验异常，或导致初始化回调不再返回，后续广告自然不会发起请求。
        mainHandler.post(() -> {
            if (MoneyBoost_isDestroyed || activity.isFinishing() || activity.isDestroyed()) {
                MoneyBoost_isInitializing = false;
                return;
            }
            try {
                MobileAds.initialize(
                        activity,
                        new InitializationConfig.Builder(appId).build(),
                        initializationStatus -> {
                            mainHandler.post(() -> {
                                if (MoneyBoost_isDestroyed) {
                                    MoneyBoost_isInitializing = false;
                                    return;
                                }
                                MoneyBoostInitAdAdapters(activity);
                                MoneyBoost_isInitSuccess = true;
                                MoneyBoost_isInitializing = false;
                                if (initListener != null) {
                                    initListener.onAdInitSuccess();
                                }
                                MoneyBoostScheduleStaggeredAdLoads();
                                MoneyBoostStartAdTick();
                                // Unity 常在 Init 完成前就调 ShowSplash / ShowBanner：补排队请求
                                if (MoneyBoost_unityColdSplashPending && !MoneyBoost_coldSplashHandled) {
                                    MoneyBoostTryShowUnityColdSplash(MoneyBoost_unityColdSplashScene);
                                }
                                MoneyBoostSyncBannerVisibility();
                            });
                        });
            } catch (Exception e) {
                Log.e("=====GMA", "MobileAds.initialize failed", e);
                MoneyBoost_isInitializing = false;
                MoneyBoost_isInitSuccess = false;
            }
        });
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
     * 冷启动错峰：Splash 立刻；Inter → Reward → Banner → MREC。
     * 低端机再拉长间隔，减轻主线程与网络并发。
     */
    private void MoneyBoostScheduleStaggeredAdLoads() {
        MoneyBoostLoadSplashAd();

        boolean lowEnd = MoneyBoostToolsManager.instance().MoneyBoostIsLowEndDevice();
        long interDelay = lowEnd ? LOAD_DELAY_INTER_LOW_END_MS : LOAD_DELAY_INTER_MS;
        long rewardDelay = lowEnd ? LOAD_DELAY_REWARD_LOW_END_MS : LOAD_DELAY_REWARD_MS;
        long bannerDelay = lowEnd ? LOAD_DELAY_BANNER_LOW_END_MS : LOAD_DELAY_BANNER_MS;
        long mrecDelay = lowEnd ? LOAD_DELAY_MREC_LOW_END_MS : LOAD_DELAY_MREC_MS;

        mainHandler.postDelayed(interLoadRunnable, interDelay);
        mainHandler.postDelayed(rewardLoadRunnable, rewardDelay);
        mainHandler.postDelayed(bannerLoadRunnable, bannerDelay);
        mainHandler.postDelayed(mrecLoadRunnable, mrecDelay);
    }

    private void MoneyBoostCancelStaggeredAdLoads() {
        mainHandler.removeCallbacks(interLoadRunnable);
        mainHandler.removeCallbacks(rewardLoadRunnable);
        mainHandler.removeCallbacks(bannerLoadRunnable);
        mainHandler.removeCallbacks(mrecLoadRunnable);
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
        if (!MoneyBoostIsIntertitialADReadyLog(scene) || MoneyBoost_interAdapter == null) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("scene", scene);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("inter_show", object.toString());
        } catch (Exception ignored) { }
        MoneyBoost_interstitialCloseDispatched = false;
        MoneyBoost_interAdapter.MoneyBoostShowInterstitialAd();
    }

    @Override
    public void MoneyBoostOnInterstitialAdClosed() {
        MoneyBoostOnFullscreenAdClosed();
        MoneyBoostClearAoaResumeEligibility();
        MoneyBoost_interstitialInSession = false;
        MoneyBoostDispatchInterstitialCloseCallbackNow();
        MoneyBoostRetryPendingColdSplashIfNeeded();
    }

    @Override
    public void MoneyBoostOnInterstitialAdFailedToShow() {
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
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("reward_should_show",object.toString());
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("reward_show",object.toString());
        }catch (Exception e){
        }
        if (MoneyBoost_rewardAdapter == null || !MoneyBoost_rewardAdapter.MoneyBoostIsReady()) {
            return;
        }
        MoneyBoost_rewardAdapter.MoneyBoostShowRewardVideoAd();
    }

    @Override
    public void MoneyBoostOnRewardVideoAdClosed() {
        MoneyBoostOnFullscreenAdClosed();
        MoneyBoostClearAoaResumeEligibility();
        MoneyBoost_rewardVideoInSession = false;
        MoneyBoostDispatchRewardCloseCallbacksNow();
    }

    @Override
    public void MoneyBoostOnRewardVideoAdDisplayed() {
        if (MoneyBoost_rewardVideoInSession) {
            return;
        }
        MoneyBoost_rewardVideoInSession = true;
        MoneyBoost_rewardCompleteDispatched = false;
        MoneyBoost_rewardCloseDispatched = false;
        MoneyBoostOnFullscreenAdShow();
        MoneyBoostSendUnityMsg("MoneyBoostADManager", "MoneyBoostCallback", "MoneyBoost_REWARD_OPEN");
    }

    @Override
    public void MoneyBoostOnRewardVideoAdClicked() {
    }

    @Override
    public void MoneyBoostOnRewardVideoAdCompleted() {
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
        if (MoneyBoost_bannerAdapter == null) {
            return;
        }
        MoneyBoost_bannerAdapter.MoneyBoostHideBannerView();
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
        if (!MoneyBoost_bannerEnabled) {
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
        if (MoneyBoostShouldBlockBannerDisplay()) {
            MoneyBoost_bannerAdapter.MoneyBoostHideBannerView();
        } else {
            MoneyBoost_bannerAdapter.MoneyBoostShowBannerView();
        }
    }

    private void MoneyBoostOnFullscreenAdShow() {
        MoneyBoost_fullscreenAdRefCount++;
        MoneyBoostSyncBannerVisibility();
    }

    private void MoneyBoostOnFullscreenAdClosed() {
        if (MoneyBoost_fullscreenAdRefCount > 0) {
            MoneyBoost_fullscreenAdRefCount--;
        }
        MoneyBoostSyncBannerVisibility();
    }

    //////////////////////////////////////////////////////////////////////MREC////////////////////////////////////////////////////////////////////////////////

    private void MoneyBoostLoadMRECBannerView(){
        if (MoneyBoost_mrecAdapter == null) {
            return;
        }
        MoneyBoost_mrecAdapter.MoneyBoostLoadMRECView();
    }

    private void MoneyBoostShowMRECBannerView(int type , boolean needFix){
        if (MoneyBoost_mrecAdapter == null) {
            return;
        }
        if (type != 2 && needFix == false){
            mainHandler.removeCallbacks(mrecHideRunnable);
            mainHandler.postDelayed(mrecHideRunnable, 1500L);
        }
        MoneyBoost_mrecAdapter.MoneyBoostShowMRECView(type);
    }

    private void MoneyBoostHideMRECBannerView(){
        mainHandler.removeCallbacks(mrecHideRunnable);
        if (MoneyBoost_mrecAdapter == null) {
            return;
        }
        MoneyBoost_mrecAdapter.MoneyBoostHideMRECView();
    }

    public void MoneyBoostShowMRECBannerViewPublic(int type, boolean needFix) {
        MoneyBoostShowMRECBannerView(type, needFix);
    }

    public void MoneyBoostHideMRECBannerViewPublic() {
        MoneyBoostHideMRECBannerView();
    }

    //////////////////////////////////////////////////////////////////////Collapsible Banner////////////////////////////////////////////////////////////////////////////////

    /** Requests and displays an AdMob collapsible banner (bottom anchored). */
    public void MoneyBoostShowCollapsibleBannerView(){
        if (MoneyBoost_bannerAdapter == null || MoneyBoost_activity == null) {
            return;
        }
        try {
            MoneyBoostFirebaseManager.instance().MoneyBoostLogFirebaseEvent("collapsibleBanner_should_show", null);
        } catch (Exception ignored) { }
        MoneyBoost_bannerEnabled = true;
        MoneyBoost_bannerAdapter.MoneyBoostLoadCollapsibleBannerView();
        MoneyBoost_bannerAdapter.MoneyBoostShowBannerView();
        // 该入口代表用户已经进入主界面，立即解除可能残留的开屏遮挡状态。
        MoneyBoostSyncBannerVisibility();
    }

    public void MoneyBoostHideCollapsibleBannerView(){
        MoneyBoost_bannerEnabled = false;
        if (MoneyBoost_bannerAdapter != null) {
            MoneyBoost_bannerAdapter.MoneyBoostHideBannerView();
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
        if (MoneyBoost_rewardVideoInSession || MoneyBoost_interstitialInSession) {
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
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.postDelayed(coldSplashTimeoutRunnable, COLD_SPLASH_WAIT_TIMEOUT_MS);
    }

    private void MoneyBoostFinishColdStartSplashFlow() {
        MoneyBoost_unityColdSplashPending = false;
        MoneyBoost_coldSplashHandled = true;
        MoneyBoost_allowHotStartSplash = true;
        MoneyBoostSyncBannerVisibility();
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
     * 注意：不要在 adapter 未就绪时把 coldSplashHandled 置 true，否则冷启动开屏会永久跳过。
     */
    public void MoneyBoostShowSplashADWithUnity(String scene) {
        if (MoneyBoost_coldSplashHandled || !MoneyBoost_needPopAD) {
            MoneyBoostFinishColdStartSplashFlow();
            return;
        }
        MoneyBoost_unityColdSplashScene = scene != null ? scene : "launch";
        // 未 Init / 无 adapter：只排队，等 MoneyBoostInit 完成后再试
        if (!MoneyBoost_isInitSuccess || MoneyBoost_splashAdapter == null) {
            MoneyBoost_unityColdSplashPending = true;
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
        if (MoneyBoost_mrecAdapter != null) {
            MoneyBoost_mrecAdapter.MoneyBoost_activity = activity;
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
        if (MoneyBoost_wentToBackground) {
            MoneyBoostHideMRECBannerView();
        } else if (MoneyBoost_mrecAdapter != null) {
            MoneyBoost_mrecAdapter.MoneyBoostResumeAutoRefresh();
        }
        if (MoneyBoost_isInitSuccess && !MoneyBoost_isDestroyed) {
            MoneyBoostStartAdTick();
        }
        MoneyBoostResumeSplashOnForegroundDeferred();
    }

    private void MoneyBoostHideMrecOnBackgroundIfNeeded() {
        if (MoneyBoost_mrecAdapter == null) {
            return;
        }
        if (MoneyBoost_mrecAdapter.MoneyBoostIsVisible() || MoneyBoost_mrecAdapter.MoneyBoostIsShowRequested()) {
            MoneyBoostHideMRECBannerView();
        }
    }

    public void MoneyBoostOnAppPause() {
        if (MoneyBoost_isDestroyed) {
            return;
        }
        MoneyBoostHideMrecOnBackgroundIfNeeded();
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
        mainHandler.removeCallbacks(mrecHideRunnable);
        MoneyBoostCancelPendingColdSplashOnBackground();
        MoneyBoostCancelPendingHotSplashOnBackground();
        MoneyBoostSyncBannerVisibility();
        MoneyBoostHideMrecOnBackgroundIfNeeded();
    }

    public void MoneyBoostOnAppDestroy() {
        MoneyBoost_isAppForeground = false;
        MoneyBoost_isDestroyed = true;
        MoneyBoost_wentToBackground = false;
        mainHandler.removeCallbacks(mrecHideRunnable);
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
        if (MoneyBoost_mrecAdapter != null) {
            MoneyBoost_mrecAdapter.MoneyBoostOnDestroy();
            MoneyBoost_mrecAdapter = null;
        }
        if (MoneyBoost_bannerAdapter != null) {
            MoneyBoost_bannerAdapter.MoneyBoostOnDestroy();
            MoneyBoost_bannerAdapter = null;
        }
        MoneyBoost_isInitSuccess = false;
        MoneyBoost_isInitializing = false;
        MoneyBoost_activity = null;
    }

    //////////////////////////////////////////////////////////////////////Unity交互////////////////////////////////////////////////////////////////////////////////

    private void MoneyBoostSendUnityMsg(String gamaObject, String methodName, String data) {
        if ("MoneyBoostCallback".equals(methodName) && MoneyBoost_debugCallbackListener != null && data != null) {
            MoneyBoost_debugCallbackListener.onCallback(data);
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
        MoneyBoostHideBannerView();
        MoneyBoostHideMRECBannerView();
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
        return MoneyBoost_bannerAdapter != null && MoneyBoost_bannerAdapter.MoneyBoostIsLoaded();
    }

    public boolean MoneyBoostDebugIsCollapsibleShowing() {
        return MoneyBoost_bannerAdapter != null && MoneyBoost_bannerAdapter.MoneyBoostIsVisible();
    }

    public boolean MoneyBoostDebugIsRewardReady() {
        return MoneyBoostIsRewardADReady("debug");
    }

    public boolean MoneyBoostDebugIsRewardShowing() {
        return MoneyBoost_rewardAdapter != null && MoneyBoost_rewardAdapter.MoneyBoostIsShowing();
    }

    public boolean MoneyBoostDebugIsMrecReady() {
        return MoneyBoost_mrecAdapter != null && MoneyBoost_mrecAdapter.MoneyBoostIsLoaded();
    }

    public boolean MoneyBoostDebugIsMrecShowing() {
        return MoneyBoost_mrecAdapter != null && MoneyBoost_mrecAdapter.MoneyBoostIsVisible();
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
