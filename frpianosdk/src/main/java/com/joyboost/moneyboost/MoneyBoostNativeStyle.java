package com.joyboost.moneyboost;

/** Project-owned visual parameters. Ad assets, AdChoices and SDK click handling stay untouched. */
final class MoneyBoostNativeStyle {
    final int background, border, text, button, buttonText;
    final float cornerRadius, buttonRadius, headlineSize, buttonSize;
    final boolean outlineButton;

    MoneyBoostNativeStyle() {
        this(0xFFFFF9FE, 0xFFCDBFEB, 0xFF2A1D50, 0xFFA85CDF, 0xFFFFFFFF,
                14, 14, false, 15, 13);
    }

    MoneyBoostNativeStyle(int background, int border, int text, int button, int buttonText,
                         float cornerRadius, float buttonRadius, boolean outlineButton,
                         float headlineSize, float buttonSize) {
        this.background = background | 0xFF000000;
        this.border = border | 0xFF000000;
        this.text = text | 0xFF000000;
        this.button = button | 0xFF000000;
        this.buttonText = buttonText | 0xFF000000;
        this.cornerRadius = bounded(cornerRadius, 0, 48, 14);
        this.buttonRadius = bounded(buttonRadius, 0, 48, 14);
        this.headlineSize = bounded(headlineSize, 12, 24, 15);
        this.buttonSize = bounded(buttonSize, 12, 20, 13);
        this.outlineButton = outlineButton;
    }

    private static float bounded(float value, float min, float max, float fallback) {
        return Float.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
}
