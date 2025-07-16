package com.dream.laughtale;
//import com.unity3d.player.UnityPlayer;

import android.app.Activity;
import android.content.Context;

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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

//import androidx.annotation.Nullable;

public class LaughTaleMediationManager extends Activity implements LaughTaleInterstitialListener, LaughTaleRewardVideoListener, LaughTaleSplashListener, LaughTaleNativeListener ,LaughTaleFullNativeListener {

    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private static LaughTaleMediationManager instance;

    private Activity LaughTale_activity;

    private List<LaughTaleInterstitialAdapter> LaughTale_interAdapterList = new ArrayList();

    private List<LaughTaleRewardVideoAdapter> LaughTale_rewardAdapterList = new ArrayList();

    private LaughTaleSplashAdapter LaughTale_splashAdapter;

    private List<LaughTaleMRECBannerAdapter> LaughTale_mrecAdapterList = new ArrayList();

    private List<LaughTaleBannerAdapter> LaughTale_bannerAdapterList = new ArrayList();

    private List<LaughTaleNativeAdapter> LaughTale_nativeAdapterList = new ArrayList();

    private List<LaughTaleFullNativeAdapter> LaughTale_fullNativeAdapterList = new ArrayList();

    // 广告闪屏显示间隔时间, 间隔内不出现广告 ,默认20
    private long LaughTale_mSplashADInterval = 1000 * 3;
    // 上次闪屏显示广告的时间
    private long LaughTale_mSplashLastADTime = 0;

    private long LaughTale_sessionLaunchCnt = 0;

    private boolean LaughTale_isInitSuccess = false;

    private boolean LaughTale_needPopAD = true;

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
        LaughTaleGetUserNoPopAd();

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
            sdkKey = appInfo.metaData.getString("LaughTale_SDK_Key");
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

        String[] LaughTale_fullNativeIdList = nativeKey.split(";");
        for (String str : LaughTale_nativeIdList){
            LaughTaleFullNativeAdapter adapter = new LaughTaleFullNativeAdapter();
            adapter.LaughTale_activity = activity;
            adapter.LaughTale_ad_unit = str;
            adapter.LaughTale_fullNativeListener = this;
            adapter.LaughTaleInitNativeAdapter();
            LaughTale_fullNativeAdapterList.add(adapter);
        }

        String[] LaughTale_bannerIdList = bannerKey.split(";");
        for (String str : LaughTale_bannerIdList) {
            LaughTaleBannerAdapter adapter = new LaughTaleBannerAdapter();
            adapter.LaughTale_activity = activity;
            adapter.LaughTale_ad_unit = str;
            adapter.LaughTaleInitBannerAdapter();
            LaughTale_bannerAdapterList.add(adapter);
        }


        String[] LaughTale_interIdList = interKey.split(";");
        for (String str : LaughTale_interIdList){
            LaughTaleInterstitialAdapter adapter = new LaughTaleInterstitialAdapter();
            adapter.LaughTale_activity = activity;
            adapter.LaughTale_ad_unit = str;
            adapter.LaughTale_InterstitialListener = this;
            adapter.LaughTaleInitInterstitialAdapter();
            LaughTale_interAdapterList.add(adapter);
        }

        String[] LaughTale_rewardIdList = rewardKey.split(";");
        for (String str : LaughTale_rewardIdList) {
            LaughTaleRewardVideoAdapter adapter = new LaughTaleRewardVideoAdapter();
            adapter.LaughTale_activity = activity;
            adapter.LaughTale_ad_unit = str;
            adapter.LaughTale_rewardVideoListener = this;
            adapter.LaughTaleInitRewardVideoAdapter();
            LaughTale_rewardAdapterList.add(adapter);
        }

        LaughTale_splashAdapter = new LaughTaleSplashAdapter();
        LaughTale_splashAdapter.LaughTale_ad_unit = splashKey;
        LaughTale_splashAdapter.LaughTale_activity = activity;
        LaughTale_splashAdapter.LaughTale_splashListener = this;
        LaughTale_splashAdapter.LaughTaleInitSplashAdapter();

