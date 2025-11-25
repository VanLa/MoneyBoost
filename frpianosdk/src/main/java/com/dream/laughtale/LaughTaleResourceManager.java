package com.dream.laughtale;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;

public class LaughTaleResourceManager {
    public static LaughTaleResourceManager instance;
    private Context LaughTale_mContext;

    public static LaughTaleResourceManager instance(){
        if (null == instance){
            instance = new LaughTaleResourceManager();
        }
        return instance;
    }

    public void LaughTaleInitResourceManager(Context context){
        LaughTale_mContext = context;
        LaughTaleFirebaseManager.instance().LaughTaleInitStorage(context);
    }

    public void LaughTaleSendToUnityMessage(String gamaObject, String methodName, String message){
        try {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleResourceManager","==="+gamaObject+"==="+methodName+"==="+message);
            Class unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
            Method initMethod = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
            initMethod.invoke(null, gamaObject, methodName, message);
        }catch (Exception e) {
        }
    }

    public void LaughTaleLoadAndPostImageResource(String image_id,String suffix){
        try {
            LaughTaleToolsManager.instance().LaughTaleLogWithDebug("=====LaughTaleResourceManager","===LaughTaleLoadAndPostImageResource"+image_id+"==="+suffix);
            LaughTaleCopyAssetGetFilePath(image_id + "." + suffix);
            File cacheFile = new File(LaughTale_mContext.getExternalCacheDir(),image_id + "." + suffix);
            if (cacheFile.exists() && LaughTaleGetFileSize(cacheFile)>0){
                try {
                    //向unity发消息
                    LaughTaleSendToUnityMessage("LaughTaleResourceManager","LaughTaleOnImageResourceData",cacheFile.getPath());
                }catch (Exception e){
                    LaughTaleSendToUnityMessage("LaughTaleResourceManager","LaughTaleCallBackFailure","LaughTaleGetImageResourceFailure");
                }
            }else {
                LaughTaleFirebaseManager.instance().LaughTaleGetStorageFireWithFilePath(image_id,suffix,new LaughTaleFirebaseManager.StorageLoadListener(){
                    @Override
                    public void onLoadSuccess(String path) {
                        try {
                            //向unity发消息
                            LaughTaleSendToUnityMessage("LaughTaleResourceManager","LaughTaleOnImageResourceData",path);
                        }catch (Exception e){
                            LaughTaleSendToUnityMessage("LaughTaleResourceManager","LaughTaleCallBackFailure","LaughTaleGetImageResourceFailure");
                        }
                    }
                    @Override
                    public void onLoadFailure(String error) {
                        LaughTaleSendToUnityMessage("LaughTaleResourceManager","LaughTaleCallBackFailure","LaughTaleGetImageResourceFailure");
                    }
                });
            }
        }catch (Exception e){
            LaughTaleSendToUnityMessage("LaughTaleResourceManager","LaughTaleCallBackFailure","LaughTaleGetImageResourceFailure");
        }
    }

    private void LaughTaleCopyAssetGetFilePath(String fileName) {
        try {
            File cacheDir = LaughTale_mContext.getExternalCacheDir();
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
            InputStream is = LaughTale_mContext.getAssets().open(fileName);
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
}
