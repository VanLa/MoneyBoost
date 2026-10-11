package com.joyboost.moneyboost;

import java.util.HashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** One business opportunity, independent of inventory; no events from readiness polling/reloads. */
final class MoneyBoostAdAnalytics {
    interface Sink { void log(String name, Map<String, Object> parameters); }
    static final class Opportunity {
        final Map<String, Object> parameters = new HashMap<>();
        final Set<Integer> impressions = new HashSet<>();
        Opportunity(String format, String scene, boolean available) {
            parameters.put("ad_type", format);
            parameters.put("scene", scene == null ? "" : scene);
            parameters.put("opportunity_id", UUID.randomUUID().toString());
            parameters.put("ad_available", available ? "1" : "0");
            parameters.put("ad_event_schema", "2");
        }
    }
    /** A logged readiness check and its immediate show share one opportunity. */
    final class Placement {
        private final String format;
        private Opportunity prepared;
        Placement(String format) { this.format = format; }
        synchronized boolean prepare(String scene, boolean available) {
            Opportunity opportunity = opportunity(format, scene, available);
            prepared = available ? opportunity : null;
            return available;
        }
        synchronized Opportunity request(String scene, boolean available) {
            Opportunity opportunity = prepared;
            prepared = null;
            if (opportunity != null && opportunity.parameters.get("scene").equals(scene == null ? "" : scene)) {
                return opportunity;
            }
            return opportunity(format, scene, available);
        }
        synchronized void clear() { prepared = null; }
    }
    private final Sink sink;
    MoneyBoostAdAnalytics(Sink sink) { this.sink = sink; }
    Opportunity opportunity(String format, String scene, boolean available) {
        Opportunity opportunity = new Opportunity(format, scene, available);
        sink.log(format + "_should_show", new HashMap<>(opportunity.parameters));
        return opportunity;
    }
    void show(Opportunity opportunity) { impression(opportunity, 0); }
    synchronized void impression(Opportunity opportunity, int refreshIndex) {
        if (opportunity == null || !opportunity.impressions.add(refreshIndex)) return;
        Map<String, Object> parameters = new HashMap<>(opportunity.parameters);
        parameters.put("refresh_index", refreshIndex);
        parameters.put("impression_id", UUID.randomUUID().toString());
        sink.log(parameters.get("ad_type") + "_show", parameters);
    }
}