        String[] LaughTale_mrecIdList = mrecKey.split(";");
        for (String str : LaughTale_mrecIdList) {
            LaughTaleMRECBannerAdapter adapter = new LaughTaleMRECBannerAdapter();
            adapter.LaughTale_activity = activity;
            adapter.LaughTale_ad_unit = str;
            adapter.LaughTaleInitBannerAdapter();
            LaughTale_mrecAdapterList.add(adapter);
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
                                    if (consentInformation.canRequestAds()) {
                                        LaughTaleInitMaxAd(activity,initListener,true, finalSdkKey);
                                    }else {
                                        LaughTaleInitMaxAd(activity,initListener,false, finalSdkKey);
                                    }
                                }
                        );
                    },
                    (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                        // Consent gathering failed.
                        Log.w("=====UMP", String.format("%s: %s",
                                requestConsentError.getErrorCode(),
                                requestConsentError.getMessage()));
                    });
            if (consentInformation.canRequestAds()) {
                LaughTaleInitMaxAd(activity,initListener,true,sdkKey);
            }else {
                LaughTaleInitMaxAd(activity,initListener,false,sdkKey);
            }
        }catch (Exception e){
            LaughTaleInitMaxAd(activity,initListener,true,sdkKey);
        }
    }

    private void LaughTaleInitMaxAd(Activity activity,ADInitListener initListener,Boolean consent,String sdkKey){
        if (LaughTale_isInitSuccess){
            return;
        }
        //Firbase
        LaughTaleFirebaseManager.instance().LaughTaleSetGDPRConsent();
        LaughTaleBankManager.instance().LaughTaleInitBankManager(activity);
        AppLovinPrivacySettings.setHasUserConsent(consent, activity);

//        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder( sdkKey )
//                .setMediationProvider( AppLovinMediationProvider.MAX )
//                .build();
//
//        AppLovinSdk.getInstance( LaughTale_activity ).initialize( initConfig, new AppLovinSdk.SdkInitializationListener()
//        {
//            @Override
//            public void onSdkInitialized(final AppLovinSdkConfiguration sdkConfig)
//            {
//                // Start loading ads
//                initListener.onAdInitSuccess();
//                LaughTale_isInitSuccess = true;
//                LaughTaleLoadRewardAd();
//                LaughTaleLoadInterstitialAd();
//                LaughTaleLoadSplashAD();
//                LaughTaleLoadMRECBannerView();
//                LaughTaleLoadBannerView();
//                LaughTaleLoadNativeView();
//                if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
//                    AppLovinSdk.getInstance(LaughTale_activity).showMediationDebugger();
//                }
//            }
//        } );

        AppLovinSdk.getInstance(LaughTale_activity).setMediationProvider("max");
        AppLovinSdk.initializeSdk(LaughTale_activity, new AppLovinSdk.SdkInitializationListener() {
            @Override
            public void onSdkInitialized(final AppLovinSdkConfiguration configuration) {
                // AppLovin SDK is initialized, start loading ads
                initListener.onAdInitSuccess();
                LaughTale_isInitSuccess = true;
                LaughTaleLoadRewardAd();
                LaughTaleLoadInterstitialAd();
                LaughTaleLoadSplashAD();
                LaughTaleLoadMRECBannerView();
                LaughTaleLoadBannerView();
                LaughTaleLoadNativeView();
                if (LaughTaleToolsManager.instance().LaughTale_isDebug) {
                    AppLovinSdk.getInstance(LaughTale_activity).showMediationDebugger();
                }
            }
        });
    }

    //////////////////////////////////////////////////////////////////////Interstitial////////////////////////////////////////////////////////////////////////////////


    private void LaughTaleLoadInterstitialAd(){
        for (LaughTaleInterstitialAdapter adapter : LaughTale_interAdapterList) {
            adapter.LaughTaleLoadInterstitialAd();
        }
    }

    ///unity用
    public boolean LaughTaleIsIntertitialADReady(String scene){
        for (LaughTaleInterstitialAdapter adapter : LaughTale_interAdapterList) {
            if (adapter.LaughTaleGetADPrice() > 0) {
                return true;
            }
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
        for (LaughTaleInterstitialAdapter adapter : LaughTale_interAdapterList) {
            if (adapter.LaughTaleGetADPrice() > 0) {
                return true;
            }
        }
        return false;
    }

    public void LaughTaleShowInterstitialUnity(String scene) {
        if (LaughTaleIsIntertitialADReadyLog(scene) || LaughTaleIsFullNativeReady()) {
            try {
                JSONObject object = new JSONObject();
                object.put("scene", scene);
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("inter_show", object.toString());
            } catch (Exception e) { }

//            // 找出价值最高的插屏广告（仅限 price > 0）
//            LaughTaleInterstitialAdapter bestAdapter = null;
//            double maxPrice = 0;
//
//            for (LaughTaleInterstitialAdapter adapter : LaughTale_interAdapterList) {
//                double price = adapter.LaughTaleGetADPrice();
//                if (price > maxPrice) {
//                    maxPrice = price;
//                    bestAdapter = adapter;
//                }
//            }
//
//            if (bestAdapter != null) {
//                bestAdapter.LaughTaleShowInterstitialAd();
//            }
            // 找出价值最高的广告（包括插屏和全屏原生，仅限 price > 0）
            Object bestAdapter = null;
            double maxPrice = 0;
            String adapterType = ""; // 用于记录是哪种类型的广告

            // 检查插屏广告
            for (LaughTaleInterstitialAdapter adapter : LaughTale_interAdapterList) {
                double price = adapter.LaughTaleGetADPrice();
                if (price > maxPrice) {
                    maxPrice = price;
                    bestAdapter = adapter;
                    adapterType = "interstitial";
                }
            }

            // 检查全屏原生广告
            for (LaughTaleFullNativeAdapter adapter : LaughTale_fullNativeAdapterList) {
                double price = adapter.LaughTaleGetADPrice();
                if (LaughTaleToolsManager.instance().LaughTale_isTestFullNative){
                    maxPrice = price;
                    bestAdapter = adapter;
                    adapterType = "fullNative";
                }else {
                    if (price > maxPrice) {
                        maxPrice = price;
                        bestAdapter = adapter;
                        adapterType = "fullNative";
                    }
                }

            }

            // 根据类型展示广告
            if (bestAdapter != null) {
                if (adapterType.equals("interstitial")) {
                    ((LaughTaleInterstitialAdapter)bestAdapter).LaughTaleShowInterstitialAd();
                } else if (adapterType.equals("fullNative")) {
                    LaughTaleHideBannerView();
                    ((LaughTaleFullNativeAdapter)bestAdapter).LaughTaleShowNativeAd();
                }
            }
        }
    }


    @Override
    public void LaughTaleOnInterstitialAdClosed() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_CLOSE");
    }

    @Override
    public void LaughTaleOnInterstitialAdDisplayed() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_OPEN");
    }

    //////////////////////////////////////////////////////////////////////RewardedVideo////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadRewardAd(){
        for (LaughTaleRewardVideoAdapter adapter : LaughTale_rewardAdapterList) {
            adapter.LaughTaleLoadRewardVideoAd();
        }
    }

    public boolean LaughTaleIsRewardADReady(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("reward_should_show",object.toString());
        }catch (Exception e){
        }
        for (LaughTaleRewardVideoAdapter adapter : LaughTale_rewardAdapterList) {
            if (adapter.LaughTaleGetADPrice() > 0){
                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===IsRewardADReady");
                return true;
            }
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===IsRewardADNotReady");
        return false;
    }

    public void LaughTaleShowRewardAdUnity(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("reward_show",object.toString());
        }catch (Exception e){
        }
        for (LaughTaleRewardVideoAdapter adapter : LaughTale_rewardAdapterList) {
            if (adapter.LaughTaleGetADPrice() > 0) {
                adapter.LaughTaleShowRewardVideoAd();
                return;
            }
        }
    }

    @Override
    public void LaughTaleOnRewardVideoAdClosed() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_CLOSE");
    }

    @Override
    public void LaughTaleOnRewardVideoAdDisplayed() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_OPEN");
    }

    @Override
    public void LaughTaleOnRewardVideoAdCompleted() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_REWARD_COMPLETE");
    }

    //////////////////////////////////////////////////////////////////////Banner////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadBannerView(){
        for (LaughTaleBannerAdapter adapter : LaughTale_bannerAdapterList) {
            adapter.LaughTaleLoadBannerView();
        }
    }

    public void LaughTaleShowBannerView(){
        for (LaughTaleBannerAdapter adapter : LaughTale_bannerAdapterList) {
            adapter.LaughTaleShowBannerView();
        }
    }

    public void LaughTaleHideBannerView(){
        for (LaughTaleBannerAdapter adapter : LaughTale_bannerAdapterList) {
            if (adapter.LaughTale_IsShow){
                adapter.LaughTaleHideBannerView();
            }
        }
    }

    //////////////////////////////////////////////////////////////////////MREC////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadMRECBannerView(){
        for (LaughTaleMRECBannerAdapter adapter : LaughTale_mrecAdapterList) {
            adapter.LaughTaleLoadMRECView();
        }
    }

    private void LaughTaleShowMRECBannerView(int type , boolean needFix){
        if (type != 2 && needFix == false){
            Executors.newSingleThreadScheduledExecutor()
                    .schedule(() -> LaughTaleHideMRECBannerView(), 1500, TimeUnit.MILLISECONDS);
        }
        for (LaughTaleMRECBannerAdapter adapter : LaughTale_mrecAdapterList) {
            if (adapter.LaughTale_isReady){
                adapter.LaughTaleShowMRECView(type);
                return;
            }
        }
    }

    private void LaughTaleHideMRECBannerView(){
        for (LaughTaleMRECBannerAdapter adapter : LaughTale_mrecAdapterList) {
            if (adapter.LaughTale_isOpen){
                adapter.LaughTaleHideMRECView();
            }
        }
    }

    private void LaughTaleLoadNativeView(){
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            adapter.LaughTaleLoadNativeAdView();
        }
    }

    //////////////////////////////////////////////////////////////////////Native////////////////////////////////////////////////////////////////////////////////

    private boolean LaughTaleIsNativeReady(){
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
            if (adapter.LaughTaleGetADPrice() > 0){
                return true;
            }
        }
        return false;
    }

    public void LaughTaleOnNativeAdClosed(){
        LaughTaleShowBannerView();
    }

    public void LaughTaleOnNativeAdDisplayed(){

    }

    public void LaughTaleOnNativeAdClick(){
    }

    private void LaughTaleSmartShowCollapsibleBannerView(){
        try {
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_should_show",null);
        }catch (Exception e){
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===LaughTaleSmartShowCollapsibleBannerView");
        //用折叠native
        if (LaughTaleIsNativeReady()){
            try {
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_show",null);
            }catch (Exception e){
            }
            //先关闭当前的
            for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
                if (adapter.LaughTale_isOpen){
                    adapter.LaughTaleAutoHideNativeAd();
                }
            }
            // 选出价值最高的广告进行展示（price > 0）
            LaughTaleNativeAdapter bestAdapter = null;
            double maxPrice = 0;
            for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
                double price = adapter.LaughTaleGetADPrice();
                if (price > maxPrice) {
                    maxPrice = price;
                    bestAdapter = adapter;
                }
            }

            // 展示最贵的
            if (bestAdapter != null) {
                LaughTaleHideBannerView();
                boolean modMode = LaughTaleBankManager.instance().getIsOldUser();
                bestAdapter.LaughTaleShowNativeAd(modMode);
//          startTimer(); // 如果你需要控制生命周期，可以解开
            }
        }
    }

