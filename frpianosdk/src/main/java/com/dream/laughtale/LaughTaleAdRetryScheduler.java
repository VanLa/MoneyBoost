package com.dream.laughtale;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 各广告位加载失败重试共用单线程调度，避免每个 Adapter 各自 new 线程池。
 * 返回 {@link ScheduledFuture} 以便 Destroy / 新一次 load 时取消旧任务。
 */
final class LaughTaleAdRetryScheduler {

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "LaughTale-AdRetry");
        t.setDaemon(true);
        return t;
    });

    private LaughTaleAdRetryScheduler() {
    }

    static ScheduledFuture<?> scheduleSeconds(Runnable task, long delaySeconds) {
        return SCHEDULER.schedule(task, delaySeconds, TimeUnit.SECONDS);
    }

    static void cancel(ScheduledFuture<?> future) {
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }
    }
}
