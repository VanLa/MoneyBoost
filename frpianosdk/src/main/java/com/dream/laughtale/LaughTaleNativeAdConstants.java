package com.dream.laughtale;

/**
 * 对齐竞品反编译默认参数（FsnAd.countDownSec、native_collapse 高度、RemoteConfig 未知项等）。
 */
public final class LaughTaleNativeAdConstants {

    /** FsnAd 全屏 Native 关闭倒计时（秒） */
    public static final int COUNTDOWN_FULLSCREEN_SEC = 3;
    /**
     * 竞品 RC native_collap_countdown_time：半屏关闭倒计时（秒）。
     * Unity EnableButtonCloseCollapsible 延后可点；默认按 3s 对齐用户实机。
     */
    public static final int COUNTDOWN_HALF_SEC = 3;
    /** 半屏高度（含 Ad 排 + 关闭排 + icon/media/CTA） */
    public static final int HALF_HEIGHT_DP = 430;
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
