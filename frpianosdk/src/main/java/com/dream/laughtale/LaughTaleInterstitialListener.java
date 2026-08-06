package com.dream.laughtale;

public interface LaughTaleInterstitialListener {
    void LaughTaleOnInterstitialAdClosed();
    void LaughTaleOnInterstitialAdDisplayed();
    void LaughTaleOnInterstitialAdClicked();
    /** show 失败（含未真正展示），Manager 需配对 Unity CLOSE */
    void LaughTaleOnInterstitialAdFailedToShow();
}
