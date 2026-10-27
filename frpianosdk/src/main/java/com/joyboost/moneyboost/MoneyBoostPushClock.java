package com.joyboost.moneyboost;

import java.util.Calendar;

final class MoneyBoostPushClock {
    static long nextFallback(long delivered, int days, int hour, long now) {
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(delivered);
        next.add(Calendar.DAY_OF_YEAR, days);
        next.set(Calendar.HOUR_OF_DAY, hour);
        next.set(Calendar.MINUTE, 0);
        next.set(Calendar.SECOND, 0);
        next.set(Calendar.MILLISECOND, 0);
        return nextAfter(next.getTimeInMillis(), days, now);
    }
    static long nextAfter(long first, int days, long now) {
        if (first <= 0 || days < 1 || days > 365) throw new IllegalArgumentException("Invalid schedule");
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(first);
        // Jump over long gaps, then use calendar days to retain local time across DST.
        if (now > first) {
            long elapsedDays = (now - first) / 86400000L;
            long periods = Math.max(0, elapsedDays / days - 1);
            if (periods > 0) next.add(Calendar.DAY_OF_YEAR, Math.toIntExact(periods * days));
        }
        while (next.getTimeInMillis() <= now) next.add(Calendar.DAY_OF_YEAR, days);
        return next.getTimeInMillis();
    }
}
