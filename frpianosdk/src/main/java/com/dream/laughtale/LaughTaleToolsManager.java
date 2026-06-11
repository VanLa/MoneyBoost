package com.dream.laughtale;

import android.app.Activity;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.View;

import com.google.android.gms.tasks.Task;
import com.google.android.play.core.review.ReviewInfo;
import com.google.android.play.core.review.ReviewManager;
import com.google.android.play.core.review.ReviewManagerFactory;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

public class LaughTaleToolsManager {
    public static LaughTaleToolsManager instance;

    public boolean LaughTale_isDebug = false;
    public boolean LaughTale_isTestMetaNative = false;
    public double LaughTale_isTestInterNativeBidder = 1.0;
    public static LaughTaleToolsManager instance(){
        if (null == instance){
            instance = new LaughTaleToolsManager();
        }
        return instance;
    }

    public void LaughTaleVibrator(Activity activity, long time){
        Vibrator vib = (Vibrator) activity.getSystemService(Service.VIBRATOR_SERVICE);
        vib.vibrate(time);
    }

    public String LaughTaleGetVersionName(Activity activity) throws Exception
    {
        PackageManager packageManager = activity.getPackageManager();
        PackageInfo packInfo = packageManager.getPackageInfo(activity.getPackageName(),0);
        String versionName = packInfo.versionName;
        return versionName;
    }

    public void LaughTaleTurnToStoreDetail(Activity activity){
        try {
            ReviewManager manager = ReviewManagerFactory.create(activity);
            Task<ReviewInfo> request = manager.requestReviewFlow();
            request.addOnCompleteListener(task -> {
                if (task.isSuccessful()) {
                    // We can get the ReviewInfo object
                    ReviewInfo reviewInfo = task.getResult();
                    Task<Void> flow = manager.launchReviewFlow(activity, reviewInfo);
                    flow.addOnCompleteListener(rtask -> {
                        // The flow has finished. The API does not indicate whether the user
                        // reviewed or not, or even whether the review dialog was shown. Thus, no
                        // matter the result, we continue our app flow.
                    });
                } else {
                    // There was some problem, log or handle the error code.
//                    @ReviewErrorCode int reviewErrorCode = ((ReviewException) task.getException()).getErrorCode();
                }
            });
        }catch (Exception e){
        }
    }

    public String LaughTaleGetCPRemoteConfig(Activity activity) throws Exception
    {
        LaughTaleLogWithDebug("=======","LaughTaleGetCPRemoteConfig");
        if (LaughTaleFirebaseManager.instance().LaughTale_cp_config != null){
            LaughTaleLogWithDebug("=======","CPRemoteConfigCallback:"+ LaughTaleFirebaseManager.instance().LaughTale_cp_config);
            return LaughTaleFirebaseManager.instance().LaughTale_cp_config;
        }else {
            LaughTaleLogWithDebug("=======","CPRemoteConfigCallback:");
            return "";
        }
    }

    public void LaughTaleTurnToPromotion(Activity activity,String appPackageName) {
        try {
            // 打点（可选）
            try {
                JSONObject object = new JSONObject();
                object.put("app_bundle",appPackageName);
                LaughTaleFirebaseManager.instance().LaughTaleLogFirebaseEvent("click_promotion", object.toString());
            } catch (Exception e) {
                // 可忽略
            }
            // 检查是否安装了目标 App
            PackageManager pm = activity.getPackageManager();
            Intent launchIntent = pm.getLaunchIntentForPackage(appPackageName);
            if (launchIntent != null) {
                // 目标 App 已安装，直接启动它
                launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(launchIntent);
            } else {
                // 未安装，跳转到 Play 商店
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + appPackageName));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                } catch (android.content.ActivityNotFoundException anfe) {
                    // Play 商店不可用，使用浏览器打开
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=" + appPackageName));
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    activity.startActivity(intent);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * RAM内存大小, 返回1GB/2GB/3GB/4GB/8G/16G
     * @return
     */
    private static volatile String LaughTale_cachedTotalRam;

    public String LaughTaleGetTotalRam(){
        if (LaughTale_cachedTotalRam != null) {
            return LaughTale_cachedTotalRam;
        }
        String path = "/proc/meminfo";
        String ramMemorySize = null;
        int totalRam = 0;
        try {
            FileReader fileReader = new FileReader(path);
            BufferedReader br = new BufferedReader(fileReader, 4096);
            ramMemorySize = br.readLine().split("\\s+")[1];
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (ramMemorySize != null) {
            totalRam = (int) Math.ceil((Float.parseFloat(ramMemorySize) / (1024 * 1024)));
        }
        LaughTale_cachedTotalRam = totalRam + "GB";
        return LaughTale_cachedTotalRam;
    }

    public String LaughTaleGetIsNewUser(Context context){
        long currentTime=System.currentTimeMillis();
        long firstInstallTime = 0;
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            firstInstallTime = info.firstInstallTime;
            if (currentTime - firstInstallTime >= 86400000) {
                return "0";
            }else {
                return "1";
            }
        } catch (Exception e) {
            return "0";
        }
    }

    /**
     * 判断当前设备是否为3GB以下的低端机
     * @return true 表示是低端机
     */
    public boolean LaughTaleIsLowEndDevice() {
        String ram = LaughTaleGetTotalRam();
        try {
            // 解析数字部分，支持 "2GB", "3GB", "4GB"
            int gb = Integer.parseInt(ram.replace("GB", "").trim());
            return gb <= 3;
        } catch (Exception e) {
            e.printStackTrace();
            return false; // 如果解析失败，默认认为不是低端机
        }
    }


    public void LaughTaleLogWithDebug(String tag, String message){
        if (LaughTale_isDebug){
            Log.e(tag,message);
        }
    }

    public float LaughTaleGetStatusBarHeight(Activity activity){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            View decorView = activity.getWindow().getDecorView();
            DisplayCutout displayCutout = decorView.getRootWindowInsets().getDisplayCutout();
            if (displayCutout != null) {
                List<Rect> rects = displayCutout.getBoundingRects();
                if (rects.size() > 0) {
                    // 获取第一个rect的高度即为刘海屏的高度
                    return  (float) rects.get(0).height();
                }
            }
        }
        return 0.0f;
    }

    public void LaughTaleShareToOtherApp(Activity activity){
        // 获取当前应用的包名
        String packageName = activity.getPackageName();
        // 构建 Google Play 链接
        String appUrl = "https://play.google.com/store/apps/details?id=" + packageName;
        // 创建分享 Intent
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        // 设置要分享的内容（Google Play 链接）
        shareIntent.putExtra(Intent.EXTRA_TEXT, appUrl);
        // 启动分享选择器
        activity.startActivity(Intent.createChooser(shareIntent, "Choose sharing option"));
    }
}
