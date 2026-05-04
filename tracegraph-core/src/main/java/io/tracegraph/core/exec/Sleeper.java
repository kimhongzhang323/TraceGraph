package io.tracegraph.core.exec;

import java.time.Duration;

interface Sleeper {

    void sleep(Duration duration) throws InterruptedException;

    static Sleeper realtime() {
        return duration -> {
            if (duration == null || duration.isZero() || duration.isNegative()) return;
            Thread.sleep(duration.toMillis(), duration.toNanosPart() % 1_000_000);
        };
    }
}
