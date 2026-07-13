package dev.opencollab.magicmetrics.health.checks;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultithreadEventLoopGroup;
import io.netty.util.concurrent.EventExecutor;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.TimeUnit;

public class NettyMeasure implements HealthCheck {
    public static final NettyMeasure INSTANCE = new NettyMeasure();

    private final WeakHashMap<EventLoopGroup, GroupData> groups = new WeakHashMap<>();

    @Override
    public HealthCheckResponse call() {
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("netty-threads");

        boolean down = false;

        for (Map.Entry<EventLoopGroup, GroupData> entry : groups.entrySet()) {
            boolean isDown = constructGroup(builder, entry.getKey(), entry.getValue());
            down |= isDown;
        }
        return builder
                .status(!down)
                .build();
    }

    public static void addGroup(EventLoopGroup group, String name) {
        GroupData groupData = new GroupData(name, new ArrayList<>());

        for (EventExecutor loop : group) {
            Heartbeat heartbeat = new Heartbeat();
            loop.scheduleAtFixedRate(heartbeat, 1, 1, TimeUnit.SECONDS);
            groupData.heartbeats.add(new WeakReference<>(heartbeat));
        }

        INSTANCE.groups.put(group, groupData);
    }

    private boolean constructGroup(HealthCheckResponseBuilder builder, EventLoopGroup group, GroupData data) {
        boolean down = group.isShuttingDown() || group.isShutdown() || group.isTerminated();

        int threads;
        if (group instanceof MultithreadEventLoopGroup multithreadGroup) {
            threads = multithreadGroup.executorCount();
        } else {
            threads = 1;
        }

        long currentTime = System.currentTimeMillis();
        for (WeakReference<Heartbeat> heartbeatRef : data.heartbeats) {
            Heartbeat heartbeat = heartbeatRef.get();
            if (heartbeat == null || (currentTime - heartbeat.lastHeartbeat) > 1500) { // 1.5 seconds
                down = true;
                break;
            }
        }

        builder.withData(data.name + "-group-thread-count", threads);
        builder.withData(data.name + "-group-status", down ? "DOWN" : "UP");
        return down;
    }

    private record GroupData(String name, List<WeakReference<Heartbeat>> heartbeats) {
    }

    @RequiredArgsConstructor(access = AccessLevel.PRIVATE)
    private static class Heartbeat implements Runnable {
        private volatile long lastHeartbeat = System.currentTimeMillis();

        @Override
        public void run() {
            this.lastHeartbeat = System.currentTimeMillis();
        }
    }
}
