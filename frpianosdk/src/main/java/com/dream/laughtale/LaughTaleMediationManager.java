package com.dream.laughtale;
//import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.applovin.sdk.AppLovinMediationProvider;
import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;


import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

public class LaughTaleMediationManager implements LaughTaleInterstitialListener, LaughTaleRewardVideoListener, LaughTaleSplashListener, LaughTaleNativeListener {

    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private static LaughTaleMediationManager instance;

    private Activity LaughTale_activity;

    private LaughTaleInterstitialAdapter LaughTale_interAdapter;

    private LaughTaleRewardVideoAdapter LaughTale_rewardAdapter;

    private LaughTaleSplashAdapter LaughTale_splashAdapter;

    private LaughTaleMRECBannerAdapter LaughTale_mrecAdapter;

    private LaughTaleBannerAdapter LaughTale_bannerAdapter;

    private final List<LaughTaleNativeAdapter> LaughTale_nativeAdapterList = new ArrayList<>();

    // 开屏 CD：距上次重置须满 30s；激励/插屏/Native 等全屏广告关闭后会重置
    private long LaughTale_mSplashADInterval = 1000 * 30;
    // 上次闪屏显示广告的时间
    private long LaughTale_mSplashLastADTime = 0;

    private long LaughTale_sessionLaunchCnt = 0;

    private boolean LaughTale_isInitSuccess = false;
    private boolean LaughTale_isInitializing = false;

    private String LaughTale_interKey = "";
    private String LaughTale_rewardKey = "";
    private String LaughTale_splashKey = "";
    private String LaughTale_mrecKey = "";
    private String LaughTale_bannerKey = "";
    private String LaughTale_nativeKey = "";

    private static final long LOAD_DELAY_BANNER_MS = 1500L;
    private static final long LOAD_DELAY_MREC_MS = 3000L;
    private static final long LOAD_DELAY_BANNER_LOW_END_MS = 2500L;
    private static final long LOAD_DELAY_MREC_LOW_END_MS = 5000L;
    private static final long AD_TICK_INTERVAL_MS = 5000L;
    private static final long COLD_SPLASH_WAIT_TIMEOUT_MS = 10000L;

    private int LaughTale_cachedPValueDayOfYear = -1;
    private double LaughTale_cachedPValue = 0.0;

    private boolean LaughTale_needPopAD = true;

    private boolean LaughTale_isInPlaying = false;

    private boolean LaughTale_isAppForeground = true;
    private boolean LaughTale_isDestroyed = false;

    /** 当前激励位用 Native 顶替展示时，Unity 仍走 LaughTale_REWARD_* 回调 */
    private boolean LaughTale_showingRewardAsNative = false;
    private LaughTaleNativeAdapter LaughTale_rewardNativeAdapter = null;
    /** REWARD_COMPLETE 已发给 Unity，避免重复 */
    private boolean LaughTale_rewardCompleteDispatched = false;

    /** 开屏 App Open 正在展示或 show 请求已发出 */
    private boolean LaughTale_splashSessionActive = false;
    /** 已向 Unity 发送 SPLASH_OPEN（展示失败时需配对 SPLASH_CLOSE） */
    private boolean LaughTale_splashOpenDispatched = false;
    /** 本进程冷启动开屏已处理（Unity 只应调一次；防止重复走冷启动流程） */
    private boolean LaughTale_coldSplashHandled = false;
    /** 冷启动开屏流程结束后，允许 SDK 在回前台触发热启动开屏 */
    private boolean LaughTale_allowHotStartSplash = false;
    /** 用户已切到后台（onStop），回前台时可尝试热启动开屏 */
    private boolean LaughTale_wentToBackground = false;
    /** 热启动开屏等待 App Open 加载中 */
    private boolean LaughTale_hotStartSplashPending = false;
    private static final String LAUGHTALE_HOT_START_SPLASH_SCENE = "background";

    /** Collapsible 位正在播 Native */
    private boolean LaughTale_showingCollapsibleAsNative = false;

    private boolean LaughTale_rewardVideoInSession = false;
    private boolean LaughTale_rewardCloseDispatched = false;
    private boolean LaughTale_interstitialInSession = false;
    private boolean LaughTale_interstitialCloseDispatched = false;
    private boolean LaughTale_nativeCloseDispatched = false;

    /** Unity 冷启动开屏请求中（ShowSplashADWithUnity） */
    private boolean LaughTale_unityColdSplashPending = false;
    private String LaughTale_unityColdSplashScene = "launch";

    /** Native/Inter/Reward/Splash 展示期间隐藏 Banner，全部关闭后恢复 */
    private int LaughTale_fullscreenAdRefCount = 0;
    /** Unity 已调用 showBanner，后续由 SDK 自行管理 Banner 显示/隐藏 */
    private boolean LaughTale_bannerEnabled = false;

    /** Debug：Collapsible 轮替，连续 4 次 Native 后 1 次 Inter */
    private static final int LAUGHTALE_DEBUG_COLLAPSIBLE_NATIVE_COUNT = 4;
    private static final int LAUGHTALE_DEBUG_COLLAPSIBLE_INTER_COUNT = 1;
    private int LaughTale_debugCollapsibleShowIndex = 0;

