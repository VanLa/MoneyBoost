package com.dream.moneyboost;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

public class MoneyBoostResourceManager {
    public static MoneyBoostResourceManager instance;
    private Context MoneyBoost_mContext;

    public static MoneyBoostResourceManager instance(){
        if (null == instance){
            instance = new MoneyBoostResourceManager();
        }
        return instance;
    }

    public void MoneyBoostInitResourceManager(Context context){
        MoneyBoost_mContext = context;
        MoneyBoostFirebaseManager.instance().MoneyBoostInitStorage(context);
    }

    public void MoneyBoostSendToUnityMessage(String gamaObject, String methodName, String message){
        try {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostResourceManager","==="+gamaObject+"==="+methodName+"==="+message);
            Class unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            Method initMethod = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
            initMethod.invoke(null, gamaObject, methodName, message);
        }catch (Exception e) {
        }
    }

    public void MoneyBoostLoadAndPostImageResource(String image_id,String suffix){
        try {
            MoneyBoostToolsManager.instance().MoneyBoostLogWithDebug("=====MoneyBoostResourceManager","===MoneyBoostLoadAndPostImageResource"+image_id+"==="+suffix);
            MoneyBoostCopyAssetGetFilePath(image_id + "." + suffix);
            File cacheFile = new File(MoneyBoost_mContext.getExternalCacheDir(),image_id + "." + suffix);
            if (cacheFile.exists() && MoneyBoostGetFileSize(cacheFile)>0){
                try {
                    //向unity发消息
                    MoneyBoostSendToUnityMessage("MoneyBoostResourceManager","MoneyBoostOnImageResourceData",cacheFile.getPath());
                }catch (Exception e){
                    MoneyBoostSendToUnityMessage("MoneyBoostResourceManager","MoneyBoostCallBackFailure","MoneyBoostGetImageResourceFailure");
                }
            }else {
                MoneyBoostFirebaseManager.instance().MoneyBoostGetStorageFireWithFilePath(image_id,suffix,new MoneyBoostFirebaseManager.StorageLoadListener(){
                    @Override
                    public void onLoadSuccess(String path) {
                        try {
                            //向unity发消息
                            MoneyBoostSendToUnityMessage("MoneyBoostResourceManager","MoneyBoostOnImageResourceData",path);
                        }catch (Exception e){
                            MoneyBoostSendToUnityMessage("MoneyBoostResourceManager","MoneyBoostCallBackFailure","MoneyBoostGetImageResourceFailure");
                        }
                    }
                    @Override
                    public void onLoadFailure(String error) {
                        MoneyBoostSendToUnityMessage("MoneyBoostResourceManager","MoneyBoostCallBackFailure","MoneyBoostGetImageResourceFailure");
                    }
                });
            }
        }catch (Exception e){
            MoneyBoostSendToUnityMessage("MoneyBoostResourceManager","MoneyBoostCallBackFailure","MoneyBoostGetImageResourceFailure");
        }
    }

    private void MoneyBoostCopyAssetGetFilePath(String fileName) {
        try {
            File cacheDir = MoneyBoost_mContext.getExternalCacheDir();
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
            InputStream is = MoneyBoost_mContext.getAssets().open(fileName);
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
}
