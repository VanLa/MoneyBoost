package com.dream.moneyboost;

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

public class MoneyBoostFirebaseManager {
    public static MoneyBoostFirebaseManager instance;
    private FirebaseRemoteConfig MoneyBoostFirebaseRemoteConfig;
    private FirebaseAnalytics MoneyBoostFirebaseAnalytics;
    private Context mContext;
    private String MoneyBoost_resource_dic = "";
    private String MoneyBoost_resource_root = "";
    private OSSClient oss;
    private String MoneyBoost_Endpoint = "";
    private String MoneyBoost_AccessKeyId = "";
    private String MoneyBoost_AccessKeySecret = "";

    public String MoneyBoost_cp_config = "";
    private SharedPreferences MoneyBoost_taichiPref;
    private SharedPreferences.Editor MoneyBoost_taichiSharedPreferencesEditor;


    public interface StorageLoadListener{
        void onLoadSuccess(String path);
        void onLoadFailure(String error);
    }

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

    public void MoneyBoostInitStorage(Context context){
        mContext = context;
        OSSPlainTextAKSKCredentialProvider provider = new OSSPlainTextAKSKCredentialProvider(MoneyBoost_AccessKeyId,MoneyBoost_AccessKeySecret);
        oss = new OSSClient(mContext,MoneyBoost_Endpoint,provider);
    }

    public void MoneyBoostGetStorageFireWithFilePath(String fileName,String fileSuf,StorageLoadListener listener){
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
            if (MoneyBoost_resource_dic.equals("")){
                try {
                    ApplicationInfo appInfo = mContext.getPackageManager()
                            .getApplicationInfo(mContext.getPackageName(),
                                    PackageManager.GET_META_DATA);
                    MoneyBoost_resource_root = appInfo.metaData.getString("MoneyBoost_RESOURCE_PROJECT") + "/";
                    MoneyBoost_resource_dic = appInfo.metaData.getString("MoneyBoost_RESOURCE_ROOT") + "/";
                }catch (Exception e){
                }
            }
            pathName = MoneyBoost_resource_root + MoneyBoost_resource_dic + pathName;

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
                        MoneyBoostCopyAssetGetFilePath(fileName+"."+fileSuf);
                        File cacheFile = new File(mContext.getExternalCacheDir(),fileName+"."+fileSuf);
                        try {
                            if (cacheFile.exists() && MoneyBoostGetFileSize(cacheFile)>0){
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
            MoneyBoostCopyAssetGetFilePath(fileName+"."+fileSuf);
            File cacheFile = new File(mContext.getExternalCacheDir(),fileName+"."+fileSuf);
            if (cacheFile == null){
                return;
            }
            if (cacheFile.exists()){
                listener.onLoadSuccess(cacheFile.getPath());
            }
        }
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

    private static long MoneyBoostGetFileSize(File file) throws Exception
    {
        long size = 0;
        if (file.exists()){
            FileInputStream fis = null;
            fis = new FileInputStream(file);
            size = fis.available();
        }
        return size;
    }

    private void MoneyBoostCopyAssetGetFilePath(String fileName) {
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