    private SharedPreferences LaughTale_dataPrefs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mrecHideRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleHideMRECBannerView();
        }
    };
    private final Runnable coldSplashTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!LaughTale_unityColdSplashPending) {
                return;
            }
            LaughTale_unityColdSplashPending = false;
            if (LaughTale_splashAdapter != null) {
                LaughTale_splashAdapter.LaughTale_autoShow = false;
            }
            LaughTale_splashSessionActive = false;
            LaughTale_coldSplashHandled = true;
            LaughTale_allowHotStartSplash = true;
        }
    };
    private final Runnable hotStartSplashTimeoutRunnable = new Runnable() {
        @Override
        public void run() {
            if (!LaughTale_hotStartSplashPending) {
                return;
            }
            LaughTaleFinishHotStartSplashAttemptNotReady();
        }
    };
    private final Runnable adTickRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleTickAdReload();
            mainHandler.postDelayed(this, AD_TICK_INTERVAL_MS);
        }
    };
    private final Runnable bannerLoadRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleLoadBannerView();
        }
    };
    private final Runnable mrecLoadRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleLoadMRECBannerView();
        }
    };

    public interface ADInitListener {
        void onAdInitSuccess();
    }

    public interface DebugCallbackListener {
        void onCallback(String callback);
    }

    private DebugCallbackListener LaughTale_debugCallbackListener;

    public void LaughTaleSetDebugCallbackListener(DebugCallbackListener listener) {
        LaughTale_debugCallbackListener = listener;
    }

    public static LaughTaleMediationManager getInstance() {
        if (instance == null) {
            instance = new LaughTaleMediationManager();
        }
        return instance;
    }
    private LaughTaleMediationManager() {
    }

    //////////////////////////////////////////////////////////////////////初始化////////////////////////////////////////////////////////////////////////////////

    public void LaughTaleInit(Context context,Activity activity,ADInitListener initListener){
        this.LaughTale_activity = activity;
        LaughTale_dataPrefs = activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
        LaughTaleGetUserNoPopAd();
        LaughTaleBankManager.instance().LaughTaleInitBankManager(activity);

        String sdkKey = "";
        try {
            ApplicationInfo appInfo = activity.getPackageManager()
                    .getApplicationInfo(activity.getPackageName(),
                            PackageManager.GET_META_DATA);
            LaughTale_interKey = appInfo.metaData.getString("LaughTale_INTERSTITIAL_ID");
            LaughTale_rewardKey = appInfo.metaData.getString("LaughTale_REWARDED_ID");
            LaughTale_nativeKey = appInfo.metaData.getString("LaughTale_NATIVE_ID");
            LaughTale_bannerKey = appInfo.metaData.getString("LaughTale_BANNER_ID");
            LaughTale_splashKey = appInfo.metaData.getString("LaughTale_SPLASH_ID");
            LaughTale_mrecKey = appInfo.metaData.getString("LaughTale_MREC_ID");
            sdkKey = appInfo.metaData.getString("applovin.sdk.key");
        }catch (Exception e){
        }

        try {
            ConsentRequestParameters params = new ConsentRequestParameters
                    .Builder()
                    .build();
            consentInformation = UserMessagingPlatform.getConsentInformation(activity);
            String finalSdkKey = sdkKey;
            consentInformation.requestConsentInfoUpdate(
                    activity,
                    params,
                    (ConsentInformation.OnConsentInfoUpdateSuccessListener) () -> {
                        // TODO: Load and show the consent form.
                        UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                                activity,
                                (ConsentForm.OnConsentFormDismissedListener) loadAndShowError -> {
                                    if (loadAndShowError != null) {
                                        // Consent gathering failed.
                                        Log.w("=====UMP", String.format("%s: %s",
                                                loadAndShowError.getErrorCode(),
                                                loadAndShowError.getMessage()));
                                    }
                                    LaughTaleInitMaxAd(activity, initListener, finalSdkKey, false);
                                }
                        );
                    },
                    (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                        // Consent gathering failed.
                        Log.w("=====UMP", String.format("%s: %s",
                                requestConsentError.getErrorCode(),
                                requestConsentError.getMessage()));
                        LaughTaleInitMaxAd(activity, initListener, finalSdkKey, true);
                    });
        }catch (Exception e){
            LaughTaleInitMaxAd(activity, initListener, sdkKey, true);
        }
    }

    private void LaughTaleInitAdAdapters(Activity activity) {
        if (LaughTale_interAdapter != null) {
            return;
        }
        String[] LaughTale_nativeIdList = LaughTale_nativeKey.split(";");
        for (String str : LaughTale_nativeIdList){
            LaughTaleNativeAdapter adapter = new LaughTaleNativeAdapter();
            adapter.LaughTale_activity = activity;
            adapter.LaughTale_ad_unit = str;
            adapter.LaughTale_nativeListener = this;
            adapter.LaughTaleInitNativeAdapter();
            LaughTale_nativeAdapterList.add(adapter);
        }

        LaughTale_bannerAdapter = new LaughTaleBannerAdapter();
        LaughTale_bannerAdapter.LaughTale_activity = activity;
        LaughTale_bannerAdapter.LaughTale_ad_unit = LaughTale_bannerKey;
        LaughTale_bannerAdapter.LaughTaleInitBannerAdapter();

        LaughTale_interAdapter = new LaughTaleInterstitialAdapter();
        LaughTale_interAdapter.LaughTale_activity = activity;
        LaughTale_interAdapter.LaughTale_ad_unit = LaughTale_interKey;
        LaughTale_interAdapter.LaughTale_InterstitialListener = this;
        LaughTale_interAdapter.LaughTaleInitInterstitialAdapter();

        LaughTale_rewardAdapter = new LaughTaleRewardVideoAdapter();
        LaughTale_rewardAdapter.LaughTale_activity = activity;
        LaughTale_rewardAdapter.LaughTale_ad_unit = LaughTale_rewardKey;
        LaughTale_rewardAdapter.LaughTale_rewardVideoListener = this;
        LaughTale_rewardAdapter.LaughTaleInitRewardVideoAdapter();

        LaughTale_splashAdapter = new LaughTaleSplashAdapter();
        LaughTale_splashAdapter.LaughTale_ad_unit = LaughTale_splashKey;
        LaughTale_splashAdapter.LaughTale_activity = activity;
        LaughTale_splashAdapter.LaughTale_splashListener = this;
        LaughTale_splashAdapter.LaughTaleInitSplashAdapter();

        LaughTale_mrecAdapter = new LaughTaleMRECBannerAdapter();
        LaughTale_mrecAdapter.LaughTale_activity = activity;
        LaughTale_mrecAdapter.LaughTale_ad_unit = LaughTale_mrecKey;
        LaughTale_mrecAdapter.LaughTaleInitBannerAdapter();
    }

    private boolean LaughTaleCanRequestAdsFromUmp() {
        return consentInformation == null || consentInformation.canRequestAds();
    }

    public boolean LaughTaleIsPrivacyOptionsRequired() {
        return consentInformation != null
                && consentInformation.getPrivacyOptionsRequirementStatus()
                == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED;
    }

    public void LaughTaleShowPrivacyOptionsForm(Activity activity) {
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }
        UserMessagingPlatform.showPrivacyOptionsForm(
                activity,
                formError -> LaughTaleApplyPrivacySettings(activity, false)
        );
    }

    /** 非 GDPR 或用户已明确同意个性化时为 true。 */
    private boolean LaughTaleHasPersonalizedConsentFromUmp() {
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
    private void LaughTaleApplyPrivacySettings(Activity activity, boolean umpPermissiveFallback) {
        boolean canRequestAds = umpPermissiveFallback || LaughTaleCanRequestAdsFromUmp();
        boolean personalizedAllowed = umpPermissiveFallback || LaughTaleHasPersonalizedConsentFromUmp();
        LaughTaleFirebaseManager.instance().LaughTaleApplyConsentFromUmp(canRequestAds, personalizedAllowed);
        AppLovinPrivacySettings.setHasUserConsent(personalizedAllowed, activity);
        int status = consentInformation != null
                ? consentInformation.getConsentStatus()
                : ConsentInformation.ConsentStatus.UNKNOWN;
        boolean doNotSell = false;
        AppLovinPrivacySettings.setDoNotSell(doNotSell, activity);
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====UMP",
                "canRequestAds=" + canRequestAds
                        + " personalized=" + personalizedAllowed
                        + " doNotSell=" + doNotSell
                        + " status=" + status
                        + " fallback=" + umpPermissiveFallback);
    }

    private void LaughTaleInitMaxAd(Activity activity, ADInitListener initListener, String sdkKey, boolean umpPermissiveFallback) {
        if (LaughTale_isInitSuccess || LaughTale_isInitializing) {
            return;
        }
        LaughTale_isInitializing = true;
        LaughTaleApplyPrivacySettings(activity, umpPermissiveFallback);
        LaughTaleInitAdAdapters(activity);
        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(sdkKey, activity)
                .setMediationProvider(AppLovinMediationProvider.MAX)
                .build();
        AppLovinSdk.getInstance(LaughTale_activity).initialize(initConfig, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration configuration) {
                initListener.onAdInitSuccess();
                LaughTale_isInitSuccess = true;
                LaughTaleScheduleStaggeredAdLoads();
                LaughTaleStartAdTick();
                if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
                    mainHandler.postDelayed(() -> {
                        if (LaughTale_activity == null || LaughTale_activity.isFinishing()
                                || LaughTale_activity.isDestroyed()) {
                            return;
                        }
                        AppLovinSdk.getInstance(LaughTale_activity).showMediationDebugger();
                    }, 30000L);
                }
            }
        });
    }

    private void LaughTaleStartAdTick() {
        mainHandler.removeCallbacks(adTickRunnable);
        mainHandler.postDelayed(adTickRunnable, AD_TICK_INTERVAL_MS);
    }

    private void LaughTaleStopAdTick() {
        mainHandler.removeCallbacks(adTickRunnable);
    }

    /** 竞品对齐：每 5 秒检查各广告位库存，未加载则补货。 */
    private void LaughTaleTickAdReload() {
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            adapter.LaughTaleTickReload();
        }
        if (LaughTale_interAdapter != null
                && !LaughTale_interAdapter.LaughTaleIsShowing()
                && LaughTale_interAdapter.LaughTaleGetADPrice() <= 0) {
            LaughTale_interAdapter.LaughTaleLoadInterstitialAd();
        }
        if (LaughTale_rewardAdapter != null
                && !LaughTale_rewardAdapter.LaughTaleIsShowing()
                && LaughTale_rewardAdapter.LaughTaleGetADPrice() <= 0) {
            LaughTale_rewardAdapter.LaughTaleLoadRewardVideoAd();
        }
        if (LaughTale_splashAdapter != null
                && !LaughTale_splashAdapter.LaughTaleIsShowing()
                && !LaughTale_splashAdapter.LaughTaleIsADReady()) {
            LaughTale_splashAdapter.LaughTaleLoadSplashAD();
        }
    }

    /** 按收入占比分阶段加载：Native/Splash 优先，Banner/MREC 延后。 */
    private void LaughTaleScheduleStaggeredAdLoads() {
        LaughTaleLoadNativeView();
        LaughTaleLoadSplashAD();
        LaughTaleLoadInterstitialAd();
        LaughTaleLoadRewardAd();

        boolean lowEnd = LaughTaleToolsManager.instance().LaughTaleIsLowEndDevice();
        long bannerDelay = lowEnd ? LOAD_DELAY_BANNER_LOW_END_MS : LOAD_DELAY_BANNER_MS;
        long mrecDelay = lowEnd ? LOAD_DELAY_MREC_LOW_END_MS : LOAD_DELAY_MREC_MS;
        mainHandler.postDelayed(bannerLoadRunnable, bannerDelay);
        mainHandler.postDelayed(mrecLoadRunnable, mrecDelay);
    }

    //////////////////////////////////////////////////////////////////////Interstitial////////////////////////////////////////////////////////////////////////////////


    private void LaughTaleLoadInterstitialAd(){
        if (LaughTale_interAdapter == null) {
            return;
        }
        LaughTale_interAdapter.LaughTaleLoadInterstitialAd();
    }

    ///unity用
    public boolean LaughTaleIsIntertitialADReady(String scene){
        if (LaughTale_interAdapter == null) {
            return false;
        }
        if (LaughTale_interAdapter.LaughTaleGetADPrice() > 0) {
            return true;
        }
        return false;
    }

    public boolean LaughTaleIsIntertitialADReadyLog(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("inter_should_show",object.toString());
        }catch (Exception e){

        }
        if (LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleGetADPrice() > 0) {
            return true;
        }
        return false;
    }

    public void LaughTaleShowInterstitialUnity(String scene) {
        if (LaughTaleIsIntertitialADReadyLog(scene)) {
            try {
                JSONObject object = new JSONObject();
                object.put("scene", scene);
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("inter_show", object.toString());
            } catch (Exception e) { }
            LaughTale_interAdapter.LaughTaleShowInterstitialAd();
        }
    }


    @Override
    public void LaughTaleOnInterstitialAdClosed() {
        LaughTaleOnFullscreenAdClosed();
        LaughTaleMarkSplashCooldownStarted();
        LaughTale_interstitialInSession = false;
        LaughTaleDispatchInterstitialCloseCallbackNow();
    }

    @Override
    public void LaughTaleOnInterstitialAdDisplayed() {
        if (LaughTale_interstitialInSession) {
            return;
        }
        LaughTale_interstitialInSession = true;
        LaughTale_interstitialCloseDispatched = false;
        LaughTaleOnFullscreenAdShow();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_OPEN");
    }

    @Override
    public void LaughTaleOnInterstitialAdClicked() {
    }

    //////////////////////////////////////////////////////////////////////RewardedVideo////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadRewardAd(){
        if (LaughTale_rewardAdapter == null) {
            return;
        }
        LaughTale_rewardAdapter.LaughTaleLoadRewardVideoAd();
    }

    public boolean LaughTaleIsRewardADReady(String scene){
        double rewardPrice = LaughTale_rewardAdapter != null ? LaughTale_rewardAdapter.LaughTaleGetADPrice() : 0.0;
        LaughTaleNativeAdapter bestNativeForReady = LaughTaleGetBestNativeAdapter();
        double bestNativePrice = bestNativeForReady != null ? bestNativeForReady.LaughTaleGetADPrice() : 0.0;
        boolean rewardReady = rewardPrice > 0;
        boolean nativeReady = bestNativeForReady != null && bestNativePrice > 0;
        if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
            if (rewardReady || nativeReady) {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                        "===IsRewardADReady(debug) reward=" + rewardPrice + " native=" + bestNativePrice);
                return true;
            }
        } else if (LaughTaleShouldShowNativeForReward(rewardPrice, bestNativePrice)) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===IsRewardADReady(native) reward=" + rewardPrice + " native=" + bestNativePrice);
            return true;
        } else if (rewardReady) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===IsRewardADReady(reward) price=" + rewardPrice);
            return true;
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===IsRewardADNotReady");
        return false;
    }

    private void LaughTaleShowRewardAsNative(LaughTaleNativeAdapter bestNativeAdapter) {
        LaughTale_showingRewardAsNative = true;
        LaughTale_rewardNativeAdapter = bestNativeAdapter;
        LaughTaleCloseOtherOpenNativeAds(bestNativeAdapter);
        bestNativeAdapter.LaughTaleShowNativeAd();
    }

    private void LaughTaleShowRewardVideoAdInternal() {
        if (LaughTale_rewardAdapter == null) {
            return;
        }
        LaughTale_showingRewardAsNative = false;
        LaughTale_rewardAdapter.LaughTaleShowRewardVideoAd();
    }

    private void LaughTaleDecideAndShowRewardAd(double rewardPrice, LaughTaleNativeAdapter bestNativeAdapter, double bestNativePrice) {
        boolean rewardReady = rewardPrice > 0;
        boolean nativeReady = bestNativeAdapter != null && bestNativePrice > 0;
        if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
            if (rewardReady && nativeReady) {
                if (new Random().nextBoolean()) {
                    LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                            "===ShowRewardAsNative(debug random) reward=" + rewardPrice + " native=" + bestNativePrice);
                    LaughTaleShowRewardAsNative(bestNativeAdapter);
                } else {
                    LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                            "===ShowRewardVideo(debug random) reward=" + rewardPrice + " native=" + bestNativePrice);
                    LaughTaleShowRewardVideoAdInternal();
                }
            } else if (nativeReady) {
                LaughTaleShowRewardAsNative(bestNativeAdapter);
            } else if (rewardReady) {
                LaughTaleShowRewardVideoAdInternal();
            }
        } else if (LaughTaleShouldShowNativeForReward(rewardPrice, bestNativePrice)) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===ShowRewardAsNative reward=" + rewardPrice + " native=" + bestNativePrice);
            LaughTaleShowRewardAsNative(bestNativeAdapter);
        } else if (rewardReady) {
            LaughTaleShowRewardVideoAdInternal();
        }
    }

    public void LaughTaleShowRewardAdUnity(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("reward_should_show",object.toString());
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("reward_show",object.toString());
        }catch (Exception e){
        }
        double rewardPrice = LaughTale_rewardAdapter != null ? LaughTale_rewardAdapter.LaughTaleGetADPrice() : 0.0;
        LaughTaleNativeAdapter bestNativeAdapter = LaughTaleGetBestNativeAdapter();
        double bestNativePrice = bestNativeAdapter != null ? bestNativeAdapter.LaughTaleGetADPrice() : 0.0;
        LaughTaleDecideAndShowRewardAd(rewardPrice, bestNativeAdapter, bestNativePrice);
    }

    @Override
    public void LaughTaleOnRewardVideoAdClosed() {
        LaughTaleOnFullscreenAdClosed();
        LaughTaleMarkSplashCooldownStarted();
        LaughTale_rewardVideoInSession = false;
        LaughTaleDispatchRewardCloseCallbacksNow();
    }

    @Override
    public void LaughTaleOnRewardVideoAdDisplayed() {
        if (LaughTale_rewardVideoInSession) {
            return;
        }
        LaughTale_rewardVideoInSession = true;
        LaughTale_rewardCompleteDispatched = false;
        LaughTale_rewardCloseDispatched = false;
        LaughTaleOnFullscreenAdShow();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_OPEN");
    }

    @Override
    public void LaughTaleOnRewardVideoAdClicked() {
    }

    @Override
    public void LaughTaleOnRewardVideoAdCompleted() {
        if (LaughTale_rewardCompleteDispatched) {
            return;
        }
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_COMPLETE");
        LaughTale_rewardCompleteDispatched = true;
    }

    //////////////////////////////////////////////////////////////////////Banner////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadBannerView(){
        if (LaughTale_bannerAdapter == null) {
            return;
        }
        LaughTale_bannerAdapter.LaughTaleLoadBannerView();
    }

    public void LaughTaleShowBannerView(){
        LaughTale_bannerEnabled = true;
        LaughTaleSyncBannerVisibility();
    }

    public void LaughTaleHideBannerView(){
        if (LaughTale_bannerAdapter == null) {
            return;
        }
        LaughTale_bannerAdapter.LaughTaleHideBannerView();
    }

    private void LaughTaleReconcileFullscreenAdRefCount() {
        if (LaughTale_fullscreenAdRefCount <= 0) {
            return;
        }
        if (!LaughTaleIsFullscreenAdShowing() && !LaughTaleIsSplashAdShowing()) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=====LaughTaleMediatonManager",
                    "===reconcile fullscreenAdRefCount " + LaughTale_fullscreenAdRefCount + " -> 0");
            LaughTale_fullscreenAdRefCount = 0;
        }
    }

    private boolean LaughTaleShouldBlockBannerDisplay() {
        if (!LaughTale_bannerEnabled) {
            return true;
        }
        if (!LaughTale_isAppForeground) {
            return true;
        }
        if (LaughTale_fullscreenAdRefCount > 0) {
            return true;
        }
        return LaughTaleIsFullscreenAdShowing() || LaughTaleIsSplashAdShowing();
    }

    private void LaughTaleSyncBannerVisibility() {
        if (LaughTale_bannerAdapter == null) {
            return;
        }
        LaughTaleReconcileFullscreenAdRefCount();
        if (LaughTaleShouldBlockBannerDisplay()) {
            LaughTale_bannerAdapter.LaughTaleHideBannerView();
        } else {
            LaughTale_bannerAdapter.LaughTaleShowBannerView();
        }
    }

    private void LaughTaleOnFullscreenAdShow() {
        LaughTale_fullscreenAdRefCount++;
        LaughTaleSyncBannerVisibility();
    }

    private void LaughTaleOnFullscreenAdClosed() {
        if (LaughTale_fullscreenAdRefCount > 0) {
            LaughTale_fullscreenAdRefCount--;
        }
        LaughTaleSyncBannerVisibility();
    }

    //////////////////////////////////////////////////////////////////////MREC////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadMRECBannerView(){
        if (LaughTale_mrecAdapter == null) {
            return;
        }
        LaughTale_mrecAdapter.LaughTaleLoadMRECView();
    }

    private void LaughTaleShowMRECBannerView(int type , boolean needFix){
        if (LaughTale_mrecAdapter == null) {
            return;
        }
        if (type != 2 && needFix == false){
            mainHandler.removeCallbacks(mrecHideRunnable);
            mainHandler.postDelayed(mrecHideRunnable, 1500L);
        }
        LaughTale_mrecAdapter.LaughTaleShowMRECView(type);
    }

    private void LaughTaleHideMRECBannerView(){
        mainHandler.removeCallbacks(mrecHideRunnable);
        if (LaughTale_mrecAdapter == null) {
            return;
        }
        LaughTale_mrecAdapter.LaughTaleHideMRECView();
    }

    public void LaughTaleShowMRECBannerViewPublic(int type, boolean needFix, String reason) {
        LaughTaleShowMRECBannerView(type, needFix);
    }

    public void LaughTaleHideMRECBannerViewPublic(String reason) {
        LaughTaleHideMRECBannerView();
    }

    //////////////////////////////////////////////////////////////////////Native////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadNativeView(){
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            adapter.LaughTaleLoadNativeAdView();
        }
    }

    private boolean LaughTaleIsNativeReady(){
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
            adapter.LaughTaleRefreshStaleInventoryIfNeeded();
            if (adapter.LaughTale_isOpen) {
                continue;
            }
            if (adapter.LaughTaleGetADPrice() > 0 && adapter.LaughTaleCanShowNativeAd()){
                return true;
            }
        }
        return false;
    }

    private LaughTaleNativeAdapter LaughTaleGetBestNativeAdapter() {
        LaughTaleNativeAdapter bestNativeAdapter = null;
        LaughTaleNativeAdapter firstDisplayable = null;
        double maxNativePrice = 0.0;
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            adapter.LaughTaleRefreshStaleInventoryIfNeeded();
            if (adapter.LaughTale_isOpen || !adapter.LaughTaleCanShowNativeAd()) {
                continue;
            }
            if (firstDisplayable == null) {
                firstDisplayable = adapter;
            }
            double price = adapter.LaughTaleGetADPrice();
            if (price > maxNativePrice) {
                maxNativePrice = price;
                bestNativeAdapter = adapter;
            }
        }
        return bestNativeAdapter != null ? bestNativeAdapter : firstDisplayable;
    }

    private boolean LaughTaleHasOpenNativeAd() {
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (adapter.LaughTale_isOpen) {
                return true;
            }
        }
        return false;
    }

    private void LaughTaleCloseAllOpenNativeAds() {
        LaughTaleCloseOtherOpenNativeAds(null);
    }

    private void LaughTaleCloseOtherOpenNativeAds(LaughTaleNativeAdapter except) {
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (adapter == except || !adapter.LaughTale_isOpen) {
                continue;
            }
            adapter.LaughTaleAutoHideNativeAd();
        }
    }

    /** Native 出价高于激励视频时，用 Native 顶替激励位 */
    private boolean LaughTaleShouldShowNativeForReward(double rewardPrice, double nativePrice) {
        return nativePrice > rewardPrice && nativePrice > 0;
    }

    @Override
    public void LaughTaleOnNativeAdClosed(boolean clickedCloseButton, boolean silentClose) {
        LaughTaleOnFullscreenAdClosed();
        if (LaughTale_showingRewardAsNative) {
            LaughTaleNativeAdapter rewardAdapter = LaughTale_rewardNativeAdapter;
            if (silentClose) {
                LaughTale_showingRewardAsNative = false;
                LaughTale_rewardNativeAdapter = null;
                LaughTaleMarkSplashCooldownStarted();
                return;
            }
            LaughTale_showingRewardAsNative = false;
            LaughTale_rewardNativeAdapter = null;
            if (clickedCloseButton && rewardAdapter != null && rewardAdapter.LaughTaleIsCountdownFinished()
                    && !LaughTale_rewardCompleteDispatched) {
                LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_COMPLETE");
                LaughTale_rewardCompleteDispatched = true;
            }
            LaughTaleDispatchRewardCloseCallbacksNow();
            LaughTaleMarkSplashCooldownStarted();
            return;
        }
        LaughTale_showingCollapsibleAsNative = false;
        LaughTaleDispatchNativeCloseCallbackNow();
        LaughTaleMarkSplashCooldownStarted();
    }

    @Override
    public void LaughTaleOnNativeAdDisplayed() {
        LaughTaleOnFullscreenAdShow();
        if (LaughTale_showingRewardAsNative) {
            LaughTale_rewardCompleteDispatched = false;
            LaughTale_rewardCloseDispatched = false;
            LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_OPEN");
            return;
        }
        LaughTale_nativeCloseDispatched = false;
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_NATIVE_OPEN");
    }

    @Override
    public void LaughTaleOnNativeAdClicked() {
    }

    @Override
    public void LaughTaleOnNativeAdLoaded() {
    }

    @Override
    public void LaughTaleOnNativeAdShowSkipped() {
        if (LaughTale_showingRewardAsNative) {
            LaughTale_showingRewardAsNative = false;
            LaughTale_rewardNativeAdapter = null;
        }
        if (LaughTale_showingCollapsibleAsNative) {
            LaughTale_showingCollapsibleAsNative = false;
        }
    }

    /**
     * 按 UTC 时间判断是否为周二、周三、周四
     */
    private boolean isLowRevenueWeekdayUTC(Calendar calendar) {
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);
        return dayOfWeek == Calendar.TUESDAY
                || dayOfWeek == Calendar.WEDNESDAY
                || dayOfWeek == Calendar.THURSDAY;
    }

    /**
     * 获取当前 P 值（按 UTC 自然日缓存）
     */
    private double getPValueByTime() {
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int dayOfYear = calendar.get(Calendar.DAY_OF_YEAR);
        if (dayOfYear != LaughTale_cachedPValueDayOfYear) {
            LaughTale_cachedPValueDayOfYear = dayOfYear;
            if (isLowRevenueWeekdayUTC(calendar)) {
                LaughTale_cachedPValue = LaughTaleFirebaseManager.instance().LaughTale_p_weekday;
            } else {
                LaughTale_cachedPValue = LaughTaleFirebaseManager.instance().LaughTale_p_weekend;
            }
        }
        return LaughTale_cachedPValue;
    }

    private void LaughTaleShowCollapsibleAsNative(LaughTaleNativeAdapter adapter) {
        LaughTale_showingCollapsibleAsNative = true;
        LaughTaleCloseOtherOpenNativeAds(adapter);
        adapter.LaughTaleShowNativeAd();
    }

    private void LaughTaleDecideAndShowCollapsibleAd(LaughTaleNativeAdapter bestNativeAdapter, double interPrice, boolean useBidding) {
        boolean interReady = interPrice > 0;
        boolean nativeReady = bestNativeAdapter != null && bestNativeAdapter.LaughTaleCanShowNativeAd();
        if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
            LaughTaleDecideAndShowCollapsibleAdDebug(bestNativeAdapter, interReady, nativeReady);
            return;
        }
        double maxNativePrice = nativeReady ? bestNativeAdapter.LaughTaleGetADPrice() : 0.0;
        if (useBidding) {
            if (nativeReady && interReady && interPrice > maxNativePrice * getPValueByTime()) {
                LaughTaleCloseAllOpenNativeAds();
                LaughTale_interAdapter.LaughTaleShowInterstitialAd();
            } else if (nativeReady) {
                LaughTaleShowCollapsibleAsNative(bestNativeAdapter);
            } else if (interReady) {
                LaughTaleCloseAllOpenNativeAds();
                LaughTale_interAdapter.LaughTaleShowInterstitialAd();
            }
        } else if (nativeReady) {
            LaughTaleShowCollapsibleAsNative(bestNativeAdapter);
        }
    }

    /**
     * Debug：4 次 Native → 1 次 Inter 循环；当前类型未 ready 时 fallback 到另一类型。
     */
    private void LaughTaleDecideAndShowCollapsibleAdDebug(
            LaughTaleNativeAdapter bestNativeAdapter, boolean interReady, boolean nativeReady) {
        int cycleLength = LAUGHTALE_DEBUG_COLLAPSIBLE_NATIVE_COUNT + LAUGHTALE_DEBUG_COLLAPSIBLE_INTER_COUNT;
        int phase = LaughTale_debugCollapsibleShowIndex % cycleLength;
        LaughTale_debugCollapsibleShowIndex++;
        boolean preferNative = phase < LAUGHTALE_DEBUG_COLLAPSIBLE_NATIVE_COUNT;

        if (preferNative) {
            if (nativeReady && bestNativeAdapter != null) {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                        "===ShowCollapsibleNative(debug cycle " + (phase + 1) + "/" + cycleLength + ")");
                LaughTaleShowCollapsibleAsNative(bestNativeAdapter);
            } else if (interReady) {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                        "===ShowCollapsibleInter(debug fallback, native not ready, cycle " + (phase + 1) + "/" + cycleLength + ")");
                LaughTaleCloseAllOpenNativeAds();
                LaughTale_interAdapter.LaughTaleShowInterstitialAd();
            }
        } else if (interReady) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===ShowCollapsibleInter(debug cycle " + (phase + 1) + "/" + cycleLength + ")");
            LaughTaleCloseAllOpenNativeAds();
            LaughTale_interAdapter.LaughTaleShowInterstitialAd();
        } else if (nativeReady && bestNativeAdapter != null) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===ShowCollapsibleNative(debug fallback, inter not ready, cycle " + (phase + 1) + "/" + cycleLength + ")");
            LaughTaleShowCollapsibleAsNative(bestNativeAdapter);
        }
    }

    private void LaughTaleSmartShowCollapsibleBannerView(boolean needHighValue){
        if (LaughTaleHasOpenNativeAd()) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=====LaughTaleMediatonManager", "===ShowCollapsibleSkipped native already showing");
            return;
        }
        try {
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_should_show",null);
        }catch (Exception e){
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===LaughTaleSmartShowCollapsibleBannerView");
        boolean interReady = LaughTaleIsIntertitialADReady("collapse");
        boolean nativeReady = LaughTaleIsNativeReady();
        if (needHighValue) {
            if (nativeReady || interReady) {
                LaughTaleDecideAndShowCollapsibleAd(
                        LaughTaleGetBestNativeAdapter(),
                        LaughTale_interAdapter != null ? LaughTale_interAdapter.LaughTaleGetADPrice() : 0.0,
                        true);
            }
        } else {
            if (nativeReady || interReady) {
                if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
                    LaughTaleDecideAndShowCollapsibleAdDebug(
                            LaughTaleGetBestNativeAdapter(), interReady, nativeReady);
                } else if (nativeReady) {
                    LaughTaleNativeAdapter bestNativeAdapter = LaughTaleGetBestNativeAdapter();
                    if (bestNativeAdapter != null) {
                        LaughTaleShowCollapsibleAsNative(bestNativeAdapter);
                    }
                }
            }
        }
    }

    public void LaughTaleShowCollapsibleBannerView(boolean needHighValue){
        LaughTaleSmartShowCollapsibleBannerView(needHighValue);
    }

    public void LaughTaleHideCollapsibleBannerView(){
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===LaughTaleHideBannerView");
//        LaughTale_collapsibleBannerAdapter.LaughTaleHideBannerView();
    }


    //////////////////////////////////////////////////////////////////////Splash////////////////////////////////////////////////////////////////////////////////

    private boolean LaughTaleCanShowSplashByInterval() {
        if (LaughTale_mSplashLastADTime == 0) {
            return true;
        }
        return System.currentTimeMillis() - LaughTale_mSplashLastADTime >= LaughTale_mSplashADInterval;
    }

    /** 全屏广告（开屏/Native/插屏/激励）关闭后才开始 30s 开屏 CD。 */
    private void LaughTaleMarkSplashCooldownStarted() {
        LaughTale_mSplashLastADTime = System.currentTimeMillis();
    }

    private double LaughTaleGetSplashAppOpenPrice() {
        if (LaughTale_splashAdapter == null || !LaughTale_splashAdapter.LaughTaleIsADReady()) {
            return 0;
        }
        return LaughTale_splashAdapter.LaughTaleGetADPrice();
    }

    private boolean LaughTaleHasSplashCandidateReady() {
        return LaughTaleGetSplashAppOpenPrice() > 0;
    }

    private void LaughTaleNotifySplashOpen() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_SPLASH_OPEN");
    }

    private void LaughTaleNotifySplashClose() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_SPLASH_CLOSE");
    }

    private void LaughTaleDispatchRewardCloseCallbacksNow() {
        if (LaughTale_rewardCloseDispatched) {
            return;
        }
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_CLOSE");
        LaughTale_rewardCloseDispatched = true;
    }

    private void LaughTaleDispatchInterstitialCloseCallbackNow() {
        if (LaughTale_interstitialCloseDispatched) {
            return;
        }
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_CLOSE");
        LaughTale_interstitialCloseDispatched = true;
    }

    private void LaughTaleDispatchNativeCloseCallbackNow() {
        if (LaughTale_nativeCloseDispatched) {
            return;
        }
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_NATIVE_CLOSE");
        LaughTale_nativeCloseDispatched = true;
    }

    /** 激励/插屏/Native 展示中或开屏展示中时不播开屏 */
    private boolean LaughTaleIsBlockingSplash() {
        if (LaughTaleIsSplashAdShowing()) {
            return true;
        }
        if (LaughTale_showingRewardAsNative || LaughTale_showingCollapsibleAsNative) {
            return true;
        }
        if (LaughTale_rewardVideoInSession || LaughTale_interstitialInSession) {
            return true;
        }
        if (LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleIsShowing()) {
            return true;
        }
        if (LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleIsShowing()) {
            return true;
        }
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (adapter.LaughTale_isOpen) {
                return true;
            }
        }
        return false;
    }

    private void LaughTaleScheduleSplashWaitTimeout() {
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.postDelayed(coldSplashTimeoutRunnable, COLD_SPLASH_WAIT_TIMEOUT_MS);
    }

    private void LaughTaleFinishColdStartSplashFlow() {
        LaughTale_unityColdSplashPending = false;
        LaughTale_coldSplashHandled = true;
        LaughTale_allowHotStartSplash = true;
    }

    private boolean LaughTaleShouldBlockHotStartByPlayingState() {
        return LaughTale_isInPlaying;
    }

    private void LaughTaleFinishHotStartSplashAttemptNotReady() {
        LaughTale_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        if (LaughTale_splashAdapter != null) {
            LaughTale_splashAdapter.LaughTale_autoShow = false;
        }
    }

    private void LaughTaleScheduleHotStartSplashWaitTimeout() {
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        mainHandler.postDelayed(hotStartSplashTimeoutRunnable, COLD_SPLASH_WAIT_TIMEOUT_MS);
    }

    /** SDK 热启动：回前台时由生命周期触发，仅 App Open。 */
    private void LaughTaleTryShowHotStartSplash() {
        if (!LaughTale_allowHotStartSplash || !LaughTale_needPopAD || LaughTale_isDestroyed
                || !LaughTale_isAppForeground || !LaughTale_wentToBackground) {
            return;
        }
        if (LaughTale_unityColdSplashPending || LaughTaleShouldBlockHotStartByPlayingState()) {
            return;
        }
        if (LaughTaleIsBlockingSplash()) {
            LaughTale_wentToBackground = false;
            return;
        }
        if (!LaughTaleCanShowSplashByInterval()) {
            LaughTale_wentToBackground = false;
            return;
        }
        LaughTale_wentToBackground = false;
        if (LaughTaleHasSplashCandidateReady()) {
            LaughTaleShowAppOpenSplash(LAUGHTALE_HOT_START_SPLASH_SCENE);
            return;
        }
        if (LaughTale_splashAdapter == null) {
            return;
        }
        LaughTale_hotStartSplashPending = true;
        LaughTale_splashAdapter.LaughTale_pendingScene = LAUGHTALE_HOT_START_SPLASH_SCENE;
        LaughTale_splashAdapter.LaughTale_autoShow = true;
        LaughTaleLoadSplashAD();
        LaughTaleScheduleHotStartSplashWaitTimeout();
    }

    private void LaughTaleTryShowHotStartSplashAfterLoad(String scene) {
        if (!LaughTale_hotStartSplashPending || !LaughTale_allowHotStartSplash
                || !LaughTale_needPopAD || LaughTale_isDestroyed) {
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (LaughTaleIsBlockingSplash() || !LaughTaleCanShowSplashByInterval()) {
            LaughTale_wentToBackground = false;
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (!LaughTaleHasSplashCandidateReady()) {
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        LaughTale_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        LaughTaleShowAppOpenSplash(scene != null ? scene : LAUGHTALE_HOT_START_SPLASH_SCENE);
    }

    private void LaughTaleTryShowUnityColdSplash(String scene) {
        if (!LaughTale_unityColdSplashPending || LaughTale_coldSplashHandled
                || !LaughTale_needPopAD || LaughTale_isDestroyed) {
            return;
        }
        if (LaughTaleIsBlockingSplash()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        if (!LaughTaleCanShowSplashByInterval()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        if (LaughTaleHasSplashCandidateReady()) {
            LaughTaleShowAppOpenSplash(scene != null ? scene : LaughTale_unityColdSplashScene);
            return;
        }
        if (LaughTale_splashAdapter != null) {
            LaughTale_splashAdapter.LaughTale_pendingScene = scene != null ? scene : LaughTale_unityColdSplashScene;
            LaughTale_splashAdapter.LaughTale_autoShow = true;
            LaughTaleLoadSplashAD();
            LaughTaleScheduleSplashWaitTimeout();
        }
    }

    private void LaughTaleResumeSplashOnForeground() {
        if (LaughTale_unityColdSplashPending && !LaughTale_coldSplashHandled) {
            LaughTaleTryShowUnityColdSplash(LaughTale_unityColdSplashScene);
            return;
        }
        LaughTaleTryShowHotStartSplash();
    }

    /** 等 onPause 里 runOnUiThread 关 Native 的回调跑完，再决策热启动开屏，避免先于 CD 写入触发开屏。 */
    private void LaughTaleResumeSplashOnForegroundDeferred() {
        mainHandler.post(this::LaughTaleResumeSplashOnForeground);
    }

    /** 冷/热启动开屏：仅 App Open，不与 Native 比价。 */
    private void LaughTaleShowAppOpenSplash(String scene) {
        if (!LaughTale_needPopAD || LaughTale_isDestroyed) {
            if (LaughTale_unityColdSplashPending) {
                LaughTaleFinishColdStartSplashFlow();
            }
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (LaughTaleIsBlockingSplash()) {
            if (LaughTale_unityColdSplashPending) {
                LaughTaleFinishColdStartSplashFlow();
            } else {
                LaughTale_wentToBackground = false;
                LaughTaleFinishHotStartSplashAttemptNotReady();
            }
            return;
        }
        if (!LaughTaleCanShowSplashByInterval()) {
            if (LaughTale_unityColdSplashPending) {
                LaughTaleFinishColdStartSplashFlow();
            }
            LaughTale_wentToBackground = false;
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (LaughTale_splashAdapter == null) {
            LaughTaleFinishHotStartSplashAttemptNotReady();
            if (LaughTale_unityColdSplashPending) {
                LaughTaleScheduleSplashWaitTimeout();
            }
            return;
        }
        if (LaughTaleGetSplashAppOpenPrice() <= 0) {
            if (LaughTale_unityColdSplashPending) {
                LaughTale_splashAdapter.LaughTale_autoShow = true;
                LaughTale_splashAdapter.LaughTale_pendingScene = scene;
                LaughTaleLoadSplashAD();
                LaughTaleScheduleSplashWaitTimeout();
            } else {
                LaughTaleFinishHotStartSplashAttemptNotReady();
            }
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("scene", scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("splash_show", object.toString());
        } catch (Exception ignored) {
        }
        LaughTale_unityColdSplashPending = false;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        LaughTale_splashSessionActive = true;
        LaughTale_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        LaughTale_splashAdapter.LaughTaleShowSplashAd(scene);
    }

    private void LaughTaleFinishSplashSession(boolean applyCooldown) {
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        if (applyCooldown) {
            LaughTaleMarkSplashCooldownStarted();
        }
        LaughTale_unityColdSplashPending = false;
        LaughTale_coldSplashHandled = true;
        LaughTale_allowHotStartSplash = true;
        LaughTale_hotStartSplashPending = false;
        LaughTale_splashSessionActive = false;
        LaughTale_splashOpenDispatched = false;
        LaughTaleSyncBannerVisibility();
    }

    @Override
    public void LaughTaleOnSplashAdDisplayFailed() {
        if (LaughTale_splashOpenDispatched) {
            LaughTaleOnFullscreenAdClosed();
            LaughTaleNotifySplashClose();
            LaughTaleFinishSplashSession(true);
            return;
        }
        LaughTale_splashSessionActive = false;
        LaughTaleFinishHotStartSplashAttemptNotReady();
        if (LaughTale_unityColdSplashPending) {
            LaughTaleFinishColdStartSplashFlow();
        } else if (!LaughTale_coldSplashHandled) {
            LaughTale_allowHotStartSplash = true;
        }
        LaughTaleSyncBannerVisibility();
    }

    /** 兼容 Unity 旧接口；热启动开屏由 SDK 生命周期 onResume 托管。 */
    public void LaughTaleShowSplashADWithLifeTime() {
    }

    public boolean LaughTaleIsSplashADReady() {
        return LaughTaleHasSplashCandidateReady();
    }

    /** Unity 冷启动：init 成功后调用一次即可。 */
    public void LaughTaleShowSplashADWithUnity(String scene) {
        if (LaughTale_coldSplashHandled || LaughTale_splashAdapter == null || !LaughTale_needPopAD) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        LaughTale_unityColdSplashScene = scene != null ? scene : "launch";
        if (LaughTaleIsBlockingSplash()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        if (!LaughTaleCanShowSplashByInterval()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        LaughTale_unityColdSplashPending = true;
        if (LaughTaleHasSplashCandidateReady()) {
            LaughTaleShowAppOpenSplash(LaughTale_unityColdSplashScene);
            return;
        }
        LaughTale_splashAdapter.LaughTale_pendingScene = LaughTale_unityColdSplashScene;
        LaughTale_splashAdapter.LaughTale_autoShow = true;
        LaughTaleLoadSplashAD();
        LaughTaleScheduleSplashWaitTimeout();
    }

    public void LaughTaleCancelSplashADWithUnity() {
        if (LaughTale_splashAdapter != null) {
            LaughTale_splashAdapter.LaughTale_autoShow = false;
        }
        LaughTale_unityColdSplashPending = false;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        LaughTaleFinishColdStartSplashFlow();
    }

    @Override
    public void LaughTaleOnSplashAdReadyForAutoShow(String scene) {
        if (LaughTale_unityColdSplashPending) {
            LaughTaleTryShowUnityColdSplash(scene != null ? scene : LaughTale_unityColdSplashScene);
            return;
        }
        if (LaughTale_hotStartSplashPending) {
            LaughTaleTryShowHotStartSplashAfterLoad(scene);
        }
    }

    private void LaughTaleLoadSplashAD() {
        if (LaughTale_splashAdapter == null) {
            return;
        }
        LaughTale_splashAdapter.LaughTaleLoadSplashAD();
    }

    @Override
    public void LaughTaleOnSplashAdClosed() {
        LaughTaleOnFullscreenAdClosed();
        LaughTaleNotifySplashClose();
        LaughTaleFinishSplashSession(true);
    }

    @Override
    public void LaughTaleOnSplashAdDisplayed() {
        LaughTale_splashOpenDispatched = true;
        LaughTaleOnFullscreenAdShow();
        LaughTaleNotifySplashOpen();
    }

    public void LaughTaleReportIsPlaying(boolean isPlaying) {
        LaughTale_isInPlaying = isPlaying;
    }

    //////////////////////////////////////////////////////////////////////Lifecycle////////////////////////////////////////////////////////////////////////////////

    public void LaughTaleOnUserLeaveHint() {
    }

    public void LaughTaleOnAppStart(Activity activity) {
        LaughTale_activity = activity;
        LaughTale_isDestroyed = false;
        LaughTale_isAppForeground = true;
        if (LaughTale_splashAdapter != null) {
            LaughTale_splashAdapter.LaughTale_activity = activity;
        }
        if (LaughTale_interAdapter != null) {
            LaughTale_interAdapter.LaughTale_activity = activity;
        }
        if (LaughTale_rewardAdapter != null) {
            LaughTale_rewardAdapter.LaughTale_activity = activity;
        }
        if (LaughTale_bannerAdapter != null) {
            LaughTale_bannerAdapter.LaughTale_activity = activity;
        }
        if (LaughTale_mrecAdapter != null) {
            LaughTale_mrecAdapter.LaughTale_activity = activity;
        }
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            adapter.LaughTale_activity = activity;
        }
        if (LaughTale_sessionLaunchCnt == 0) {
            LaughTale_sessionLaunchCnt++;
            return;
        }
        LaughTaleSyncBannerVisibility();
        LaughTaleResumeSplashOnForegroundDeferred();
    }

    public void LaughTaleOnAppResume() {
        LaughTale_isAppForeground = true;
        LaughTaleSyncBannerVisibility();
        if (LaughTale_wentToBackground) {
            LaughTaleHideMRECBannerView();
        } else if (LaughTale_mrecAdapter != null) {
            LaughTale_mrecAdapter.LaughTaleResumeAutoRefresh();
        }
        LaughTaleResumeSplashOnForegroundDeferred();
    }

    private void LaughTaleHideMrecOnBackgroundIfNeeded() {
        if (LaughTale_mrecAdapter == null) {
            return;
        }
        if (LaughTale_mrecAdapter.LaughTaleIsVisible() || LaughTale_mrecAdapter.LaughTaleIsShowRequested()) {
            LaughTaleHideMRECBannerView();
        }
    }

    public void LaughTaleOnAppPause() {
        if (LaughTale_isDestroyed) {
            return;
        }
        LaughTaleHideAllNativeAdsOnBackground();
        LaughTaleHideMrecOnBackgroundIfNeeded();
    }

    private void LaughTaleCancelPendingHotSplashOnBackground() {
        if (!LaughTale_hotStartSplashPending) {
            return;
        }
        LaughTaleFinishHotStartSplashAttemptNotReady();
    }

    private void LaughTaleCancelPendingColdSplashOnBackground() {
        if (!LaughTale_unityColdSplashPending) {
            return;
        }
        LaughTale_unityColdSplashPending = false;
        LaughTale_coldSplashHandled = true;
        LaughTale_allowHotStartSplash = true;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        if (LaughTale_splashAdapter != null) {
            LaughTale_splashAdapter.LaughTale_autoShow = false;
        }
    }

    private boolean LaughTaleIsSplashAdShowing() {
        if (LaughTale_splashSessionActive) {
            return true;
        }
        return LaughTale_splashAdapter != null && LaughTale_splashAdapter.LaughTaleIsShowing();
    }

    private boolean LaughTaleIsFullscreenAdShowing() {
        if (LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleIsShowing()) {
            return true;
        }
        if (LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleIsShowing()) {
            return true;
        }
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (adapter.LaughTale_isOpen) {
                return true;
            }
        }
        return false;
    }

    private void LaughTaleHideAllNativeAdsOnBackground() {
        boolean rewardNativeShowing = LaughTale_showingRewardAsNative;
        if (rewardNativeShowing && !LaughTale_rewardCompleteDispatched) {
            LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_COMPLETE");
            LaughTale_rewardCompleteDispatched = true;
        }
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (!adapter.LaughTale_isOpen) {
                continue;
            }
            adapter.LaughTaleCloseNativeAdOnBackground();
        }
        if (rewardNativeShowing) {
            LaughTaleDispatchRewardCloseCallbacksNow();
        }
    }

    public void LaughTaleOnAppStop() {
        LaughTale_isAppForeground = false;
        LaughTale_wentToBackground = true;
        mainHandler.removeCallbacks(mrecHideRunnable);
        LaughTaleCancelPendingColdSplashOnBackground();
        LaughTaleCancelPendingHotSplashOnBackground();
        LaughTaleHideAllNativeAdsOnBackground();
        LaughTaleSyncBannerVisibility();
        LaughTaleHideMrecOnBackgroundIfNeeded();
    }

    public void LaughTaleOnAppDestroy(Activity activity) {
        LaughTale_isAppForeground = false;
        LaughTale_isDestroyed = true;
        LaughTale_wentToBackground = false;
        mainHandler.removeCallbacks(mrecHideRunnable);
        mainHandler.removeCallbacks(bannerLoadRunnable);
        mainHandler.removeCallbacks(mrecLoadRunnable);
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        LaughTaleStopAdTick();
        LaughTale_unityColdSplashPending = false;
        if (LaughTale_activity == activity) {
            LaughTale_activity = null;
        }
    }

    //////////////////////////////////////////////////////////////////////Unity交互////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleSendUnityMsg(String gamaObject, String methodName, String data) {
        if ("LaughTaleCallback".equals(methodName) && LaughTale_debugCallbackListener != null && data != null) {
            LaughTale_debugCallbackListener.onCallback(data);
        }
        LaughTaleUnityBridge.send(gamaObject, methodName, data);
    }
    //////////////////////////////////////////////////////////////////////tools////////////////////////////////////////////////////////////////////////////////

    public void LaughTaleSetUserNoPopAd(){
        if (LaughTale_dataPrefs == null && LaughTale_activity != null) {
            LaughTale_dataPrefs = LaughTale_activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
        }
        if (LaughTale_dataPrefs != null) {
            LaughTale_dataPrefs.edit().putInt("noPop", 1).apply();
        }
        LaughTale_needPopAD = false;
        LaughTale_bannerEnabled = false;
        //关闭现在的广告
        LaughTaleHideBannerView();
        LaughTaleHideMRECBannerView();
    }

    public void LaughTaleGetUserNoPopAd(){
        if (LaughTale_dataPrefs == null && LaughTale_activity != null) {
            LaughTale_dataPrefs = LaughTale_activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
        }
        int launch = LaughTale_dataPrefs != null ? LaughTale_dataPrefs.getInt("noPop", 0) : 0;
        if (launch != 0){
            LaughTale_needPopAD = false;
        }else {
            LaughTale_needPopAD = true;
        }
    }

    //////////////////////////////////////////////////////////////////////Debug status (test app) ////////////////////////////////////////////////////////////////

    public boolean LaughTaleDebugIsCollapsibleReady() {
        return LaughTaleIsNativeReady()
                || (LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleGetADPrice() > 0);
    }

    public boolean LaughTaleDebugIsCollapsibleShowing() {
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (adapter.LaughTale_isOpen) {
                return true;
            }
        }
        return LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleIsShowing();
    }

    public boolean LaughTaleDebugIsRewardReady() {
        return LaughTaleIsRewardADReady("debug");
    }

    public boolean LaughTaleDebugIsRewardShowing() {
        if (LaughTale_showingRewardAsNative) {
            for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
                if (adapter.LaughTale_isOpen) {
                    return true;
                }
            }
        }
        return LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleIsShowing();
    }

    public boolean LaughTaleDebugIsMrecReady() {
        return LaughTale_mrecAdapter != null && LaughTale_mrecAdapter.LaughTaleIsLoaded();
    }

    public boolean LaughTaleDebugIsMrecShowing() {
        return LaughTale_mrecAdapter != null && LaughTale_mrecAdapter.LaughTaleIsVisible();
    }

    public boolean LaughTaleDebugIsNativeReady() {
        return LaughTaleIsNativeReady();
    }

    public boolean LaughTaleDebugIsInterReady() {
        return LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleGetADPrice() > 0;
    }

    public boolean LaughTaleDebugIsRewardVideoReady() {
        return LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleGetADPrice() > 0;
    }

    public boolean LaughTaleDebugIsSplashReady() {
        return LaughTaleHasSplashCandidateReady();
    }
}
