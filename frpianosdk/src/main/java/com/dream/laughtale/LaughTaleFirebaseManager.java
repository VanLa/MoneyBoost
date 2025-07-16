package com.dream.laughtale;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
//import com.posthog.android.PostHog;
//import com.posthog.android.Properties;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class LaughTaleFirebaseManager {
    public static LaughTaleFirebaseManager instance;
    private FirebaseRemoteConfig LaughTaleFirebaseRemoteConfig;
    private FirebaseAnalytics LaughTaleFirebaseAnalytics;
    private Context mContext;
//    private String LaughTale_resource_dic = "";
//    private String LaughTale_resource_root = "";

//    private String LaughTale_Endpoint = "https://moncheyplantgame.top";
//    private String LaughTale_AccessKeyId = "REDACTED_ALIBABA_ACCESS_KEY_ID";
//    private String LaughTale_AccessKeySecret = "REDACTED_ALIBABA_ACCESS_KEY_SECRET";

    public String LaughTale_cp_config = "";

    private SharedPreferences LaughTale_taichiPref;
    private SharedPreferences.Editor LaughTale_taichiSharedPreferencesEditor;


    public interface StorageLoadListener{
        void onLoadSuccess(String path);
        void onLoadFailure(String error);
    }

    public interface ConfigLoadListener{
        void onLoadSuccess(String json);
    }

    public static LaughTaleFirebaseManager instance() {
        if (null == instance) {
            instance = new LaughTaleFirebaseManager();
        }
        return instance;
    }

    public void LaughTaleInitFirebaseAnalytics(Context context){
        mContext = context;
        LaughTaleFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        LaughTaleFirebaseRemoteConfig = FirebaseRemoteConfig.getInstance();
        //上报内存
        String ram = LaughTaleToolsManager.instance().LaughTaleGetTotalRam();
        LaughTaleLogFirebaseUserProperty("Ram",ram);

        //上报是否新用户
        String isNew = LaughTaleToolsManager.instance().LaughTaleGetIsNewUser(context);
        LaughTaleLogFirebaseUserProperty("IsNew",isNew);

        LaughTale_taichiPref = context.getApplicationContext().getSharedPreferences("TaichiTroasCache",0);
        LaughTale_taichiSharedPreferencesEditor = LaughTale_taichiPref.edit();
        //交叉推广
        LaughTaleFetchCPRemoteJson(context);
    }

    public void LaughTaleSetGDPRConsent(){
        Map<FirebaseAnalytics.ConsentType, FirebaseAnalytics.ConsentStatus> consentMap = new EnumMap<>(FirebaseAnalytics.ConsentType.class);
        consentMap.put(FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE, FirebaseAnalytics.ConsentStatus.GRANTED);
        consentMap.put(FirebaseAnalytics.ConsentType.AD_STORAGE, FirebaseAnalytics.ConsentStatus.GRANTED);
        LaughTaleFirebaseAnalytics.setConsent(consentMap);
    }

    public void LaughTaleFetchFirebaseRemoteJson(Context context, String key, ConfigLoadListener listener){
        try {
            LaughTaleFirebaseRemoteConfig.fetchAndActivate()
                    .addOnCompleteListener((Activity) context, new OnCompleteListener<Boolean>() {
                        @Override
                        public void onComplete(@NonNull Task<Boolean> task) {
                            if (task.isSuccessful()) {
                                String json = LaughTaleFirebaseRemoteConfig.getString(key);
                                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("===LaughTaleFirebaseRemoteConfig===",json);
                                listener.onLoadSuccess(json);
                            }
                        }
                    });
        }catch (Exception e){

        }
    }

    private void LaughTaleFetchCPRemoteJson(Context context){
        try {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("===LaughTaleFirebaseCPConfig===:","LaughTaleFetchCPRemoteJson");
            LaughTaleFirebaseRemoteConfig.fetchAndActivate()
                    .addOnCompleteListener((Activity) context, new OnCompleteListener<Boolean>() {
                        @Override
                        public void onComplete(@NonNull Task<Boolean> task) {
                            if (task.isSuccessful()) {
                                String json = LaughTaleFirebaseRemoteConfig.getString("cp_config");
                                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("===LaughTaleFirebaseCPConfig===:",json);
                                LaughTale_cp_config = json;
                            }
                        }
                    });
        }catch (Exception e){
        }
    }


    private static long LaughTaleGetFileSize(File file) throws Exception
    {
        long size = 0;
        if (file.exists()){
            FileInputStream fis = null;
            fis = new FileInputStream(file);
            size = fis.available();
        }
        return size;
    }

    private void LaughTaleCopyAssetGetFilePath(String fileName) {
        try {
            File cacheDir = mContext.getExternalCacheDir();
            if (cacheDir == null){
                return;
            }
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File outFile = new File(cacheDir, fileName);
            if (outFile == null){
                return;
            }
            if (!outFile.exists()) {
                boolean res = outFile.createNewFile();
            } else {
                if (outFile.length() > 5) {//表示已经写入一次
                    return;
                }
            }
            InputStream is = mContext.getAssets().open(fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {
                fos.write(buffer, 0, byteCount);
            }
            fos.flush();
            is.close();
            fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void LaughTaleLogFirebaseEvent(String eventName, @Nullable String parameters){
        try {
            if (eventName != null){
                if (parameters != null){
                    Bundle bundle = LaughTaleJsonStringToBundle(parameters);
                    LaughTaleFirebaseAnalytics.logEvent(eventName,bundle);
//                    com.posthog.android.Properties props = jsonToProperties(parameters);
//                    PostHog.with(mContext).capture(eventName,props);
                    LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase","logFirebaseEvent:"+eventName+"==="+bundle.toString());
                }else {
                    LaughTaleFirebaseAnalytics.logEvent(eventName,null);
//                    PostHog.with(mContext).capture(eventName);
                    LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase","logFirebaseEvent:"+eventName);
                }
            }
        }catch (Exception e){

        }
    }

//    private com.posthog.android.Properties jsonToProperties(String jsonString) throws JSONException {
//        com.posthog.android.Properties retMap = new com.posthog.android.Properties();
//        JSONObject json = toJsonObject(jsonString);
//        if(json != JSONObject.NULL) {
//            retMap = toProperties(json);
//        }
//        return retMap;
//    }
//
//    private com.posthog.android.Properties toProperties(JSONObject object) throws JSONException {
//        com.posthog.android.Properties properties = new com.posthog.android.Properties();
//        Iterator<String> keysItr = object.keys();
//        while(keysItr.hasNext()) {
//            String key = keysItr.next();
//            Object value = object.get(key);
//
//            if(value instanceof JSONArray) {
//                value = toList((JSONArray) value);
//            }
//
//            else if(value instanceof JSONObject) {
//                value = toProperties((JSONObject) value);
//            }
//            properties.putValue(key, value);
//        }
//        return properties;
//    }
//
//    private List<Object> toList(JSONArray array) throws JSONException {
//        List<Object> list = new ArrayList<Object>();
//        for(int i = 0; i < array.length(); i++) {
//            Object value = array.get(i);
//            if(value instanceof JSONArray) {
//                value = toList((JSONArray) value);
//            }
//
//            else if(value instanceof JSONObject) {
//                value = toProperties((JSONObject) value);
//            }
//            list.add(value);
//        }
//        return list;
//    }

    public static Bundle LaughTaleJsonStringToBundle(String jsonString){
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

    public void LaughTaleLogFirebaseUserProperty(String key,String value){
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase","UserProperty:"+key +"==="+value);
        LaughTaleFirebaseAnalytics.setUserProperty(key, value);
        HashMap<String, Object> userProps = new HashMap<String, Object>();
        userProps.put(key, value);
//        PostHog.with(mContext).capture("setUserProps",new Properties().putValue("$set",userProps));
    }

    public void LaughTaleLogFirebaseRevenue(Double revenue, String ad_format, String network_name, String unit_id){
        double currentImpressionRevenue = revenue;
        Bundle params = new Bundle();
        params.putString(FirebaseAnalytics.Param.AD_PLATFORM, "appLovin");
        params.putString(FirebaseAnalytics.Param.AD_SOURCE, network_name);
        params.putString(FirebaseAnalytics.Param.AD_FORMAT, ad_format);
        params.putString(FirebaseAnalytics.Param.AD_UNIT_NAME, unit_id);
        params.putDouble(FirebaseAnalytics.Param.VALUE, revenue);
        params.putString(FirebaseAnalytics.Param.CURRENCY, "USD");
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase","Ad_Impression_Revenue:"+ad_format+"==="+revenue);
        //Aro
        LaughTaleFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params);
        //Taichi
        LaughTaleFirebaseAnalytics.logEvent("Ad_Impression_Revenue", params);// 给Taichi用
        float previousTaichiTroasCache = LaughTale_taichiPref.getFloat("TaichiTroasCache", 0); //App本地存储用于累计tROAS的缓存值,sharedPref只是作为事例，可以选择其它本地存储的方式
        float currentTaichiTroasCache = (float) (previousTaichiTroasCache +
                currentImpressionRevenue);//累加tROAS的缓存值
//check是否应该发送TaichitROAS事件
        if (currentTaichiTroasCache >= 0.01) {//如果超过0.01就触发一次tROAS taichi事件
            Bundle roasbundle = new Bundle();
            roasbundle.putDouble(FirebaseAnalytics.Param.VALUE,
                    currentTaichiTroasCache);//(Required)tROAS事件必须带Double类型的Value
            roasbundle.putString(FirebaseAnalytics.Param.CURRENCY, "USD");//(Required)tROAS事件必须 带Currency的币种，如果是USD的话，就写USD，如果不是USD，务必把其他币种换算成USD
            LaughTaleFirebaseAnalytics.logEvent("Total_Avenue_001", roasbundle); // 给Taichi用
            LaughTale_taichiSharedPreferencesEditor.putFloat("TaichiTroasCache", 0);//重新清零，开始计算
        } else {
            LaughTale_taichiSharedPreferencesEditor.putFloat("TaichiTroasCache", currentTaichiTroasCache);//先存着直到超过0.01才发送
        }
        LaughTale_taichiSharedPreferencesEditor.commit();
    }

}
