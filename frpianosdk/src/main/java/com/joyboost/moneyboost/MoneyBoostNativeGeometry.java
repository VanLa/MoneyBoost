package com.joyboost.moneyboost;

/** Pixel geometry shared by layout and regression checks. Never clip an ad into a smaller slot. */
final class MoneyBoostNativeGeometry {
    static boolean fits(int x, int y, int w, int h, int rootW, int rootH, int bottomReserve, int minW, int minH) {
        return rootW > 0 && rootH > 0 && x >= 0 && y >= 0 && w >= minW && h >= minH
                && (long)x + w <= rootW && (long)y + h <= rootH - Math.max(0, bottomReserve);
    }
}
