package com.joyboost.moneyboost;

import java.util.*;
import java.util.concurrent.*;

public final class MoneyBoostAdAnalyticsTest {
    static final class Event {
        final String name;
        final Map<String, Object> params;
        Event(String name, Map<String, Object> params) { this.name = name; this.params = params; }
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        List<Event> events = Collections.synchronizedList(new ArrayList<>());
        MoneyBoostAdAnalytics analytics = new MoneyBoostAdAnalytics((name, params) -> events.add(new Event(name, params)));
        for (String type : Arrays.asList("inter", "reward", "banner", "collapsibleBanner", "splash")) {
            events.clear();
            MoneyBoostAdAnalytics.Opportunity noFill = analytics.opportunity(type, "empty", false);
            check(events.size() == 1 && events.get(0).name.equals(type + "_should_show"), "no-fill opportunity missing: " + type);
            check(events.get(0).params.get("ad_available").equals("0"), "no-fill availability wrong");
            // Failure/timeout/load retry never calls show. Duplicate displayed callbacks must be harmless.
            MoneyBoostAdAnalytics.Opportunity success = analytics.opportunity(type, "settlement", true);
            analytics.show(success);
            analytics.show(success);
            check(events.size() == 3, "duplicate callback counted: " + type);
            check(events.get(2).name.equals(type + "_show"), "show missing: " + type);
            check(events.get(1).params.get("opportunity_id").equals(events.get(2).params.get("opportunity_id")), "pair mismatch");
            check(!noFill.parameters.get("opportunity_id").equals(success.parameters.get("opportunity_id")), "ID reuse");
        }
        for (String type : Arrays.asList("inter", "reward")) {
            events.clear();
            MoneyBoostAdAnalytics.Placement placement = analytics.new Placement(type);
            check(placement.prepare("button", true), "ready result changed");
            MoneyBoostAdAnalytics.Opportunity first = placement.request("button", true);
            analytics.show(first);
            check(events.size() == 2, "ready-check + show double counts opportunity");
            // A subsequent real click at the same placement must still count.
            analytics.show(placement.request("button", true));
            check(events.size() == 4, "second click missing");
            placement.prepare("empty", false);
            check(events.size() == 5, "inventory gate suppresses opportunity");
            placement.prepare("abandoned", true);
            placement.clear();
            analytics.show(placement.request("new_session", true));
            check(events.size() == 8, "destroy retained old request");
        }
        events.clear();
        MoneyBoostAdAnalytics.Placement lostInventory = analytics.new Placement("inter");
        lostInventory.prepare("race", true);
        lostInventory.request("race", false); // Inventory disappeared before show: no phantom show, no second opportunity.
        check(events.size() == 1, "inventory race double counted opportunity");
        events.clear();
        MoneyBoostAdAnalytics.Opportunity banner = analytics.opportunity("banner", "persistent", true);
        analytics.impression(banner, 1);
        analytics.impression(banner, 1); // Duplicate callback / hide then restore same creative.
        analytics.impression(banner, 2); // Genuine auto-refresh on the same BannerAd object.
        analytics.impression(banner, 2);
        check(events.size() == 3, "banner refresh missing or visibility sync double counted");
        check(!events.get(1).params.get("impression_id").equals(events.get(2).params.get("impression_id")), "refresh ID reuse");
        events.clear();
        MoneyBoostAdAnalytics.Opportunity concurrent = analytics.opportunity("inter", "concurrent", true);
        ExecutorService workers = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 100; i++) workers.submit(() -> analytics.show(concurrent));
        workers.shutdown();
        check(workers.awaitTermination(10, TimeUnit.SECONDS), "workers timed out");
        check(events.size() == 2, "concurrent callback double counted");
        analytics.show(null);
        check(events.size() == 2, "stale callback created phantom impression");
        System.out.println("PASS: five formats, no-fill, duplicate/concurrent callbacks, pairing, readiness handoff, repeated requests, lifecycle clear, banner refresh.");
    }
}
