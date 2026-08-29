package com.bino.dra.adapter.out.dispatch;

import com.bino.dra.application.port.in.DisputeJobRunner;
import com.bino.dra.domain.model.Dispute;
import com.bino.dra.domain.model.Money;
import com.bino.dra.domain.model.Network;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ExecutorDisputeJobDispatcherTest {

    @Test
    void an_in_flight_dispute_finishes_during_shutdown() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        DisputeJobRunner slow = dispute -> {
            started.countDown();
            sleep(300);
            finished.set(true);
        };
        ExecutorDisputeJobDispatcher dispatcher = new ExecutorDisputeJobDispatcher(slow, 2);

        dispatcher.dispatch(dispute("D-1"));
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();

        dispatcher.shutdownGracefully();

        assertThat(finished).isTrue();
    }

    @Test
    void after_shutdown_a_new_dispute_is_refused_rather_than_lost() throws Exception {
        ExecutorDisputeJobDispatcher dispatcher =
                new ExecutorDisputeJobDispatcher(dispute -> { }, 1);
        dispatcher.shutdownGracefully();

        assertThatThrownBy(() -> dispatcher.dispatch(dispute("D-2")))
                .isInstanceOf(RejectedExecutionException.class);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static Dispute dispute(String id) {
        return new Dispute(id, "TXN-" + id, "M-1", Network.VISA, "10.4",
                new Money(4_500L, "EUR"), Instant.now(), Instant.now().plusSeconds(2_592_000L), null);
    }
}
