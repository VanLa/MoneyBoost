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
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

//import androidx.annotation.Nullable;

public class LaughTaleMediationManager extends Activity implements LaughTaleInterstitialListener, LaughTaleRewardVideoListener, LaughTaleSplashListener, LaughTaleNativeListener {

    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private static LaughTaleMediationManager instance;

    private Activity LaughTale_activity;

    private LaughTaleInterstitialAdapter LaughTale_interAdapter;

    private LaughTaleRewardVideoAdapter LaughTale_rewardAdapter;

    private LaughTaleSplashAdapter LaughTale_splashAdapter;

    private LaughTaleMRECBannerAdapter LaughTale_mrecAdapter;

    private LaughTaleBannerAdapter LaughTale_bannerAdapter;

    private List<LaughTaleNativeAdapter> LaughTale_nativeAdapterList = new ArrayList();

    // 广告闪屏显示间隔时间, 间隔内不出现广告 ,默认5
    private long LaughTale_mSplashADInterval = 1000 * 5;
    // 上次闪屏显示广告的时间
    private long LaughTale_mSplashLastADTime = 0;

    private long LaughTale_sessionLaunchCnt = 0;

    private boolean LaughTale_isInitSuccess = false;

    private boolean LaughTale_needPopAD = true;

    private boolean LaughTale_isNativeClick = false;

