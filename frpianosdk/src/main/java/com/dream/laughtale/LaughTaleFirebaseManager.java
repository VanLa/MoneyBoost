package com.dream.laughtale;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import androidx.annotation.NonNull;

import com.alibaba.sdk.android.oss.ClientException;
import com.alibaba.sdk.android.oss.OSSClient;
import com.alibaba.sdk.android.oss.ServiceException;
import com.alibaba.sdk.android.oss.callback.OSSCompletedCallback;
import com.alibaba.sdk.android.oss.common.auth.OSSPlainTextAKSKCredentialProvider;
import com.alibaba.sdk.android.oss.model.GetObjectRequest;
import com.alibaba.sdk.android.oss.model.GetObjectResult;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
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
    private String LaughTale_resource_dic = "";
    private String LaughTale_resource_root = "";
    private OSSClient oss;
    private String LaughTale_Endpoint = "https://musemania.top";
    private String LaughTale_AccessKeyId = "REDACTED_ALIBABA_ACCESS_KEY_ID";
    private String LaughTale_AccessKeySecret = "REDACTED_ALIBABA_ACCESS_KEY_SECRET";

    public String LaughTale_cp_config = "";
    public double LaughTale_p_weekday = 1.0;
    public double LaughTale_p_weekend = 1.0;

    /** Native 关闭钮尺寸 Remote Config 原始 JSON，key: native_close_size_config */
    public String LaughTale_native_close_size_config = "";
    /** 统一关闭钮尺寸（dp），不区分 Meta */
    public float LaughTale_native_close_size = LaughTaleLayoutSize.DEFAULT_CLOSE_SIZE_DP;

    private static final String RC_KEY_NATIVE_CLOSE_SIZE_CONFIG = "native_close_size_config";

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

        LaughTaleSyncNativeLayoutFromPrefs();
        //获取firebase
        LaughTaleFetchRemoteConfig(context);
    }

    private void LaughTaleSyncNativeLayoutFromPrefs() {
        if (mContext == null) {
            return;
        }
        LaughTaleLayoutSize size = new LaughTaleLayoutSize(mContext);
        LaughTale_native_close_size = size.closeSize;
    }

    public void LaughTaleInitStorage(Context context){
        mContext = context;
        OSSPlainTextAKSKCredentialProvider provider = new OSSPlainTextAKSKCredentialProvider(LaughTale_AccessKeyId,LaughTale_AccessKeySecret);
        oss = new OSSClient(mContext,LaughTale_Endpoint,provider);
    }

    public void LaughTaleGetStorageFireWithFilePath(String fileName,String fileSuf,StorageLoadListener listener){
        File finalFile = new File(mContext.getExternalCacheDir(),fileName+"."+fileSuf);
        try {
            finalFile.createNewFile();
            try {
                // 创建一个 FileOutputStream 实例，第二个参数为 false 表示不追加内容
                FileOutputStream fos = new FileOutputStream(finalFile, false);
                // 关闭 FileOutputStream
                fos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            String pathName = fileName;
            if (LaughTale_resource_dic.equals("")){
                try {
                    ApplicationInfo appInfo = mContext.getPackageManager()
                            .getApplicationInfo(mContext.getPackageName(),
                                    PackageManager.GET_META_DATA);
                    LaughTale_resource_root = appInfo.metaData.getString("LaughTale_RESOURCE_PROJECT") + "/";
                    LaughTale_resource_dic = appInfo.metaData.getString("LaughTale_RESOURCE_ROOT") + "/";
                }catch (Exception e){
                }
            }
            pathName = LaughTale_resource_root + LaughTale_resource_dic + pathName;

            GetObjectRequest get = new GetObjectRequest("musemania-casual",pathName+"."+fileSuf);
            oss.asyncGetObject(get, new OSSCompletedCallback<GetObjectRequest, GetObjectResult>() {
                @Override
                public void onSuccess(GetObjectRequest request, GetObjectResult result) {
                    // 开始读取数据。
                    long length = result.getContentLength();
                    if (length > 0) {
                        byte[] buffer = new byte[(int) length];
                        int readCount = 0;
                        while (readCount < length) {
                            try{
                                readCount += result.getObjectContent().read(buffer, readCount, (int) length - readCount);
                            }catch (Exception e){
                            }
                        }
                        // 将下载后的文件存放在指定的本地路径，例如D:\\localpath\\exampleobject.jpg。
                        try {
                            FileOutputStream fout = new FileOutputStream(finalFile);
                            fout.write(buffer);
                            fout.close();
                            listener.onLoadSuccess(finalFile.getPath());
                        } catch (Exception e) {
                        }
                    }
                }

                @Override
                public void onFailure(GetObjectRequest request, ClientException clientException, ServiceException serviceException) {
                    // 请求异常。
                    if (clientException != null) {
                        // 本地异常，如网络异常等。
                        clientException.printStackTrace();
                    }
                    if (serviceException != null) {
                        // 服务异常。
                        Log.e("ErrorCode", serviceException.getErrorCode());
                        Log.e("RequestId", serviceException.getRequestId());
                        Log.e("HostId", serviceException.getHostId());
                        Log.e("RawMessage", serviceException.getRawMessage());
                    }
                    if(fileSuf.equals("ogg") == false){
                        LaughTaleCopyAssetGetFilePath(fileName+"."+fileSuf);
                        File cacheFile = new File(mContext.getExternalCacheDir(),fileName+"."+fileSuf);
                        try {
                            if (cacheFile.exists() && LaughTaleGetFileSize(cacheFile)>0){
                                listener.onLoadSuccess(cacheFile.getPath());
                            }else {
                                listener.onLoadFailure("=====GetObjectFailure:"+fileName);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            });
        }catch (IOException ex){
            LaughTaleCopyAssetGetFilePath(fileName+"."+fileSuf);
            File cacheFile = new File(mContext.getExternalCacheDir(),fileName+"."+fileSuf);
            if (cacheFile == null){
                return;
            }
            if (cacheFile.exists()){
                listener.onLoadSuccess(cacheFile.getPath());
            }
        }
    }

    public void LaughTaleApplyConsentFromUmp(boolean canRequestAds, boolean personalizedAllowed) {
        if (LaughTaleFirebaseAnalytics == null) {
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
        LaughTaleFirebaseAnalytics.setConsent(consentMap);
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase",
                "ApplyConsentFromUmp canRequestAds=" + canRequestAds
                        + " personalized=" + personalizedAllowed
                        + " analytics=" + analyticsStatus);
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

    private void LaughTaleFetchRemoteConfig(Context context){
        try {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("===LaughTaleFirebaseRemoteConfig===:","LaughTaleFetchCPRemoteJson");
            LaughTaleFirebaseRemoteConfig.fetchAndActivate()
                    .addOnCompleteListener((Activity) context, new OnCompleteListener<Boolean>() {
                        @Override
                        public void onComplete(@NonNull Task<Boolean> task) {
                            if (task.isSuccessful()) {
                                String json = LaughTaleFirebaseRemoteConfig.getString("cp_config");
                                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("===LaughTaleFirebaseRemoteConfig===:",json);
                                LaughTale_cp_config = json;
                                LaughTale_p_weekend = LaughTaleFirebaseRemoteConfig.getDouble("p_value_weekend");
                                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("===LaughTaleFirebaseRemoteConfig===:","===p_value_weekend===:"+LaughTale_p_weekend+"");
                                LaughTale_p_weekday = LaughTaleFirebaseRemoteConfig.getDouble("p_value_weekday");
                                LaughTaleToolsManager.instance().LaughTaleLogWithDebug("===LaughTaleFirebaseRemoteConfig===:","===p_value_weekday===:"+LaughTale_p_weekday+"");
                                LaughTaleApplyNativeLayoutRemoteConfig();
                            }
                        }
                    });
        }catch (Exception e){
        }
    }

    /**
     * Remote Config key: native_close_size_config（String JSON）
     * {"closeSize":25}（兼容旧字段 closeSize_0，但忽略 Meta 分支，只用 closeSize）
     * 未配置或取不到时沿用 {@link LaughTaleLayoutSize} 写死默认值。
     */
    private void LaughTaleApplyNativeLayoutRemoteConfig() {
        if (mContext == null) {
            return;
        }
        String json = LaughTaleFirebaseRemoteConfig.getString(RC_KEY_NATIVE_CLOSE_SIZE_CONFIG);
        if (json == null || json.trim().isEmpty()) {
            LaughTaleSyncNativeLayoutFromPrefs();
            return;
        }
        LaughTale_native_close_size_config = json;
        try {
            JSONObject object = new JSONObject(json);
            float closeSize = LaughTaleParseLayoutSizeValue(
                    object, "closeSize", LaughTaleLayoutSize.DEFAULT_CLOSE_SIZE_DP);
            LaughTale_native_close_size = closeSize;
            new LaughTaleLayoutSize(mContext).updateFromRemote(closeSize);
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "===LaughTaleFirebaseRemoteConfig===",
                    "===native_close_size_config=== " + json);
        } catch (JSONException e) {
            LaughTaleSyncNativeLayoutFromPrefs();
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug(
                    "===LaughTaleFirebaseRemoteConfig===",
                    "===native_close_size_config parse error=== " + e.getMessage());
        }
    }

    private static float LaughTaleParseLayoutSizeValue(JSONObject object, String key, float defaultValue)
            throws JSONException {
        if (!object.has(key)) {
            return defaultValue;
        }
        float value = (float) object.getDouble(key);
        return value > 0f ? value : defaultValue;
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
        if (revenue == null) {
            return;
        }
        if (LaughTaleFirebaseAnalytics == null && mContext != null) {
            LaughTaleFirebaseAnalytics = FirebaseAnalytics.getInstance(mContext);
        }
        if (LaughTaleFirebaseAnalytics == null) {
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
        LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleFirebase","Ad_Impression_Revenue:"+ad_format+"==="+revenue);
        //Aro
        LaughTaleFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.AD_IMPRESSION, params);
        //Taichi
        LaughTaleFirebaseAnalytics.logEvent("Ad_Impression_Revenue", params);// 给Taichi用
        if (LaughTale_taichiPref == null || LaughTale_taichiSharedPreferencesEditor == null) {
            return;
        }
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
        LaughTale_taichiSharedPreferencesEditor.apply();
    }

}
