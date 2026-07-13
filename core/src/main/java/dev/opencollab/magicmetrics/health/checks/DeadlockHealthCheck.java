package dev.opencollab.magicmetrics.health.checks;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;

public class DeadlockHealthCheck implements HealthCheck {
    @Override
    public HealthCheckResponse call() {
        ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        long[] threadIds = bean.findDeadlockedThreads();

        HealthCheckResponseBuilder builder = HealthCheckResponse.named("deadlock");

        if (threadIds != null && threadIds.length > 0) {
            builder.down();
            ThreadInfo[] infos = bean.getThreadInfo(threadIds);

            for (ThreadInfo info : infos) {
                builder.withData(info.getThreadName(), "Locked on " + info.getLockName() + " at " + info.getLockOwnerName());
                builder.withData(info.getThreadName() + "-stacktrace", stringifyTrace(info.getStackTrace()));
            }
        } else {
            builder.up();
        }
        return builder.build();
    }

    private String stringifyTrace(StackTraceElement[] elements) {
        StringBuilder builder = new StringBuilder();
        for (StackTraceElement element : elements) {
            builder.append(element.toString()).append("\n");
        }
        return builder.toString();
    }
}
