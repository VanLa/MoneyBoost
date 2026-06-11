package com.dream.laughtale;

import java.lang.reflect.Method;

/**
 * 缓存 UnitySendMessage 反射，避免每次回调都 Class.forName / getMethod。
 */
final class LaughTaleUnityBridge {

    private static volatile Method unitySendMessage;

    private LaughTaleUnityBridge() {
    }

    static void send(String gameObject, String methodName, String data) {
        if (gameObject == null || gameObject.isEmpty() || methodName == null || methodName.isEmpty()) {
            return;
        }
        try {
            Method method = unitySendMessage;
            if (method == null) {
                Class<?> unityPlayerClass = Class.forName("com.unity3d.player.UnityPlayer");
                method = unityPlayerClass.getMethod("UnitySendMessage", String.class, String.class, String.class);
                unitySendMessage = method;
            }
            method.invoke(null, gameObject, methodName, data != null ? data : "");
        } catch (Exception ignored) {
        }
    }
}
