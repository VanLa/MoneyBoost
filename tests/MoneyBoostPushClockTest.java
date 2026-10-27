package com.joyboost.moneyboost;
import java.util.Calendar;
import java.util.TimeZone;

public final class MoneyBoostPushClockTest {
    private static long at(int year, int month, int day, int hour) {
        Calendar c = Calendar.getInstance(); c.clear(); c.set(year, month - 1, day, hour, 0, 0); return c.getTimeInMillis();
    }
    private static void equal(long actual, long expected, String label) {
        if (actual != expected) throw new AssertionError(label + ": " + actual + " != " + expected);
    }
    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        equal(MoneyBoostPushClock.nextAfter(at(2026, 10, 28, 20), 1, at(2026, 10, 27, 21)), at(2026, 10, 28, 20), "Future first reminder");
        equal(MoneyBoostPushClock.nextAfter(at(2026, 10, 20, 20), 1, at(2026, 10, 27, 21)), at(2026, 10, 28, 20), "Long inactive gap");
        equal(MoneyBoostPushClock.nextAfter(at(2026, 10, 27, 20), 1, at(2026, 10, 27, 20)), at(2026, 10, 28, 20), "No immediate duplicate");
        equal(MoneyBoostPushClock.nextAfter(at(2026, 3, 7, 20), 1, at(2026, 3, 8, 19)), at(2026, 3, 8, 20), "Spring DST retains 20:00");
        equal(MoneyBoostPushClock.nextAfter(at(2026, 10, 31, 20), 1, at(2026, 11, 1, 19)), at(2026, 11, 1, 20), "Fall DST retains 20:00");
        equal(MoneyBoostPushClock.nextAfter(at(2026, 12, 31, 20), 3, at(2027, 1, 1, 21)), at(2027, 1, 3, 20), "Repeat interval across year");
        try { MoneyBoostPushClock.nextAfter(at(2026, 1, 1, 20), 0, at(2026, 1, 1, 21)); throw new AssertionError("Accepted zero interval"); }
        catch (IllegalArgumentException expected) { }
        equal(MoneyBoostPushClock.nextFallback(at(2026, 10, 28, 9), 1, 20, at(2026, 10, 28, 9)), at(2026, 10, 29, 20), "No Day1 open means Day2 fallback 20:00");
        equal(MoneyBoostPushClock.nextFallback(at(2026, 10, 28, 21), 1, 20, at(2026, 10, 28, 21)), at(2026, 10, 29, 20), "Fallback is next day, not another push today");
        System.out.println("PUSH CLOCK PASS: 9 cases including DST, fallback and long inactivity");
    }
}
