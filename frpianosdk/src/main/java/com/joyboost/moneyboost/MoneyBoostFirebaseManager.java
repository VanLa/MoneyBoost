package com.joyboost.moneyboost;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
//import com.posthog.android.Properties;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MoneyBoostFirebaseManager {
    public static MoneyBoostFirebaseManager instance;
    private FirebaseRemoteConfig MoneyBoostFirebaseRemoteConfig;
    private FirebaseAnalytics MoneyBoostFirebaseAnalytics;
    private Context mContext;

    public String MoneyBoost_cp_config = "";
    private SharedPreferences MoneyBoost_taichiPref;
    private SharedPreferences.Editor MoneyBoost_taichiSharedPreferencesEditor;


    public interface ConfigLoadListener{
        void onLoadSuccess(String json);
    }

    public static MoneyBoostFirebaseManager instance() {
        if (null == instance) {
            instance = new MoneyBoostFirebaseManager();
        }
        return instance;
    }

    public void MoneyBoostInitFirebaseAnalytics(Context context){
        mContext = context;
        MoneyBoostFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        MoneyBoostFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        //上报内存
        String ram = MoneyBoostToolsManager.instance().MoneyBoostGetTotalRam();
        MoneyBoostLogFirebaseUserProperty("Ram",ram);

        //上报是否新用户
        String isNew = MoneyBoostToolsManager.instance().MoneyBoostGetIsNewUser(context);
        MoneyBoostLogFirebaseUserProperty("IsNew",isNew);

        MoneyBoost_taichiPref = context.getApplicationContext().getSharedPreferences("TaichiTroasCache",0);
        MoneyBoost_taichiSharedPreferencesEditor = MoneyBoost_taichiPref.edit();

        //获取firebase
        MoneyBoostFetchRemoteConfig(context);
    }

    public void MoneyBoostApplyConsentFromUmp(boolean canRequestAds, boolean personalizedAllowed) {
        if (MoneyBoostFirebaseAnalytics == null) {
            return;
        }
        FirebaseAnalytics.ConsentStatus adStatus = canRequestAds
                ? FirebaseAnalytics.ConsentStatus.GRANTED
                : FirebaseAnalytics.ConsentStatus.DENIED;
        FirebaseAnalytics.ConsentStatus analyticsStatus = canRequestAds
                ? FirebaseAnalytics.ConsentStatus.GRANTED
                : FirebaseAnalytics.ConsentStatus.DENIED;
        Map<FirebaseAnalytics.ConsentType, FirebaseAnalytics.ConsentStatus> consentMap =
                new EnumMap<>(FirebaseAnalytics.ConsentType.class);
        consentMap.put(FirebaseAnalytics.ConsentType.AD_STORAGE, adStatus);
        consentMap.put(FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE, analyticsStatus);
        MoneyBoostFirebaseAnalytics.setConsent(consentMap);
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostFirebase",
                "ApplyConsentFromUmp canRequestAds=" + canRequestAds
                        + " personalized=" + personalizedAllowed
                        + " analytics=" + analyticsStatus);
    }

    public void MoneyBoostFetchFirebaseRemoteJson(Context context, String key, ConfigLoadListener listener){
        try {
            MoneyBoostFirebaseRemoteConfig.fetchAndActivate()
                    .addOnCompleteListener((Activity) context, new OnCompleteListener<Boolean>() {
                        @Override
                        public void onComplete(@NonNull Task<Boolean> task) {
                            if (task.isSuccessful()) {
                                String json = MoneyBoostFirebaseRemoteConfig.getString(key);
                                MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("===MoneyBoostFirebaseRemoteConfig===",json);
                                listener.onLoadSuccess(json);
                            }
                        }
                    });
        }catch (Exception e){

        }
    }

    private void MoneyBoostFetchRemoteConfig(Context context){
        try {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("===MoneyBoostFirebaseRemoteConfig===:","MoneyBoostFetchCPRemoteJson");
            MoneyBoostFirebaseRemoteConfig.fetchAndActivate()
                    .addOnCompleteListener((Activity) context, new OnCompleteListener<Boolean>() {
                        @Override
                        public void onComplete(@NonNull Task<Boolean> task) {
                            if (task.isSuccessful()) {
                                String json = MoneyBoostFirebaseRemoteConfig.getString("cp_config");
                                MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("===MoneyBoostFirebaseRemoteConfig===:",json);
                                MoneyBoost_cp_config = json;
                            }
                        }
                    });
        }catch (Exception e){
        }
    }

    public void MoneyBoostLogFirebaseEvent(String eventName, @Nullable String parameters){
        try {
            if (eventName != null){
                if (parameters != null){
                    Bundle bundle = MoneyBoostJsonStringToBundle(parameters);
                    MoneyBoostFirebaseAnalytics.logEvent(eventName,bundle);
//                    com.posthog.android.Properties props = jsonToProperties(parameters);
//                    PostHog.with(mContext).capture(eventName,props);
                    MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostFirebase","logFirebaseEvent:"+eventName+"==="+bundle.toString());
                }else {
                    MoneyBoostFirebaseAnalytics.logEvent(eventName,null);
//                    PostHog.with(mContext).capture(eventName);
                    MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostFirebase","logFirebaseEvent:"+eventName);
                }
            }
        }catch (Exception e){

        }
    }

    public static Bundle MoneyBoostJsonStringToBundle(String jsonString){
        try {
            JSONObject jsonObject = toJsonObject(jsonString);
            return jsonToBundle(jsonObject);
        } catch (JSONException ignored) {

        }
        return null;
    }
    public static JSONObject toJsonObject(String jsonString) throws JSONException {
        return new JSONObject(jsonString);
    }
    public static Bundle jsonToBundle(JSONObject jsonObject) throws JSONException {
        Bundle bundle = new Bundle();
        Iterator iter = jsonObject.keys();
        while(iter.hasNext()){
            String key = (String)iter.next();
            String value = jsonObject.getString(key);
            bundle.putString(key,value);
        }
        return bundle;
    }

    public void MoneyBoostLogFirebaseUserProperty(String key,String value){
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostFirebase","UserProperty:"+key +"==="+value);
        MoneyBoostFirebaseAnalytics.setUserProperty(key, value);
        HashMap<String, Object> userProps = new HashMap<String, Object>();
        userProps.put(key, value);
//        PostHog.with(mContext).capture("setUserProps",new Properties().putValue("$set",userProps));
    }

    public void MoneyBoostLogFirebaseRevenue(Double revenue, String ad_format, String network_name, String unit_id){
        if (revenue == null) {
            return;
        }
        if (MoneyBoostFirebaseAnalytics == null && mContext != null) {
            MoneyBoostFirebaseAnalytics = FirebaseAnalytics.getInstance(mContext);
        }
        if (MoneyBoostFirebaseAnalytics == null) {
            return;
        }
        double currentImpressionRevenue = revenue;
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.AD_PLATFORM, "appLovin");
        params.putString(FirebaseAnalytics.Param.AD_SOURCE, network_name);
        params.putString(FirebaseAnalytics.Param.AD_FORMAT, ad_format);
        params.putString(FirebaseAnalytics.Param.AD_UNIT_NAME, unit_id);
        params.putDouble(FirebaseAnalytics.Param.VALUE, revenue);
        params.putString(FirebaseAnalytics.Param.CURRENCY, "USD");
        MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostFirebase","Ad_Impression_Revenue:"+ad_format+"==="+revenue);
        //Aro
        MoneyBoostFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params);
        //Taichi
        MoneyBoostFirebaseAnalytics.logEvent("Ad_Impression_Revenue", params);// 给Taichi用
        if (MoneyBoost_taichiPref == null || MoneyBoost_taichiSharedPreferencesEditor == null) {
            return;
        }
        float previousTaichiTroasCache = MoneyBoost_taichiPref.getFloat("TaichiTroasCache", 0); //App本地存储用于累计tROAS的缓存值,sharedPref只是作为事例，可以选择其它本地存储的方式
        float currentTaichiTroasCache = (float) (previousTaichiTroasCache +
                currentImpressionRevenue);//累加tROAS的缓存值
//check是否应该发送TaichitROAS事件
        if (currentTaichiTroasCache >= 0.01) {//如果超过0.01就触发一次tROAS taichi事件
            Bundle roasbundle = new Bundle();
            roasbundle.putDouble(FirebaseAnalytics.Param.VALUE,
                    currentTaichiTroasCache);//(Required)tROAS事件必须带Double类型的Value
            roasbundle.putString(FirebaseAnalytics.Param.CURRENCY, "USD");//(Required)tROAS事件必须 带Currency的币种，如果是USD的话，就写USD，如果不是USD，务必把其他币种换算成USD
            MoneyBoostFirebaseAnalytics.logEvent("Total_Avenue_001", roasbundle); // 给Taichi用
            MoneyBoost_taichiSharedPreferencesEditor.putFloat("TaichiTroasCache", 0);//重新清零，开始计算
        } else {
            MoneyBoost_taichiSharedPreferencesEditor.putFloat("TaichiTroasCache", currentTaichiTroasCache);//先存着直到超过0.01才发送
        }
        MoneyBoost_taichiSharedPreferencesEditor.apply();
    }

}
