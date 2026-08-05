package com.dream.laughtale;

public interface LaughTaleSplashListener {
    void LaughTaleOnSplashAdClosed();

    void LaughTaleOnSplashAdDisplayed();

    void LaughTaleOnSplashAdDisplayFailed();

    /** App Open 加载完成且 SDK 标记了冷/热启动待展示时回调 */
    void LaughTaleOnSplashAdReadyForAutoShow(String scene);
}
