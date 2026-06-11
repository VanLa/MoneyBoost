package com.dream.laughtale;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 各广告位加载失败重试共用单线程调度，避免每个 Adapter 各自 new 线程池。
 */
final class LaughTaleAdRetryScheduler {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LaughTale-AdRetry");
        t.setDaemon(true);
        return t;
    });

    private LaughTaleAdRetryScheduler() {
    }

    static void scheduleSeconds(Runnable task, long delaySeconds) {
        SCHEDULER.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }
}
