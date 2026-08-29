package com.bino.dra.adapter.out.dispatch;

import com.bino.dra.application.port.in.DisputeJobRunner;
import com.bino.dra.application.port.out.DisputeJobDispatcher;
import com.bino.dra.domain.model.Dispute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Component
public class ExecutorDisputeJobDispatcher implements DisputeJobDispatcher {

    private static final Logger log = LoggerFactory.getLogger(ExecutorDisputeJobDispatcher.class);
    private static final int SHUTDOWN_MAX_SECONDS = 30;

    private final DisputeJobRunner runner;
    private final ExecutorService pool;

    public ExecutorDisputeJobDispatcher(DisputeJobRunner runner,
                                        @Value("${dra.submission.worker-pool-size}") int poolSize) {
        this.runner = runner;
        this.pool = Executors.newFixedThreadPool(poolSize, r -> {
            Thread t = new Thread(r, "dispute-worker");
            // Non-daemon: the JVM must wait for these threads, or an in-flight dispute is killed
            t.setDaemon(false);
            return t;
        });
    }

    @Override
    public void dispatch(Dispute dispute) {
        pool.execute(() -> runner.run(dispute));
    }

    @PreDestroy
    void shutdownGracefully() throws InterruptedException {
        pool.shutdown();
        if (!pool.awaitTermination(SHUTDOWN_MAX_SECONDS, TimeUnit.SECONDS)) {
            log.warn("Disputes still in flight after {}s: forcing shutdown", SHUTDOWN_MAX_SECONDS);
            pool.shutdownNow();
        }
    }
}
