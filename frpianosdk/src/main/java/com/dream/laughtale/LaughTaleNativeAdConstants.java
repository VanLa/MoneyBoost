package com.dream.laughtale;

/**
 * 对齐竞品反编译默认参数（FsnAd.countDownSec、native_collapse 高度、RemoteConfig 未知项等）。
 */
public final class LaughTaleNativeAdConstants {

    /** FsnAd 全屏 Native 关闭倒计时（秒） */
    public static final int COUNTDOWN_FULLSCREEN_SEC = 3;
    /** 半屏关闭倒计时（秒）；对齐历史 b2x delayTime=3 */
    public static final int COUNTDOWN_HALF_SEC = 3;
    /** 半屏卡片高度参考（b2x DEFAULT 合计约 444dp） */
    public static final int HALF_HEIGHT_DP = 444;
    /** CountShowNativeFullInter：连续全屏 Native 优先次数上限 */
    public static final int NATIVE_FULL_COUNT_BEFORE_INTER_MAX = 3;

    /**
     * 竞品 open_ad_startup_on_off：冷启动是否出开屏。
     * RC 默认值未知，写死 true。
     */
    public static final boolean OPEN_AD_STARTUP_ON = true;
    /**
     * 竞品 open_ad_resume_on_off：热启动/回前台是否出开屏。
     * RC 默认值未知，写死 true。
     */
    public static final boolean OPEN_AD_RESUME_ON = true;
    /** 关闭后是否立即补货 */
    public static final boolean LOAD_NEW_AFTER_CLOSE = true;
    /** FSN 默认非静音；对齐竞品 VideoOptions.setStartMuted */
    public static final boolean START_MUTE_VIDEO = false;

    private LaughTaleNativeAdConstants() {
    }
}
