package com.dream.laughtale;
//import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.content.Context;
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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LaughTaleMediationManager implements LaughTaleInterstitialListener, LaughTaleRewardVideoListener,
        LaughTaleNativeListener, LaughTaleSplashListener {

    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private static LaughTaleMediationManager instance;

    private Activity LaughTale_activity;

    private LaughTaleInterstitialAdapter LaughTale_interAdapter;

    private LaughTaleRewardVideoAdapter LaughTale_rewardAdapter;

    private LaughTaleMRECBannerAdapter LaughTale_mrecAdapter;

    private LaughTaleBannerAdapter LaughTale_bannerAdapter;

    /** 标准 App Open 开屏，来自 LaughTale_SPLASH_ID */
    private LaughTaleSplashAdapter LaughTale_splashAdapter;

    /** 全屏 Native（showInter 优先位），来自 LaughTale_NATIVE_ID */
    private final List<LaughTaleNativeAdapter> LaughTale_fullscreenNativeList = new ArrayList<>();
    /** 半屏 Native（CollapsibleBanner），来自 LaughTale_COLLAPSIBLE_NATIVE_ID */
    private final List<LaughTaleNativeAdapter> LaughTale_collapsibleNativeList = new ArrayList<>();
    /** 全屏+半屏合并缓存，避免热路径反复 new ArrayList */
    private List<LaughTaleNativeAdapter> LaughTale_allNativeAdapters = Collections.emptyList();

    /**
     * 竞品 CanShowAoaResume：切过后台才允许热启动开屏；
     * 展示全屏广告时清掉，避免插屏/激励刚关就立刻出开屏。
     */
    private boolean LaughTale_canShowAoaResume = false;

    private long LaughTale_sessionLaunchCnt = 0;

    private boolean LaughTale_isInitSuccess = false;
    private boolean LaughTale_isInitializing = false;

    private String LaughTale_interKey = "";
    private String LaughTale_rewardKey = "";
    private String LaughTale_mrecKey = "";
    private String LaughTale_bannerKey = "";
    private String LaughTale_nativeKey = "";
    private String LaughTale_collapsibleNativeKey = "";
    private String LaughTale_splashKey = "";

    private static final long LOAD_DELAY_FULLSCREEN_NATIVE_MS = 150L;
    private static final long LOAD_DELAY_COLLAPSIBLE_NATIVE_MS = 400L;
    private static final long LOAD_DELAY_INTER_MS = 800L;
    private static final long LOAD_DELAY_REWARD_MS = 1200L;
    private static final long LOAD_DELAY_BANNER_MS = 1500L;
    private static final long LOAD_DELAY_MREC_MS = 3000L;
    private static final long LOAD_DELAY_FULLSCREEN_NATIVE_LOW_END_MS = 300L;
    private static final long LOAD_DELAY_COLLAPSIBLE_NATIVE_LOW_END_MS = 700L;
    private static final long LOAD_DELAY_INTER_LOW_END_MS = 1400L;
    private static final long LOAD_DELAY_REWARD_LOW_END_MS = 2000L;
    private static final long LOAD_DELAY_BANNER_LOW_END_MS = 2500L;
    private static final long LOAD_DELAY_MREC_LOW_END_MS = 5000L;
    private static final long AD_TICK_INTERVAL_MS = 5000L;
    private static final long COLD_SPLASH_WAIT_TIMEOUT_MS = 12000L;

    private static final String PREF_NATIVE_FULL_INTER_SHOW_COUNT = "native_full_inter_show_count";

    private boolean LaughTale_needPopAD = true;

    private boolean LaughTale_isInPlaying = false;

    private boolean LaughTale_isAppForeground = true;
    private boolean LaughTale_isDestroyed = false;

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

    /** showInter 正在播全屏 Native（Unity 仍走 INTERSTITIAL_*） */
    private boolean LaughTale_showingInterAsNative = false;
    private LaughTaleNativeAdapter LaughTale_interNativeAdapter = null;
    /** CollapsibleBanner 正在播半屏 Native（Unity 走 NATIVE_*） */
    private boolean LaughTale_showingCollapsibleAsNative = false;
    private LaughTaleNativeAdapter LaughTale_collapsibleShowingAdapter = null;

    private boolean LaughTale_rewardVideoInSession = false;
    private boolean LaughTale_rewardCloseDispatched = false;
    private boolean LaughTale_interstitialInSession = false;
    private boolean LaughTale_interstitialCloseDispatched = false;
    private boolean LaughTale_nativeCloseDispatched = false;

    /** Unity 冷启动开屏请求中（ShowSplashADWithUnity） */
    private boolean LaughTale_unityColdSplashPending = false;
    private String LaughTale_unityColdSplashScene = "launch";

    /** 全屏广告 / 半屏 Native 展示期间隐藏 Banner */
    private int LaughTale_fullscreenAdRefCount = 0;
    /** Unity 已调用 showBanner，后续由 SDK 自行管理 Banner 显示/隐藏 */
    private boolean LaughTale_bannerEnabled = false;

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
            LaughTale_splashSessionActive = false;
            LaughTale_coldSplashHandled = true;
            LaughTale_allowHotStartSplash = true;
            // 超时未出开屏时也要恢复 Banner（Unity 常在 Init 前就 ShowBanner）
            LaughTaleSyncBannerVisibility();
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
    private final Runnable fullscreenNativeLoadRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleLoadNativeList(LaughTale_fullscreenNativeList);
        }
    };
    private final Runnable collapsibleNativeLoadRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleLoadNativeList(LaughTale_collapsibleNativeList);
        }
    };
    private final Runnable interLoadRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleLoadInterstitialAd();
        }
    };
    private final Runnable rewardLoadRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleLoadRewardAd();
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

        String admobAppId = "";
        try {
            ApplicationInfo appInfo = activity.getPackageManager()
                    .getApplicationInfo(activity.getPackageName(),
                            PackageManager.GET_META_DATA);
            LaughTale_interKey = appInfo.metaData.getString("LaughTale_INTERSTITIAL_ID");
            LaughTale_rewardKey = appInfo.metaData.getString("LaughTale_REWARDED_ID");
            LaughTale_nativeKey = appInfo.metaData.getString("LaughTale_NATIVE_ID");
            LaughTale_collapsibleNativeKey = appInfo.metaData.getString("LaughTale_COLLAPSIBLE_NATIVE_ID");
            LaughTale_splashKey = appInfo.metaData.getString("LaughTale_SPLASH_ID");
            LaughTale_bannerKey = appInfo.metaData.getString("LaughTale_BANNER_ID");
            LaughTale_mrecKey = appInfo.metaData.getString("LaughTale_MREC_ID");
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
                                    LaughTaleInitGmaSdk(activity, initListener, finalAdmobAppId, false);
                                }
                        );
                    },
                    (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                        Log.w("=====UMP", String.format("%s: %s",
                                requestConsentError.getErrorCode(),
                                requestConsentError.getMessage()));
                        LaughTaleInitGmaSdk(activity, initListener, finalAdmobAppId, true);
                    });
        }catch (Exception e){
            LaughTaleInitGmaSdk(activity, initListener, admobAppId, true);
        }
    }

    private void LaughTaleInitAdAdapters(Activity activity) {
        if (LaughTale_interAdapter != null) {
            return;
        }
        LaughTaleCreateNativeAdapters(
                LaughTale_nativeKey,
                LaughTaleNativeAdapter.DisplayMode.FULLSCREEN,
                LaughTale_fullscreenNativeList,
                activity,
                0);
        // 半屏只保留 1 个广告位（忽略 ; 后的 collapse2）
        LaughTaleCreateNativeAdapters(
                LaughTale_collapsibleNativeKey,
                LaughTaleNativeAdapter.DisplayMode.HALF,
                LaughTale_collapsibleNativeList,
                activity,
                1);

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
        LaughTale_splashAdapter.LaughTale_activity = activity;
        LaughTale_splashAdapter.LaughTale_ad_unit = LaughTale_splashKey;
        LaughTale_splashAdapter.LaughTale_splashListener = this;
        LaughTale_splashAdapter.LaughTaleInitSplashAdapter();

        LaughTale_mrecAdapter = new LaughTaleMRECBannerAdapter();
        LaughTale_mrecAdapter.LaughTale_activity = activity;
        LaughTale_mrecAdapter.LaughTale_ad_unit = LaughTale_mrecKey;
        LaughTale_mrecAdapter.LaughTaleInitBannerAdapter();
        LaughTaleRebuildAllNativeAdaptersCache();
    }

    /**
     * @param maxCount 0=不限制；半屏传 1，忽略 ; 分隔的第 2 个及以后 ID。
     */
    private void LaughTaleCreateNativeAdapters(
            String keyCsv,
            LaughTaleNativeAdapter.DisplayMode mode,
            List<LaughTaleNativeAdapter> out,
            Activity activity,
            int maxCount) {
        if (keyCsv == null || keyCsv.trim().isEmpty()) {
            return;
        }
        String[] ids = keyCsv.split(";");
        for (String raw : ids) {
            if (raw == null) {
                continue;
            }
            String id = raw.trim();
            if (id.isEmpty()) {
                continue;
            }
            if (maxCount > 0 && out.size() >= maxCount) {
                break;
            }
            LaughTaleNativeAdapter adapter = new LaughTaleNativeAdapter();
            adapter.LaughTale_activity = activity;
            adapter.LaughTale_ad_unit = id;
            adapter.LaughTale_displayMode = mode;
            adapter.LaughTale_nativeListener = this;
            adapter.LaughTaleInitNativeAdapter();
            out.add(adapter);
        }
    }

    private void LaughTaleRebuildAllNativeAdaptersCache() {
        List<LaughTaleNativeAdapter> all = new ArrayList<>(
                LaughTale_fullscreenNativeList.size() + LaughTale_collapsibleNativeList.size());
        all.addAll(LaughTale_fullscreenNativeList);
        all.addAll(LaughTale_collapsibleNativeList);
        LaughTale_allNativeAdapters = all;
    }

    private Iterable<LaughTaleNativeAdapter> LaughTaleAllNativeAdapters() {
        return LaughTale_allNativeAdapters;
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
        int status = consentInformation != null
                ? consentInformation.getConsentStatus()
                : ConsentInformation.ConsentStatus.UNKNOWN;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====UMP",
                "canRequestAds=" + canRequestAds
                        + " personalized=" + personalizedAllowed
                        + " status=" + status
                        + " fallback=" + umpPermissiveFallback);
    }

    /** 初始化 GMA Next-Gen SDK（后台线程），完成后再加载广告。 */
    private void LaughTaleInitGmaSdk(Activity activity, ADInitListener initListener, String admobAppId,
                                     boolean umpPermissiveFallback) {
        if (LaughTale_isInitSuccess || LaughTale_isInitializing) {
            return;
        }
        LaughTaleApplyPrivacySettings(activity, umpPermissiveFallback);

        if (admobAppId == null || admobAppId.trim().isEmpty()) {
            Log.e("=====GMA", "com.google.android.gms.ads.APPLICATION_ID not configured, skip MobileAds init");
            LaughTale_isInitializing = false;
            LaughTale_isInitSuccess = false;
            return;
        }

        LaughTale_isInitializing = true;
        final String appId = admobAppId.trim();

        new Thread(() -> {
            try {
                MobileAds.initialize(
                        activity,
                        new InitializationConfig.Builder(appId).build(),
                        initializationStatus -> {
                            mainHandler.post(() -> {
                                LaughTaleInitAdAdapters(activity);
                                LaughTale_isInitSuccess = true;
                                LaughTale_isInitializing = false;
                                if (initListener != null) {
                                    initListener.onAdInitSuccess();
                                }
                                LaughTaleScheduleStaggeredAdLoads();
                                LaughTaleStartAdTick();
                                // Unity 常在 Init 完成前就调 ShowSplash / ShowBanner：补排队请求
                                if (LaughTale_unityColdSplashPending && !LaughTale_coldSplashHandled) {
                                    LaughTaleTryShowUnityColdSplash(LaughTale_unityColdSplashScene);
                                }
                                LaughTaleSyncBannerVisibility();
                            });
                        });
            } catch (Exception e) {
                Log.e("=====GMA", "MobileAds.initialize failed", e);
                mainHandler.post(() -> {
                    LaughTale_isInitializing = false;
                    LaughTale_isInitSuccess = false;
                });
            }
        }, "LaughTale-GMA-Init").start();
    }

    private void LaughTaleStartAdTick() {
        mainHandler.removeCallbacks(adTickRunnable);
        mainHandler.postDelayed(adTickRunnable, AD_TICK_INTERVAL_MS);
    }

    private void LaughTaleStopAdTick() {
        mainHandler.removeCallbacks(adTickRunnable);
    }

    /** 每 5 秒补货；已在 loading / 已有库存则跳过，避免与失败重试叠加重载。 */
    private void LaughTaleTickAdReload() {
        if (!LaughTale_isAppForeground || LaughTale_isDestroyed || !LaughTale_isInitSuccess) {
            return;
        }
        for (LaughTaleNativeAdapter adapter : LaughTaleAllNativeAdapters()) {
            adapter.LaughTaleTickReload();
        }
        if (LaughTale_interAdapter != null
                && !LaughTale_interAdapter.LaughTaleIsShowing()
                && !LaughTale_interAdapter.LaughTaleIsLoading()
                && !LaughTale_interAdapter.LaughTaleIsAdAvailable()) {
            LaughTale_interAdapter.LaughTaleLoadInterstitialAd();
        }
        if (LaughTale_rewardAdapter != null
                && !LaughTale_rewardAdapter.LaughTaleIsShowing()
                && !LaughTale_rewardAdapter.LaughTaleIsLoading()
                && !LaughTale_rewardAdapter.LaughTaleIsAdAvailable()) {
            LaughTale_rewardAdapter.LaughTaleLoadRewardVideoAd();
        }
        if (LaughTale_splashAdapter != null
                && !LaughTale_splashAdapter.LaughTaleIsShowing()
                && !LaughTale_splashAdapter.LaughTaleIsLoading()
                && !LaughTale_splashAdapter.LaughTaleIsReady()) {
            LaughTale_splashAdapter.LaughTaleLoadSplashAd();
        }
        // Banner 失败后无自动退避，靠 tick 补货
        if (LaughTale_bannerAdapter != null
                && !LaughTale_bannerAdapter.LaughTaleIsLoading()
                && !LaughTale_bannerAdapter.LaughTaleIsLoaded()) {
            LaughTale_bannerAdapter.LaughTaleLoadBannerView();
        }
    }

    /**
     * 冷启动错峰：Splash 立刻；全屏 Native → 半屏 Native → Inter → Reward → Banner → MREC。
     * 低端机再拉长间隔，减轻主线程与网络并发。
     */
    private void LaughTaleScheduleStaggeredAdLoads() {
        LaughTaleLoadSplashAd();

        boolean lowEnd = LaughTaleToolsManager.instance().LaughTaleIsLowEndDevice();
        long fullscreenNativeDelay = lowEnd
                ? LOAD_DELAY_FULLSCREEN_NATIVE_LOW_END_MS : LOAD_DELAY_FULLSCREEN_NATIVE_MS;
        long collapsibleNativeDelay = lowEnd
                ? LOAD_DELAY_COLLAPSIBLE_NATIVE_LOW_END_MS : LOAD_DELAY_COLLAPSIBLE_NATIVE_MS;
        long interDelay = lowEnd ? LOAD_DELAY_INTER_LOW_END_MS : LOAD_DELAY_INTER_MS;
        long rewardDelay = lowEnd ? LOAD_DELAY_REWARD_LOW_END_MS : LOAD_DELAY_REWARD_MS;
        long bannerDelay = lowEnd ? LOAD_DELAY_BANNER_LOW_END_MS : LOAD_DELAY_BANNER_MS;
        long mrecDelay = lowEnd ? LOAD_DELAY_MREC_LOW_END_MS : LOAD_DELAY_MREC_MS;

        mainHandler.postDelayed(fullscreenNativeLoadRunnable, fullscreenNativeDelay);
        mainHandler.postDelayed(collapsibleNativeLoadRunnable, collapsibleNativeDelay);
        mainHandler.postDelayed(interLoadRunnable, interDelay);
        mainHandler.postDelayed(rewardLoadRunnable, rewardDelay);
        mainHandler.postDelayed(bannerLoadRunnable, bannerDelay);
        mainHandler.postDelayed(mrecLoadRunnable, mrecDelay);
    }

    private void LaughTaleCancelStaggeredAdLoads() {
        mainHandler.removeCallbacks(fullscreenNativeLoadRunnable);
        mainHandler.removeCallbacks(collapsibleNativeLoadRunnable);
        mainHandler.removeCallbacks(interLoadRunnable);
        mainHandler.removeCallbacks(rewardLoadRunnable);
        mainHandler.removeCallbacks(bannerLoadRunnable);
        mainHandler.removeCallbacks(mrecLoadRunnable);
    }

    //////////////////////////////////////////////////////////////////////Interstitial////////////////////////////////////////////////////////////////////////////////


    private void LaughTaleLoadInterstitialAd(){
        if (LaughTale_interAdapter == null) {
            return;
        }
        LaughTale_interAdapter.LaughTaleLoadInterstitialAd();
    }

    private int LaughTaleGetNativeFullInterShowCount() {
        if (LaughTale_dataPrefs == null) {
            return 0;
        }
        return LaughTale_dataPrefs.getInt(PREF_NATIVE_FULL_INTER_SHOW_COUNT, 0);
    }

    private void LaughTaleIncrementNativeFullInterShowCount() {
        if (LaughTale_dataPrefs == null) {
            return;
        }
        int next = LaughTaleGetNativeFullInterShowCount() + 1;
        LaughTale_dataPrefs.edit().putInt(PREF_NATIVE_FULL_INTER_SHOW_COUNT, next).apply();
    }

    private void LaughTaleResetNativeFullInterShowCount() {
        if (LaughTale_dataPrefs == null) {
            return;
        }
        LaughTale_dataPrefs.edit().putInt(PREF_NATIVE_FULL_INTER_SHOW_COUNT, 0).apply();
    }

    /** 竞品：全屏广告展示后清掉 CanShowAoaResume。 */
    private void LaughTaleClearAoaResumeEligibility() {
        LaughTale_canShowAoaResume = false;
    }

    private boolean LaughTaleIsInterFormatReady() {
        return LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleIsReady();
    }

    private boolean LaughTaleIsFullscreenNativeReady() {
        return LaughTalePickReadyFullscreenNative() != null;
    }

    /**
     * 对齐竞品：就绪 + 次数优先（非 eCPM 比价）。
     * count &lt; max 且全屏 Native ready → Native；否则 Inter；再否则兜底 Native。
     */
    private boolean LaughTaleShouldPreferFullscreenNativeForInter() {
        return LaughTaleIsFullscreenNativeReady()
                && LaughTaleGetNativeFullInterShowCount()
                        < LaughTaleNativeAdConstants.NATIVE_FULL_COUNT_BEFORE_INTER_MAX;
    }

    private boolean LaughTaleCanShowInterOrFullscreenNative() {
        return LaughTaleIsFullscreenNativeReady() || LaughTaleIsInterFormatReady();
    }

    ///unity用
    public boolean LaughTaleIsIntertitialADReady(String scene){
        return LaughTaleCanShowInterOrFullscreenNative();
    }

    public boolean LaughTaleIsIntertitialADReadyLog(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("inter_should_show",object.toString());
        }catch (Exception e){

        }
        return LaughTaleCanShowInterOrFullscreenNative();
    }

    public void LaughTaleShowInterstitialUnity(String scene) {
        if (!LaughTaleIsIntertitialADReadyLog(scene)) {
            return;
        }
        try {
            JSONObject object = new JSONObject();
            object.put("scene", scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("inter_show", object.toString());
        } catch (Exception e) { }
        LaughTaleDecideAndShowInterstitialAd();
    }

    private void LaughTaleDecideAndShowInterstitialAd() {
        LaughTaleNativeAdapter fullscreenNative = LaughTalePickReadyFullscreenNative();
        boolean nativeReady = fullscreenNative != null;
        boolean interReady = LaughTaleIsInterFormatReady();
        if (LaughTaleShouldPreferFullscreenNativeForInter() && nativeReady) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===ShowInterAsNative count=" + LaughTaleGetNativeFullInterShowCount()
                            + "/" + LaughTaleNativeAdConstants.NATIVE_FULL_COUNT_BEFORE_INTER_MAX);
            LaughTaleShowInterAsFullscreenNative(fullscreenNative);
            return;
        }
        if (interReady) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===ShowInterFormat");
                LaughTale_showingInterAsNative = false;
            LaughTale_interNativeAdapter = null;
            LaughTaleCloseAllOpenNativeAds();
            // 允许本次 show 失败时再发 CLOSE，避免 Unity 等不到回调
            LaughTale_interstitialCloseDispatched = false;
            LaughTale_interAdapter.LaughTaleShowInterstitialAd();
            return;
        }
        if (nativeReady) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                    "===ShowInterAsNative(fallback no inter)");
            LaughTaleShowInterAsFullscreenNative(fullscreenNative);
        }
    }

    private void LaughTaleShowInterAsFullscreenNative(LaughTaleNativeAdapter adapter) {
        // 先静默关掉其它 Native，再置 inter flag，避免误走 INTERSTITIAL_CLOSE
        LaughTaleCloseOtherOpenNativeAds(adapter);
        LaughTale_showingInterAsNative = true;
        LaughTale_interNativeAdapter = adapter;
        adapter.LaughTaleShowNativeAd();
    }

    @Override
    public void LaughTaleOnInterstitialAdClosed() {
        LaughTaleOnFullscreenAdClosed();
        LaughTaleClearAoaResumeEligibility();
        LaughTale_interstitialInSession = false;
        LaughTaleResetNativeFullInterShowCount();
        LaughTaleDispatchInterstitialCloseCallbackNow();
        LaughTaleRetryPendingColdSplashIfNeeded();
    }

    @Override
    public void LaughTaleOnInterstitialAdFailedToShow() {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=====LaughTaleMediatonManager", "===InterFailedToShow");
        LaughTaleOnFullscreenAdClosed();
        LaughTaleClearAoaResumeEligibility();
        LaughTale_interstitialInSession = false;
        // 未真正展示不重置 Native 优先次数；仍发 CLOSE 解阻塞 Unity
        LaughTaleDispatchInterstitialCloseCallbackNow();
        LaughTaleRetryPendingColdSplashIfNeeded();
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
        boolean rewardReady = LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleIsReady();
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                "===IsRewardADReady=" + rewardReady);
        return rewardReady;
    }

    public void LaughTaleShowRewardAdUnity(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("reward_should_show",object.toString());
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("reward_show",object.toString());
        }catch (Exception e){
        }
        if (LaughTale_rewardAdapter == null || !LaughTale_rewardAdapter.LaughTaleIsReady()) {
            return;
        }
        LaughTale_rewardAdapter.LaughTaleShowRewardVideoAd();
    }

    @Override
    public void LaughTaleOnRewardVideoAdClosed() {
        LaughTaleOnFullscreenAdClosed();
        LaughTaleClearAoaResumeEligibility();
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
        LaughTale_bannerEnabled = false;
        if (LaughTale_bannerAdapter == null) {
            return;
        }
        LaughTale_bannerAdapter.LaughTaleHideBannerView();
    }

    /** Banner 加载完成后再按 bannerEnabled / 全屏状态 Sync，避免 Init 前 ShowBanner 永久不显示 */
    public void LaughTaleOnBannerAdLoaded() {
        LaughTaleSyncBannerVisibility();
    }

    /** 半屏遮罩底部预留，避免挡住 Banner */
    public int LaughTaleGetBannerReserveHeightPx() {
        if (LaughTale_bannerAdapter == null) {
            return 0;
        }
        return LaughTale_bannerAdapter.LaughTaleGetBannerReserveHeightPx();
    }

    public void LaughTaleBringBannerToFront() {
        if (LaughTale_bannerAdapter == null) {
            return;
        }
        LaughTale_bannerAdapter.LaughTaleBringBannerToFront();
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
        // 半屏 b2x 居中卡片不挡 Banner
        return LaughTaleIsFullscreenAdShowing() || LaughTaleIsSplashAdShowing();
    }

    private void LaughTaleSyncBannerVisibility() {
        // 全屏广告 dismiss 可能在 GMA 后台线程回调，Banner 显隐必须回主线程
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post(this::LaughTaleSyncBannerVisibility);
            return;
        }
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
        LaughTaleLoadNativeList(LaughTale_allNativeAdapters);
    }

    private void LaughTaleLoadNativeList(List<LaughTaleNativeAdapter> list) {
        if (list == null || list.isEmpty()) {
            return;
        }
        for (LaughTaleNativeAdapter adapter : list) {
            adapter.LaughTaleLoadNativeAdView();
        }
    }

    private boolean LaughTaleIsFullscreenNativeInventoryReady(){
        return LaughTalePickReadyFullscreenNative() != null;
    }

    private boolean LaughTaleIsCollapsibleNativeReady(){
        return LaughTalePickReadyCollapsibleNative() != null;
    }

    private LaughTaleNativeAdapter LaughTalePickReadyFromList(List<LaughTaleNativeAdapter> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        for (LaughTaleNativeAdapter adapter : list) {
            adapter.LaughTaleRefreshStaleInventoryIfNeeded();
            if (adapter.LaughTale_isOpen) {
                continue;
            }
            if (adapter.LaughTaleCanShowNativeAd()) {
                return adapter;
            }
        }
        return null;
    }

    private LaughTaleNativeAdapter LaughTalePickReadyFullscreenNative() {
        return LaughTalePickReadyFromList(LaughTale_fullscreenNativeList);
    }

    private LaughTaleNativeAdapter LaughTalePickReadyCollapsibleNative() {
        return LaughTalePickReadyFromList(LaughTale_collapsibleNativeList);
    }

    private boolean LaughTaleHasOpenNativeAd() {
        for (LaughTaleNativeAdapter adapter : LaughTaleAllNativeAdapters()) {
            if (adapter.LaughTale_isOpen) {
                return true;
            }
        }
        return false;
    }

    private boolean LaughTaleHasOpenCollapsibleNative() {
        for (LaughTaleNativeAdapter adapter : LaughTale_collapsibleNativeList) {
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
        for (LaughTaleNativeAdapter adapter : LaughTaleAllNativeAdapters()) {
            if (adapter == except || !adapter.LaughTale_isOpen) {
                continue;
            }
            adapter.LaughTaleForceCloseNativeAdSilent();
        }
    }

    @Override
    public void LaughTaleOnNativeAdClosed(boolean clickedCloseButton, boolean silentClose) {
        if (LaughTale_showingInterAsNative) {
            LaughTaleOnFullscreenAdClosed();
            LaughTale_showingInterAsNative = false;
            LaughTale_interNativeAdapter = null;
            LaughTale_interstitialInSession = false;
            LaughTaleDispatchInterstitialCloseCallbackNow();
            LaughTaleClearAoaResumeEligibility();
            LaughTaleRetryPendingColdSplashIfNeeded();
            return;
        }
        if (LaughTale_showingCollapsibleAsNative) {
            LaughTale_showingCollapsibleAsNative = false;
            LaughTale_collapsibleShowingAdapter = null;
            if (silentClose) {
                LaughTaleSyncBannerVisibility();
                LaughTaleRetryPendingColdSplashIfNeeded();
                return;
            }
            LaughTaleDispatchNativeCloseCallbackNow();
            LaughTaleSyncBannerVisibility();
            LaughTaleRetryPendingColdSplashIfNeeded();
            return;
        }
        if (silentClose) {
            LaughTaleSyncBannerVisibility();
            return;
        }
        LaughTaleOnFullscreenAdClosed();
        LaughTaleDispatchNativeCloseCallbackNow();
        LaughTaleClearAoaResumeEligibility();
    }

    @Override
    public void LaughTaleOnNativeAdDisplayed() {
        if (LaughTale_showingInterAsNative) {
            LaughTaleOnFullscreenAdShow();
            if (LaughTale_interstitialInSession) {
                return;
            }
            LaughTale_interstitialInSession = true;
            LaughTale_interstitialCloseDispatched = false;
            LaughTaleIncrementNativeFullInterShowCount();
            LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_OPEN");
            return;
        }
        if (LaughTale_showingCollapsibleAsNative) {
            LaughTaleSyncBannerVisibility();
            LaughTale_nativeCloseDispatched = false;
            LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_NATIVE_OPEN");
            return;
        }
        LaughTaleOnFullscreenAdShow();
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
        if (LaughTale_showingInterAsNative) {
            LaughTale_showingInterAsNative = false;
            LaughTale_interNativeAdapter = null;
            LaughTaleSyncBannerVisibility();
        }
        if (LaughTale_showingCollapsibleAsNative) {
            LaughTale_showingCollapsibleAsNative = false;
            LaughTale_collapsibleShowingAdapter = null;
            LaughTaleSyncBannerVisibility();
        }
    }

    private void LaughTaleShowCollapsibleHalfNative(LaughTaleNativeAdapter adapter) {
        LaughTale_showingCollapsibleAsNative = true;
        LaughTale_collapsibleShowingAdapter = adapter;
        LaughTaleCloseOtherOpenNativeAds(adapter);
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                "===ShowCollapsibleHalf unit=" + adapter.LaughTale_ad_unit);
        adapter.LaughTaleShowNativeAdHalf();
    }

    /**
     * CollapsibleBanner = 半屏 Native（单广告位、FSN 样式、无 double）。
     */
    public void LaughTaleShowCollapsibleBannerView(boolean needHighValue){
        if (LaughTaleHasOpenCollapsibleNative()) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=====LaughTaleMediatonManager", "===ShowCollapsibleSkipped already showing");
            return;
        }
        try {
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_should_show",null);
        }catch (Exception e){
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager",
                "===ShowCollapsibleBanner half-native needHighValue=" + needHighValue);
        LaughTaleNativeAdapter adapter = LaughTalePickReadyCollapsibleNative();
        if (adapter == null) {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=====LaughTaleMediatonManager", "===ShowCollapsibleSkipped not ready");
            return;
        }
        LaughTaleShowCollapsibleHalfNative(adapter);
    }

    public void LaughTaleHideCollapsibleBannerView(){
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=====LaughTaleMediatonManager", "===HideCollapsibleBannerView");
        for (LaughTaleNativeAdapter adapter : LaughTale_collapsibleNativeList) {
            if (adapter.LaughTale_isOpen) {
                adapter.LaughTaleAutoHideNativeAd();
            }
        }
    }


    //////////////////////////////////////////////////////////////////////Splash////////////////////////////////////////////////////////////////////////////////

    /**
     * 冷启动开屏开关：needPopAD + open_ad_startup_on_off。
     * 次数限制由本进程内存 flag {@link #LaughTale_coldSplashHandled} 保证（每次冷启动最多 1 次，不落盘）。
     */
    private boolean LaughTaleCanShowColdStartSplash() {
        return LaughTale_needPopAD && LaughTaleNativeAdConstants.OPEN_AD_STARTUP_ON;
    }

    /**
     * 竞品热启动：open_ad_on_off + open_ad_resume_on_off + CanShowAoaResume。
     * CanShowAoaResume 在真正切后台后置 true，全屏广告关闭后清 false。
     */
    private boolean LaughTaleCanShowResumeSplash() {
        return LaughTale_needPopAD
                && LaughTaleNativeAdConstants.OPEN_AD_RESUME_ON
                && LaughTale_canShowAoaResume;
    }

    private boolean LaughTaleHasSplashCandidateReady() {
        return LaughTale_splashAdapter != null && LaughTale_splashAdapter.LaughTaleIsReady();
    }

    private void LaughTaleLoadSplashAd() {
        if (LaughTale_splashAdapter == null) {
            return;
        }
        LaughTale_splashAdapter.LaughTaleLoadSplashAd();
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

    /** 激励/插屏/全屏 Native / 半屏 Collapsible / 开屏展示中时不播开屏 */
    private boolean LaughTaleIsBlockingSplash() {
        if (LaughTaleIsSplashAdShowing()) {
            return true;
        }
        if (LaughTale_showingInterAsNative || LaughTale_showingCollapsibleAsNative) {
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
        for (LaughTaleNativeAdapter adapter : LaughTaleAllNativeAdapters()) {
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
        LaughTaleSyncBannerVisibility();
    }

    private boolean LaughTaleShouldBlockHotStartByPlayingState() {
        return LaughTale_isInPlaying;
    }

    private void LaughTaleFinishHotStartSplashAttemptNotReady() {
        LaughTale_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
    }

    private void LaughTaleScheduleHotStartSplashWaitTimeout() {
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        mainHandler.postDelayed(hotStartSplashTimeoutRunnable, COLD_SPLASH_WAIT_TIMEOUT_MS);
    }

    /** SDK 热启动：回前台时由生命周期触发；仅 Native AOA 全屏。 */
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
        if (!LaughTaleCanShowResumeSplash()) {
            LaughTale_wentToBackground = false;
            return;
        }
        LaughTale_wentToBackground = false;
        if (LaughTaleHasSplashCandidateReady()) {
            LaughTaleShowStandardSplash(LAUGHTALE_HOT_START_SPLASH_SCENE);
            return;
        }
        LaughTale_hotStartSplashPending = true;
        LaughTaleBeginWaitingForSplashInventory(LAUGHTALE_HOT_START_SPLASH_SCENE);
    }

    private void LaughTaleTryShowHotStartSplashAfterLoad(String scene) {
        if (!LaughTale_hotStartSplashPending || !LaughTale_allowHotStartSplash
                || !LaughTale_needPopAD || LaughTale_isDestroyed) {
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (LaughTaleIsBlockingSplash() || !LaughTaleCanShowResumeSplash()) {
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
        LaughTaleShowStandardSplash(scene != null ? scene : LAUGHTALE_HOT_START_SPLASH_SCENE);
    }

    private void LaughTaleTryShowUnityColdSplash(String scene) {
        if (!LaughTale_unityColdSplashPending || LaughTale_coldSplashHandled
                || !LaughTale_needPopAD || LaughTale_isDestroyed) {
            return;
        }
        if (LaughTaleIsBlockingSplash()) {
            // 被半屏/插屏挡住时不要结束冷启动，等对方关闭后再试
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=====LaughTaleMediatonManager",
                    "===ColdSplash blocked, keep pending");
            return;
        }
        if (!LaughTaleCanShowColdStartSplash()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        if (LaughTaleHasSplashCandidateReady()) {
            LaughTaleShowStandardSplash(scene != null ? scene : LaughTale_unityColdSplashScene);
            return;
        }
        LaughTaleBeginWaitingForSplashInventory(scene != null ? scene : LaughTale_unityColdSplashScene);
    }

    /** 挡住冷启动开屏的全屏/半屏关掉后重试。 */
    private void LaughTaleRetryPendingColdSplashIfNeeded() {
        if (!LaughTale_unityColdSplashPending || LaughTale_coldSplashHandled) {
            return;
        }
        mainHandler.post(() -> LaughTaleTryShowUnityColdSplash(LaughTale_unityColdSplashScene));
    }

    private void LaughTaleResumeSplashOnForeground() {
        if (LaughTale_unityColdSplashPending && !LaughTale_coldSplashHandled) {
            LaughTaleTryShowUnityColdSplash(LaughTale_unityColdSplashScene);
            return;
        }
        LaughTaleTryShowHotStartSplash();
    }

    /** 等 onPause 里关 Native 的回调跑完，再决策热启动开屏。 */
    private void LaughTaleResumeSplashOnForegroundDeferred() {
        mainHandler.post(this::LaughTaleResumeSplashOnForeground);
    }

    /** 冷/热启动开屏：标准 App Open（LaughTale_SPLASH_ID）。 */
    private void LaughTaleShowStandardSplash(String scene) {
        if (!LaughTale_needPopAD || LaughTale_isDestroyed) {
            if (LaughTale_unityColdSplashPending) {
                LaughTaleFinishColdStartSplashFlow();
            }
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        if (LaughTaleIsBlockingSplash()) {
            if (LaughTale_unityColdSplashPending) {
                // 冷启动排队中被挡住：保留 pending，不 Finish
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                        "=====LaughTaleMediatonManager",
                        "===ShowStandardSplash blocked while cold pending");
                return;
            }
            LaughTale_wentToBackground = false;
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }
        boolean isCold = LaughTale_unityColdSplashPending;
        if (isCold) {
            if (!LaughTaleCanShowColdStartSplash()) {
                LaughTaleFinishColdStartSplashFlow();
                return;
            }
        } else if (!LaughTaleCanShowResumeSplash()) {
            LaughTale_wentToBackground = false;
            LaughTaleFinishHotStartSplashAttemptNotReady();
            return;
        }

        if (!LaughTaleHasSplashCandidateReady()) {
            if (LaughTale_unityColdSplashPending || LaughTale_hotStartSplashPending) {
                LaughTaleBeginWaitingForSplashInventory(scene);
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
        LaughTale_hotStartSplashPending = false;
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        LaughTale_splashSessionActive = true;
        LaughTaleClearAoaResumeEligibility();

        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=====LaughTaleMediatonManager", "===ShowSplash AppOpen");
        LaughTale_splashAdapter.LaughTaleShowSplashAd();
    }

    private void LaughTaleBeginWaitingForSplashInventory(String scene) {
        LaughTaleLoadSplashAd();
        if (LaughTale_unityColdSplashPending) {
            LaughTaleScheduleSplashWaitTimeout();
        } else if (LaughTale_hotStartSplashPending) {
            LaughTaleScheduleHotStartSplashWaitTimeout();
        }
    }

    private void LaughTaleFinishSplashSession(boolean unusedApplyCooldown) {
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        LaughTaleClearAoaResumeEligibility();
        LaughTale_unityColdSplashPending = false;
        LaughTale_coldSplashHandled = true;
        LaughTale_allowHotStartSplash = true;
        LaughTale_hotStartSplashPending = false;
        LaughTale_splashSessionActive = false;
        LaughTale_splashOpenDispatched = false;
        LaughTaleSyncBannerVisibility();
    }

    /**
     * Unity 冷启动开屏。理想时机：Init 成功之后；若 Init 前调用会排队，等 Init 后再播。
     * 注意：不要在 adapter 未就绪时把 coldSplashHandled 置 true，否则冷启动开屏会永久跳过。
     */
    public void LaughTaleShowSplashADWithUnity(String scene) {
        if (LaughTale_coldSplashHandled || !LaughTale_needPopAD) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        LaughTale_unityColdSplashScene = scene != null ? scene : "launch";
        // 未 Init / 无 adapter：只排队，等 LaughTaleInit 完成后再试
        if (!LaughTale_isInitSuccess || LaughTale_splashAdapter == null) {
            LaughTale_unityColdSplashPending = true;
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "=====LaughTaleMediatonManager",
                    "===ShowSplash queued until init (isInit=" + LaughTale_isInitSuccess + ")");
            return;
        }
        if (LaughTale_splashKey == null || LaughTale_splashKey.trim().isEmpty()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        if (LaughTaleIsBlockingSplash()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        if (!LaughTaleCanShowColdStartSplash()) {
            LaughTaleFinishColdStartSplashFlow();
            return;
        }
        LaughTale_unityColdSplashPending = true;
        if (LaughTaleHasSplashCandidateReady()) {
            LaughTaleShowStandardSplash(LaughTale_unityColdSplashScene);
            return;
        }
        LaughTaleBeginWaitingForSplashInventory(LaughTale_unityColdSplashScene);
    }

    public void LaughTaleCancelSplashADWithUnity() {
        LaughTale_unityColdSplashPending = false;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        LaughTaleFinishColdStartSplashFlow();
    }

    /** 兼容 Unity 旧接口；热启动开屏由 SDK 生命周期 onResume 托管。 */
    public void LaughTaleShowSplashADWithLifeTime() {
    }

    public boolean LaughTaleIsSplashADReady() {
        return LaughTaleHasSplashCandidateReady();
    }

    @Override
    public void LaughTaleOnSplashAdLoaded() {
        if (LaughTale_unityColdSplashPending) {
            LaughTaleTryShowUnityColdSplash(LaughTale_unityColdSplashScene);
            return;
        }
        if (LaughTale_hotStartSplashPending) {
            LaughTaleTryShowHotStartSplashAfterLoad(LAUGHTALE_HOT_START_SPLASH_SCENE);
        }
    }

    @Override
    public void LaughTaleOnSplashAdDisplayed() {
        LaughTaleOnFullscreenAdShow();
        LaughTale_splashOpenDispatched = true;
        LaughTaleNotifySplashOpen();
    }

    @Override
    public void LaughTaleOnSplashAdClosed() {
        LaughTaleOnFullscreenAdClosed();
        if (LaughTale_splashOpenDispatched) {
            LaughTaleNotifySplashClose();
        }
        LaughTaleFinishSplashSession(true);
    }

    @Override
    public void LaughTaleOnSplashAdFailedToShow() {
        LaughTale_splashSessionActive = false;
        if (LaughTale_splashOpenDispatched) {
            LaughTaleNotifySplashClose();
            LaughTaleFinishSplashSession(true);
            return;
        }
        LaughTaleFinishHotStartSplashAttemptNotReady();
        if (LaughTale_unityColdSplashPending) {
            LaughTaleFinishColdStartSplashFlow();
        } else if (!LaughTale_coldSplashHandled) {
            LaughTale_allowHotStartSplash = true;
        }
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
        if (LaughTale_splashAdapter != null) {
            LaughTale_splashAdapter.LaughTale_activity = activity;
        }
        for (LaughTaleNativeAdapter adapter : LaughTaleAllNativeAdapters()) {
            adapter.LaughTale_activity = activity;
        }
        if (LaughTale_isInitSuccess && !LaughTale_isDestroyed) {
            LaughTaleStartAdTick();
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
        if (LaughTale_isInitSuccess && !LaughTale_isDestroyed) {
            LaughTaleStartAdTick();
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
    }

    private boolean LaughTaleIsSplashAdShowing() {
        return LaughTale_splashSessionActive
                || (LaughTale_splashAdapter != null && LaughTale_splashAdapter.LaughTaleIsShowing());
    }

    private boolean LaughTaleIsFullscreenAdShowing() {
        if (LaughTale_splashAdapter != null && LaughTale_splashAdapter.LaughTaleIsShowing()) {
            return true;
        }
        if (LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleIsShowing()) {
            return true;
        }
        if (LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleIsShowing()) {
            return true;
        }
        if (LaughTale_showingInterAsNative) {
            return true;
        }
        for (LaughTaleNativeAdapter adapter : LaughTale_fullscreenNativeList) {
            if (adapter.LaughTale_isOpen) {
                return true;
            }
        }
        return false;
    }

    private void LaughTaleHideAllNativeAdsOnBackground() {
        for (LaughTaleNativeAdapter adapter : LaughTaleAllNativeAdapters()) {
            if (!adapter.LaughTale_isOpen) {
                continue;
            }
            adapter.LaughTaleCloseNativeAdOnBackground();
        }
    }

    public void LaughTaleOnAppStop() {
        LaughTale_isAppForeground = false;
        LaughTale_wentToBackground = true;
        // 竞品 CanShowAoaResume：真正切后台后允许下次回前台热启动开屏
        if (LaughTaleNativeAdConstants.OPEN_AD_RESUME_ON) {
            LaughTale_canShowAoaResume = true;
        }
        LaughTaleStopAdTick();
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
        LaughTaleCancelStaggeredAdLoads();
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        LaughTaleStopAdTick();
        LaughTale_unityColdSplashPending = false;
        for (LaughTaleNativeAdapter adapter : LaughTaleAllNativeAdapters()) {
            adapter.LaughTaleRelease();
        }
        LaughTale_fullscreenNativeList.clear();
        LaughTale_collapsibleNativeList.clear();
        LaughTaleRebuildAllNativeAdaptersCache();
        if (LaughTale_interAdapter != null) {
            LaughTale_interAdapter.LaughTaleOnDestroy();
            LaughTale_interAdapter = null;
        }
        if (LaughTale_rewardAdapter != null) {
            LaughTale_rewardAdapter.LaughTaleOnDestroy();
            LaughTale_rewardAdapter = null;
        }
        if (LaughTale_splashAdapter != null) {
            LaughTale_splashAdapter.LaughTaleOnDestroy();
            LaughTale_splashAdapter = null;
        }
        if (LaughTale_mrecAdapter != null) {
            LaughTale_mrecAdapter.LaughTaleOnDestroy();
            LaughTale_mrecAdapter = null;
        }
        if (LaughTale_bannerAdapter != null) {
            LaughTale_bannerAdapter.LaughTaleOnDestroy();
            LaughTale_bannerAdapter = null;
        }
        LaughTale_isInitSuccess = false;
        LaughTale_isInitializing = false;
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
        return LaughTaleIsCollapsibleNativeReady();
    }

    public boolean LaughTaleDebugIsCollapsibleShowing() {
        return LaughTaleHasOpenCollapsibleNative();
    }

    public boolean LaughTaleDebugIsRewardReady() {
        return LaughTaleIsRewardADReady("debug");
    }

    public boolean LaughTaleDebugIsRewardShowing() {
        return LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleIsShowing();
    }

    public boolean LaughTaleDebugIsMrecReady() {
        return LaughTale_mrecAdapter != null && LaughTale_mrecAdapter.LaughTaleIsLoaded();
    }

    public boolean LaughTaleDebugIsMrecShowing() {
        return LaughTale_mrecAdapter != null && LaughTale_mrecAdapter.LaughTaleIsVisible();
    }

    public boolean LaughTaleDebugIsNativeReady() {
        return LaughTaleIsFullscreenNativeInventoryReady();
    }

    public boolean LaughTaleDebugIsInterReady() {
        return LaughTaleCanShowInterOrFullscreenNative();
    }

    public boolean LaughTaleDebugIsInterShowing() {
        return LaughTale_interstitialInSession
                || LaughTale_showingInterAsNative
                || (LaughTale_interAdapter != null && LaughTale_interAdapter.LaughTaleIsShowing());
    }

    public boolean LaughTaleDebugIsRewardVideoReady() {
        return LaughTale_rewardAdapter != null && LaughTale_rewardAdapter.LaughTaleIsReady();
    }

    public boolean LaughTaleDebugIsSplashReady() {
        return LaughTaleHasSplashCandidateReady();
    }

    /**
     * Demo / 调试：重置本进程冷启动开屏状态，便于同一次运行内反复测开屏。
     * 正式包不要调用。
     */
    public void LaughTaleDebugResetColdSplashState() {
        LaughTale_coldSplashHandled = false;
        LaughTale_unityColdSplashPending = false;
        LaughTale_hotStartSplashPending = false;
        LaughTale_splashSessionActive = false;
        LaughTale_allowHotStartSplash = false;
        mainHandler.removeCallbacks(coldSplashTimeoutRunnable);
        mainHandler.removeCallbacks(hotStartSplashTimeoutRunnable);
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=====LaughTaleMediatonManager", "===DebugResetColdSplashState");
    }

    public String LaughTaleDebugSplashStateText() {
        return "SplashReady=" + LaughTaleHasSplashCandidateReady()
                + " pending=" + LaughTale_unityColdSplashPending
                + " handled=" + LaughTale_coldSplashHandled
                + " needPop=" + LaughTale_needPopAD
                + " init=" + LaughTale_isInitSuccess;
    }
}