    private boolean LaughTale_isInPlaying = false;

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
        AppLovinPrivacySettings.setHasUserConsent(consent, activity);
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
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_CLOSE");
    }

    @Override
    public void LaughTaleOnInterstitialAdDisplayed() {
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_INTERSTITIAL_OPEN");
    }

    //////////////////////////////////////////////////////////////////////RewardedVideo////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadRewardAd(){
        LaughTale_rewardAdapter.LaughTaleLoadRewardVideoAd();
    }

    public boolean LaughTaleIsRewardADReady(String scene){
        try {
            JSONObject object = new JSONObject();
            object.put("scene",scene);
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("reward_should_show",object.toString());
        }catch (Exception e){
        }
        if (LaughTale_rewardAdapter.LaughTaleGetADPrice() > 0){
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===IsRewardADReady");
            return true;
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
        if (LaughTale_rewardAdapter.LaughTaleGetADPrice() > 0) {
            LaughTale_rewardAdapter.LaughTaleShowRewardVideoAd();
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
        LaughTale_bannerAdapter.LaughTaleLoadBannerView();
    }

    public void LaughTaleShowBannerView(){
        LaughTale_bannerAdapter.LaughTaleShowBannerView();
    }

    public void LaughTaleHideBannerView(){
        LaughTale_bannerAdapter.LaughTaleHideBannerView();
    }

    //////////////////////////////////////////////////////////////////////MREC////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadMRECBannerView(){
        LaughTale_mrecAdapter.LaughTaleLoadMRECView();
    }

    private void LaughTaleShowMRECBannerView(int type , boolean needFix){
        if (type != 2 && needFix == false){
            Executors.newSingleThreadScheduledExecutor()
                    .schedule(() -> LaughTaleHideMRECBannerView(), 1500, TimeUnit.MILLISECONDS);
        }
        LaughTale_mrecAdapter.LaughTaleShowMRECView(type);
    }

    private void LaughTaleHideMRECBannerView(){
        LaughTale_mrecAdapter.LaughTaleHideMRECView();
    }

    //////////////////////////////////////////////////////////////////////Native////////////////////////////////////////////////////////////////////////////////

    private void LaughTaleLoadNativeView(){
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
            adapter.LaughTaleLoadNativeAdView();
        }
    }

    private boolean LaughTaleIsNativeReady(){
        for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
            if (adapter.LaughTaleGetADPrice() > 0){
                return true;
            }
        }
        return false;
    }

    public void LaughTaleOnNativeAdClosed(){
        LaughTale_isNativeClick = false;
        LaughTaleShowBannerView();
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_NATIVE_CLOSE");
    }

    public void LaughTaleOnNativeAdDisplayed(){
        LaughTale_isNativeClick = false;
        LaughTaleSendUnityMsg("LaughTaleADManager", "LaughTaleCallback", "LaughTale_NATIVE_OPEN");
    }

    public void LaughTaleOnNativeAdClick(){
        LaughTale_isNativeClick = true;
    }

    /**
     * 按 UTC 时间判断是否为周二、周三、周四
     */
    private boolean isLowRevenueWeekdayUTC() {
        // 强制使用 UTC 时区，不受手机本地时区影响
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        int dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK);

        // Calendar.TUESDAY = 3, WEDNESDAY = 4, THURSDAY = 5
        return dayOfWeek == Calendar.TUESDAY
                || dayOfWeek == Calendar.WEDNESDAY
                || dayOfWeek == Calendar.THURSDAY;
    }

    /**
     * 获取当前 P 值
     */
    private double getPValueByTime() {
        // 如果是 UTC 的周二到周四，返回周内低杠杆 P 值
        if (isLowRevenueWeekdayUTC()) {
            return LaughTaleFirebaseManager.instance().LaughTale_p_weekday;
        }
        // 其余时间（周五到周一）返回基准 P 值
        return LaughTaleFirebaseManager.instance().LaughTale_p_weekend;
    }

    private void LaughTaleSmartShowCollapsibleBannerView(boolean needHighValue){
        try {
            LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("collapsibleBanner_should_show",null);
        }catch (Exception e){
        }
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleMediatonManager","===LaughTaleSmartShowCollapsibleBannerView");
        if (needHighValue){
            //考虑是否要比价
            if (LaughTaleIsNativeReady()||LaughTaleIsIntertitialADReady("collapse")) {
                //先关闭当前的
                for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
                    if (adapter.LaughTale_isOpen) {
                        adapter.LaughTaleAutoHideNativeAd();
                    }
                }
                // 选出价值最高的Native
                LaughTaleNativeAdapter bestNativeAdapter = null;
                double maxNativePrice = 0.0;

                for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
                    double price = adapter.LaughTaleGetADPrice();
                    if (price > maxNativePrice) {
                        maxNativePrice = price;
                        bestNativeAdapter = adapter;
                    }
                }
                //需要比价inter
                double interPrice = LaughTale_interAdapter.LaughTaleGetADPrice();
                if (bestNativeAdapter != null && interPrice * LaughTaleToolsManager.instance().LaughTale_isTestInterNativeBidder > maxNativePrice * getPValueByTime() ) {
                    LaughTale_interAdapter.LaughTaleShowInterstitialAd();
                } else if (bestNativeAdapter != null) {
                    LaughTaleHideBannerView();
                    bestNativeAdapter.LaughTaleShowNativeAd();
                }
            }
        }else {
            //对inter要求比较高的点位
            if (LaughTaleIsNativeReady()){
                //先关闭当前的
                for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
                    if (adapter.LaughTale_isOpen){
                        adapter.LaughTaleAutoHideNativeAd();
                    }
                }
                // 选出价值最高的Native
                LaughTaleNativeAdapter bestNativeAdapter = null;
                double maxNativePrice = 0.0;
                for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList) {
                    double price = adapter.LaughTaleGetADPrice();
                    if (price > maxNativePrice) {
                        maxNativePrice = price;
                        bestNativeAdapter = adapter;
                    }
                }
                if (bestNativeAdapter != null){
                    LaughTaleHideBannerView();
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
            for (LaughTaleNativeAdapter adapter : LaughTale_nativeAdapterList){
                if (adapter.LaughTale_isOpen){
                    if (LaughTale_isNativeClick == true) {
                        adapter.LaughTaleAutoHideNativeAd();
                    }
                    return;
                }
            }
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

    public void LaughTaleReportIsPlaying(boolean isPlaying){
        LaughTale_isInPlaying = isPlaying;
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