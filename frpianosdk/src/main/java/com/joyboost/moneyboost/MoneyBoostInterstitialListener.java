package com.joyboost.moneyboost;

public interface MoneyBoostInterstitialListener {
    void MoneyBoostOnInterstitialAdClosed();
    void MoneyBoostOnInterstitialAdDisplayed();
    void MoneyBoostOnInterstitialAdClicked();
    /** show 失败（含未真正展示），Manager 需配对 Unity CLOSE */
    void MoneyBoostOnInterstitialAdFailedToShow();
}