//    private void startTimer() {
//        if (countDownTimer != null){
//            countDownTimer.cancel(); // 取消之前的计时器（如果有）
//            countDownTimer.start(); // 启动计时器
//        }
//    }

    public void LaughTaleShowCollapsibleBannerView(){
        LaughTaleSmartShowCollapsibleBannerView();
    }

    public void LaughTaleHideCollapsibleBannerView(){
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===LaughTaleHideBannerView");
//        LaughTale_collapsibleBannerAdapter.LaughTaleHideBannerView();
    }
    //////////////////////////////////////////////////////////////////////FullNative////////////////////////////////////////////////////////////////////////////////

    public void LaughTaleOnFullNativeAdClosed(){
        LaughTaleShowBannerView();
    }
    public void LaughTaleOnFullNativeAdDisplayed(){

    }
    public void LaughTaleOnFullNativeAdClick(){

    }

    private void LaughTaleSmartShowFullNativeView(){
        try {
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_should_show",null);
        }catch (Exception e){
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===LaughTaleSmartShowCollapsibleBannerView");
        //用折叠native
        if (LaughTaleIsFullNativeReady()){
            try {
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_show",null);
            }catch (Exception e){
            }
            //先关闭当前的
            for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
                if (adapter.LaughTale_isOpen){
                    adapter.LaughTaleAutoHideNativeAd();
                }
            }
            // 选出价值最高的广告进行展示（price > 0）
            LaughTaleNativeAdapter bestAdapter = null;
            double maxPrice = 0;
            for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
                double price = adapter.LaughTaleGetADPrice();
                if (price > maxPrice) {
                    maxPrice = price;
                    bestAdapter = adapter;
                }
            }

            // 展示最贵的
            if (bestAdapter != null) {
                LaughTaleHideBannerView();
                boolean modMode = LaughTaleBankManager.instance().getIsOldUser();
                bestAdapter.LaughTaleShowNativeAd(modMode);
//          startTimer(); // 如果你需要控制生命周期，可以解开
            }
        }
    }



    //////////////////////////////////////////////////////////////////////Splash////////////////////////////////////////////////////////////////////////////////

    private boolean LaughTaleIsFullNativeReady(){
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
            if (adapter.LaughTaleGetADPrice() > 0){
                return true;
            }
        }
        return false;
    }

    public void LaughTaleShowSplashADWithLifeTime(){
        if (LaughTale_needPopAD){
            if (LaughTale_sessionLaunchCnt == 0){
                LaughTale_sessionLaunchCnt++;
                return;
            }
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase","showSplashADWithLifeTime");
            for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
                if (adapter.LaughTale_isOpen){
                    adapter.LaughTaleAutoHideNativeAd();
                }
            }
            for (LaughTaleFullNativeAdapter adapter : LaughTale_fullNativeAdapterList){
                if (adapter.LaughTale_isOpen){
                    adapter.LaughTaleAutoHideNativeAd();
                }
            }
            if (LaughTaleIsSplashADReady()){
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
        if (LaughTale_splashAdapter != null && LaughTale_needPopAD){
            if (LaughTaleIsSplashADReady()) {
                try {
                    JSONObject object = new JSONObject();
                    object.put("scene", scene);
                    LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("splash_show", object.toString());
                } catch (Exception e) {
                }
                LaughTale_splashAdapter.LaughTaleShowSplashAd(scene);
            }else {
                LaughTale_splashAdapter.LaughTale_autoShow = true;
            }
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
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_SPLASH_CLOSE");
    }

    @Override
    public void LaughTaleOnSplashAdDisplayed() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_SPLASH_OPEN");
    }
    //////////////////////////////////////////////////////////////////////Unity交互////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleSendUnityMsg(String gamaObject, String methodName, String data) {
        if (gamaObject.length() == 0 || methodName.length() == 0){
            return;
        }else {
            try {
                Class unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
                Method initMethod = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
                initMethod.invoke(null, gamaObject, methodName, data);
            } catch (Exception e) {
            }
        }
    }

    //////////////////////////////////////////////////////////////////////tools////////////////////////////////////////////////////////////////////////////////

    public void LaughTaleSetUserNoPopAd(){
        SharedPreferences sharedPreferences = LaughTale_activity.getSharedPreferences("data", LaughTale_activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putInt("noPop", 1);
        editor.commit();
        LaughTale_needPopAD = false;
        //关闭现在的广告
        LaughTaleHideBannerView();
        LaughTaleHideMRECBannerView();
    }

    public void LaughTaleGetUserNoPopAd(){
        SharedPreferences sharedPreferences = LaughTale_activity.getSharedPreferences("data", LaughTale_activity.MODE_PRIVATE);
        int launch = sharedPreferences.getInt("noPop", 0);
        if (launch != 0){
            LaughTale_needPopAD = false;
        }else {
            LaughTale_needPopAD = true;
        }
    }

}