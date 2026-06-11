package com.dream.laughtale;
//import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.applovin.sdk.AppLovinPrivacySettings;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkConfiguration;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;

import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.util.Log;


import org.json.JSONObject;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

//import androidx.annotation.Nullable;

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

    // 广告闪屏显示间隔时间, 间隔内不出现广告 ,默认5
    private long LaughTale_mSplashADInterval = 1000 * 5;
    // 上次闪屏显示广告的时间
    private long LaughTale_mSplashLastADTime = 0;

    private long LaughTale_sessionLaunchCnt = 0;

    private boolean LaughTale_isInitSuccess = false;
    private boolean LaughTale_isInitializing = false;

    private static final long LOAD_DELAY_BANNER_MS = 1500L;
    private static final long LOAD_DELAY_MREC_MS = 3000L;
    private static final long LOAD_DELAY_BANNER_LOW_END_MS = 2500L;
    private static final long LOAD_DELAY_MREC_LOW_END_MS = 5000L;

    private int LaughTale_cachedPValueDayOfYear = -1;
    private double LaughTale_cachedPValue = 0.0;

    private boolean LaughTale_needPopAD = true;

    private boolean LaughTale_isInPlaying = false;

    private boolean LaughTale_isAppForeground = true;
    private boolean LaughTale_isDestroyed = false;

    /** 当前激励位用 Native 顶替展示时，Unity 仍走 LaughTale_REWARD_* 回调 */
    private boolean LaughTale_showingRewardAsNative = false;

    /** Native/Inter/Reward/Splash 展示期间隐藏 Banner，全部关闭后恢复 */
    private int LaughTale_fullscreenAdRefCount = 0;

    private SharedPreferences LaughTale_dataPrefs;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Runnable mrecHideRunnable = new Runnable() {
        @Override
        public void run() {
            LaughTaleHideMRECBannerView();
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

    public static LaughTaleMediationManager getInstance() {
        if (instance == null) {
            instance = new LaughTaleMediationManager();
        }
        return instance;
    }
    private LaughTaleMediationManager() {
//        ProcessLifecycleOwner.get().getLifecycle().addObserver( this );
    }

    //////////////////////////////////////////////////////////////////////初始化////////////////////////////////////////////////////////////////////////////////

    public void LaughTaleInit(Context context,Activity activity,ADInitListener initListener){
        this.LaughTale_activity = activity;
        LaughTale_dataPrefs = activity.getSharedPreferences("data", Activity.MODE_PRIVATE);
        LaughTaleGetUserNoPopAd();
        LaughTaleBankManager.instance().LaughTaleInitBankManager(activity);

        String interKey = "";
        String rewardKey = "";
        String splashKey = "";
        String mrecKey = "";
        String bannerKey = "";
        String nativeKey = "";
        String sdkKey = "";
        try {
            ApplicationInfo appInfo = activity.getPackageManager()
                    .getApplicationInfo(activity.getPackageName(),
                            PackageManager.GET_META_DATA);
            interKey = appInfo.metaData.getString("LaughTale_INTERSTITIAL_ID");
            rewardKey = appInfo.metaData.getString("LaughTale_REWARDED_ID");
            nativeKey = appInfo.metaData.getString("LaughTale_NATIVE_ID");
            bannerKey = appInfo.metaData.getString("LaughTale_BANNER_ID");
            splashKey = appInfo.metaData.getString("LaughTale_SPLASH_ID");
            mrecKey = appInfo.metaData.getString("LaughTale_MREC_ID");
//            sdkKey = appInfo.metaData.getString("LaughTale_SDK_Key");
        }catch (Exception e){
        }

        String[] LaughTale_nativeIdList = nativeKey.split(";");
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
        LaughTale_bannerAdapter.LaughTale_ad_unit = bannerKey;
        LaughTale_bannerAdapter.LaughTaleInitBannerAdapter();

        LaughTale_interAdapter = new LaughTaleInterstitialAdapter();
        LaughTale_interAdapter.LaughTale_activity = activity;
        LaughTale_interAdapter.LaughTale_ad_unit = interKey;
        LaughTale_interAdapter.LaughTale_InterstitialListener = this;
        LaughTale_interAdapter.LaughTaleInitInterstitialAdapter();

        LaughTale_rewardAdapter = new LaughTaleRewardVideoAdapter();
        LaughTale_rewardAdapter.LaughTale_activity = activity;
        LaughTale_rewardAdapter.LaughTale_ad_unit = rewardKey;
        LaughTale_rewardAdapter.LaughTale_rewardVideoListener = this;
        LaughTale_rewardAdapter.LaughTaleInitRewardVideoAdapter();

        LaughTale_splashAdapter = new LaughTaleSplashAdapter();
        LaughTale_splashAdapter.LaughTale_ad_unit = splashKey;
        LaughTale_splashAdapter.LaughTale_activity = activity;
        LaughTale_splashAdapter.LaughTale_splashListener = this;
        LaughTale_splashAdapter.LaughTaleInitSplashAdapter();

        LaughTale_mrecAdapter = new LaughTaleMRECBannerAdapter();
        LaughTale_mrecAdapter.LaughTale_activity = activity;
        LaughTale_mrecAdapter.LaughTale_ad_unit = mrecKey;
        LaughTale_mrecAdapter.LaughTaleInitBannerAdapter();

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

    private boolean LaughTaleCanRequestAdsFromUmp() {
        return consentInformation == null || consentInformation.canRequestAds();
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
        boolean doNotSell = !umpPermissiveFallback
                && status == ConsentInformation.ConsentStatus.REQUIRED
                && !personalizedAllowed;
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
        AppLovinSdk.getInstance(LaughTale_activity).setMediationProvider("max");
        AppLovinSdk.initializeSdk(LaughTale_activity, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration configuration) {
                // AppLovin SDK is initialized, start loading ads
                initListener.onAdInitSuccess();
                LaughTale_isInitSuccess = true;
                LaughTaleScheduleStaggeredAdLoads();
                if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
                    AppLovinSdk.getInstance(LaughTale_activity).showMediationDebugger();
                }
            }
        });
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
        LaughTale_interAdapter.LaughTaleLoadInterstitialAd();
    }

    ///unity用
    public boolean LaughTaleIsIntertitialADReady(String scene){
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
        if (LaughTale_interAdapter.LaughTaleGetADPrice() > 0) {
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
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_CLOSE");
    }

    @Override
    public void LaughTaleOnInterstitialAdDisplayed() {
        LaughTaleOnFullscreenAdShow();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_OPEN");
    }

    //////////////////////////////////////////////////////////////////////RewardedVideo////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadRewardAd(){
        LaughTale_rewardAdapter.LaughTaleLoadRewardVideoAd();
    }

    public boolean LaughTaleIsRewardADReady(String scene){
        double rewardPrice = LaughTale_rewardAdapter.LaughTaleGetADPrice();
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
        LaughTaleCloseAllOpenNativeAds();
        bestNativeAdapter.LaughTaleShowNativeAd();
    }

    private void LaughTaleShowRewardVideoAdInternal() {
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
        double rewardPrice = LaughTale_rewardAdapter.LaughTaleGetADPrice();
        LaughTaleNativeAdapter bestNativeAdapter = LaughTaleGetBestNativeAdapter();
        double bestNativePrice = bestNativeAdapter != null ? bestNativeAdapter.LaughTaleGetADPrice() : 0.0;
        LaughTaleDecideAndShowRewardAd(rewardPrice, bestNativeAdapter, bestNativePrice);
    }

    @Override
    public void LaughTaleOnRewardVideoAdClosed() {
        LaughTaleOnFullscreenAdClosed();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_CLOSE");
    }

    @Override
    public void LaughTaleOnRewardVideoAdDisplayed() {
        LaughTaleOnFullscreenAdShow();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_OPEN");
    }

    @Override
    public void LaughTaleOnRewardVideoAdCompleted() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_COMPLETE");
    }

    //////////////////////////////////////////////////////////////////////Banner////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadBannerView(){
        LaughTale_bannerAdapter.LaughTaleLoadBannerView();
    }

    public void LaughTaleShowBannerView(){
        LaughTale_bannerAdapter.LaughTaleShowBannerView();
    }

    public void LaughTaleHideBannerView(){
        LaughTale_bannerAdapter.LaughTaleHideBannerView();
    }

    private void LaughTaleOnFullscreenAdShow() {
        if (LaughTale_fullscreenAdRefCount == 0) {
            LaughTaleHideBannerView();
        }
        LaughTale_fullscreenAdRefCount++;
    }

    private void LaughTaleOnFullscreenAdClosed() {
        if (LaughTale_fullscreenAdRefCount > 0) {
            LaughTale_fullscreenAdRefCount--;
        }
        if (LaughTale_fullscreenAdRefCount == 0) {
            LaughTaleShowBannerView();
        }
    }

    //////////////////////////////////////////////////////////////////////MREC////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadMRECBannerView(){
        LaughTale_mrecAdapter.LaughTaleLoadMRECView();
    }

    private void LaughTaleShowMRECBannerView(int type , boolean needFix){
        if (type != 2 && needFix == false){
            mainHandler.removeCallbacks(mrecHideRunnable);
            mainHandler.postDelayed(mrecHideRunnable, 1500L);
        }
        LaughTale_mrecAdapter.LaughTaleShowMRECView(type);
    }

    private void LaughTaleHideMRECBannerView(){
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
            if (adapter.LaughTaleGetADPrice() > 0 && adapter.LaughTaleCanShowNativeAd()){
                return true;
            }
        }
        return false;
    }

    private LaughTaleNativeAdapter LaughTaleGetBestNativeAdapter() {
        LaughTaleNativeAdapter bestNativeAdapter = null;
        double maxNativePrice = 0.0;
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (!adapter.LaughTaleCanShowNativeAd()) {
                continue;
            }
            double price = adapter.LaughTaleGetADPrice();
            if (price > maxNativePrice) {
                maxNativePrice = price;
                bestNativeAdapter = adapter;
            }
        }
        return bestNativeAdapter;
    }

    private void LaughTaleCloseAllOpenNativeAds() {
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (adapter.LaughTale_isOpen) {
                adapter.LaughTaleAutoHideNativeAd();
            }
        }
    }

    /** 回前台展示 Splash 前先关闭可关闭的全屏广告（Native）；插屏/激励由 SDK 托管无法强制关闭。 */
    private void LaughTaleCloseAllFullscreenAds() {
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=====LaughTaleMediatonManager", "===close fullscreen ads before splash");
        LaughTaleCloseAllOpenNativeAds();
    }

    /** Native 出价高于激励视频时，用 Native 顶替激励位 */
    private boolean LaughTaleShouldShowNativeForReward(double rewardPrice, double nativePrice) {
        return nativePrice > rewardPrice && nativePrice > 0;
    }

    @Override
    public void LaughTaleOnNativeAdClosed(){
        LaughTaleOnFullscreenAdClosed();
        if (LaughTale_showingRewardAsNative) {
            LaughTale_showingRewardAsNative = false;
            LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_COMPLETE");
            LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_CLOSE");
            return;
        }
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_NATIVE_CLOSE");
    }

    @Override
    public void LaughTaleOnNativeAdDisplayed(){
        LaughTaleOnFullscreenAdShow();
        if (LaughTale_showingRewardAsNative) {
            LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_OPEN");
            return;
        }
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_NATIVE_OPEN");
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

    private void LaughTaleDecideAndShowCollapsibleAd(LaughTaleNativeAdapter bestNativeAdapter, double interPrice, boolean useBidding) {
        boolean interReady = interPrice > 0;
        boolean nativeReady = bestNativeAdapter != null;
        double maxNativePrice = nativeReady ? bestNativeAdapter.LaughTaleGetADPrice() : 0.0;
        if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
            if (interReady && nativeReady) {
                if (new Random().nextBoolean()) {
                    LaughTale_interAdapter.LaughTaleShowInterstitialAd();
                } else {
                    bestNativeAdapter.LaughTaleShowNativeAd();
                }
            } else if (nativeReady) {
                bestNativeAdapter.LaughTaleShowNativeAd();
            } else if (interReady) {
                LaughTale_interAdapter.LaughTaleShowInterstitialAd();
            }
        } else if (useBidding) {
            if (nativeReady && interReady && interPrice > maxNativePrice * getPValueByTime()) {
                LaughTale_interAdapter.LaughTaleShowInterstitialAd();
            } else if (nativeReady) {
                bestNativeAdapter.LaughTaleShowNativeAd();
            } else if (interReady) {
                LaughTale_interAdapter.LaughTaleShowInterstitialAd();
            }
        } else if (nativeReady) {
            bestNativeAdapter.LaughTaleShowNativeAd();
        }
    }

    private void LaughTaleSmartShowCollapsibleBannerView(boolean needHighValue){
        try {
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_should_show",null);
        }catch (Exception e){
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===LaughTaleSmartShowCollapsibleBannerView");
        boolean interReady = LaughTaleIsIntertitialADReady("collapse");
        boolean nativeReady = LaughTaleIsNativeReady();
        if (needHighValue) {
            if (nativeReady || interReady) {
                LaughTaleCloseAllOpenNativeAds();
                LaughTaleDecideAndShowCollapsibleAd(
                        LaughTaleGetBestNativeAdapter(),
                        LaughTale_interAdapter.LaughTaleGetADPrice(),
                        true);
            }
        } else {
            if (nativeReady) {
                LaughTaleCloseAllOpenNativeAds();
                LaughTaleNativeAdapter bestNativeAdapter = LaughTaleGetBestNativeAdapter();
                if (bestNativeAdapter != null) {
                    bestNativeAdapter.LaughTaleShowNativeAd();
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

    public void LaughTaleShowSplashADWithLifeTime(){
        if (LaughTale_needPopAD){
            if (LaughTale_sessionLaunchCnt == 0){
                LaughTale_sessionLaunchCnt++;
                return;
            }
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase","showSplashADWithLifeTime");
            LaughTaleCloseAllFullscreenAds();
            if (LaughTaleIsSplashADReady()&& LaughTale_isInPlaying == false){
                LaughTale_splashAdapter.LaughTaleShowSplashAd("background");
            }
        }
    }

    public boolean LaughTaleIsSplashADReady(){
        if (System.currentTimeMillis() - LaughTale_mSplashLastADTime > LaughTale_mSplashADInterval){
            if (LaughTale_splashAdapter != null){
                if (LaughTale_splashAdapter.LaughTaleIsADReady()){
                    return true;
                }
            }
        }
        return false;
    }

    public void LaughTaleShowSplashADWithUnity(String scene){
        if (LaughTale_splashAdapter == null || !LaughTale_needPopAD) {
            return;
        }
        if (LaughTaleIsSplashADReady()) {
                LaughTaleCloseAllFullscreenAds();
                try {
                    JSONObject object = new JSONObject();
                    object.put("scene", scene);
                    LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("splash_show", object.toString());
                } catch (Exception e) {
                }
                LaughTale_splashAdapter.LaughTaleShowSplashAd(scene);
            } else {
                LaughTale_splashAdapter.LaughTale_autoShow = true;
                LaughTaleLoadSplashAD();
            }
    }

    public void LaughTaleCancelSplashADWithUnity(){
        if (LaughTale_splashAdapter != null){
            LaughTale_splashAdapter.LaughTale_autoShow = false;
        }
    }

    private void LaughTaleLoadSplashAD(){
        LaughTale_splashAdapter.LaughTaleLoadSplashAD();
    }

    @Override
    public void LaughTaleOnSplashAdClosed() {
        LaughTale_mSplashLastADTime = System.currentTimeMillis();
        LaughTaleOnFullscreenAdClosed();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_SPLASH_CLOSE");
    }

    @Override
    public void LaughTaleOnSplashAdDisplayed() {
        LaughTaleOnFullscreenAdShow();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_SPLASH_OPEN");
    }

    public void LaughTaleReportIsPlaying(boolean isPlaying){
        LaughTale_isInPlaying = isPlaying;
    }

    //////////////////////////////////////////////////////////////////////Lifecycle////////////////////////////////////////////////////////////////////////////////

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
        LaughTaleCloseAllFullscreenAds();
        LaughTaleTryShowPendingUnitySplash();
    }

    public void LaughTaleOnAppResume() {
        LaughTale_isAppForeground = true;
        LaughTaleCloseAllFullscreenAds();
        if (LaughTale_bannerAdapter != null) {
            LaughTale_bannerAdapter.LaughTaleResumeAutoRefresh();
        }
        if (LaughTale_mrecAdapter != null) {
            LaughTale_mrecAdapter.LaughTaleResumeAutoRefresh();
        }
        LaughTaleTryShowPendingUnitySplash();
    }

    /** 切后台时关闭正在展示的全屏 Native，与 0611bk onActivityPaused 行为一致。 */
    public void LaughTaleOnAppPause() {
        if (LaughTale_isDestroyed) {
            return;
        }
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            if (adapter.LaughTale_isOpen) {
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                        "=====LaughTaleMediatonManager", "===close native on app pause");
                LaughTaleCloseAllOpenNativeAds();
                return;
            }
        }
    }

    private void LaughTaleTryShowPendingUnitySplash() {
        if (!LaughTale_isAppForeground || LaughTale_isDestroyed
                || LaughTale_splashAdapter == null || !LaughTale_needPopAD) {
            return;
        }
        if (!LaughTale_splashAdapter.LaughTale_autoShow) {
            return;
        }
        if (!LaughTaleIsSplashADReady()) {
            return;
        }
        LaughTaleCloseAllFullscreenAds();
        LaughTale_splashAdapter.LaughTale_autoShow = false;
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                "=====LaughTaleMediatonManager", "===tryShowPendingUnitySplash");
        LaughTale_splashAdapter.LaughTaleShowSplashAd("pending");
    }

    public void LaughTaleOnAppStop() {
        mainHandler.removeCallbacks(mrecHideRunnable);
        if (LaughTale_bannerAdapter != null) {
            LaughTale_bannerAdapter.LaughTalePauseAutoRefresh();
        }
        if (LaughTale_mrecAdapter != null) {
            LaughTale_mrecAdapter.LaughTalePauseAutoRefresh();
        }
        LaughTale_isAppForeground = false;
    }

    public void LaughTaleOnAppDestroy(Activity activity) {
        LaughTale_isAppForeground = false;
        LaughTale_isDestroyed = true;
        mainHandler.removeCallbacks(mrecHideRunnable);
        mainHandler.removeCallbacks(bannerLoadRunnable);
        mainHandler.removeCallbacks(mrecLoadRunnable);
        if (LaughTale_activity == activity) {
            LaughTale_activity = null;
        }
    }

    //////////////////////////////////////////////////////////////////////Unity交互////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleSendUnityMsg(String gamaObject, String methodName, String data) {
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
        return LaughTale_splashAdapter != null && LaughTale_splashAdapter.LaughTaleIsADReady();
    }
}