package dev.opencollab.magicmetrics.server;

import dev.opencollab.magicmetrics.nethernet.LayeredNetherChannelMetrics;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelMetrics;
import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelOption;

import java.util.List;

/**
 * Sits at the front of a NetherNet server channel's pipeline and sees every accepted connection
 * before the {@code ServerBootstrap} acceptor registers it.
 * <p>
 * {@link NetherChannelOption#NETHER_METRICS} is a child option, and the server is already bound by the
 * time we get to it, so it can't be set through the bootstrap's child options. Setting it here, before
 * the child is registered, means the connection reports from the very start of its handshake.
 * <p>
 * NetherNet has no listener callback for a connection opening or closing, so the active channel count
 * is tracked here too.
 */
@ChannelHandler.Sharable
public final class NetherChildMetricsHandler extends ChannelInboundHandlerAdapter {
    public static final String NAME = "magicmetrics-nethernet";

    private final ServerMetrics metrics;

    public NetherChildMetricsHandler(ServerMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof Channel child) {
            try {
                NetherChannelMetrics metrics = child.config().getOption(NetherChannelOption.NETHER_METRICS);
                if (metrics != null) {
                    metrics = new LayeredNetherChannelMetrics(List.of(metrics, this.metrics));
                } else {
                    metrics = this.metrics;
                }
                child.config().setOption(NetherChannelOption.NETHER_METRICS, metrics);
            } catch (Throwable ignored) {
                // Not a NetherNet child; never stop the connection from being accepted over metrics
            }
            metrics.channelOpened();
            child.closeFuture().addListener(future -> metrics.channelClosed());
        }
        ctx.fireChannelRead(msg);
    }
}
