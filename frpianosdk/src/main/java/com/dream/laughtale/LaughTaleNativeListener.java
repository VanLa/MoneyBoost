package com.dream.laughtale;

public interface LaughTaleNativeListener {
    /** @param clickedCloseButton 用户是否点了关闭按钮 */
    /** @param silentClose 切后台等场景仅隐藏 UI */
    void LaughTaleOnNativeAdClosed(boolean clickedCloseButton, boolean silentClose);

    void LaughTaleOnNativeAdDisplayed();

    void LaughTaleOnNativeAdClicked();

    void LaughTaleOnNativeAdLoaded();

    /** 有展示请求但当前无可用缓存，未能挂载 UI */
    void LaughTaleOnNativeAdShowSkipped();
}
