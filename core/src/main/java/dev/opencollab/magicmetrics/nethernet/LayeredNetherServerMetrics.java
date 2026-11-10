package dev.opencollab.magicmetrics.nethernet;

import org.cloudburstmc.netty.channel.nethernet.config.NetherServerMetrics;

import java.net.InetSocketAddress;
import java.util.List;

public class LayeredNetherServerMetrics implements NetherServerMetrics {
    private final List<NetherServerMetrics> layers;

    public LayeredNetherServerMetrics(List<NetherServerMetrics> layers) {
        this.layers = layers;
    }

    @Override
    public void connectionAccepted(String networkId, boolean verifiedIdentity) {
        this.layers.forEach(m -> m.connectionAccepted(networkId, verifiedIdentity));
    }

    @Override
    public void joinRefused(int httpStatus) {
        this.layers.forEach(m -> m.joinRefused(httpStatus));
    }

    @Override
    public void addressRefused(InetSocketAddress address) {
        this.layers.forEach(m -> m.addressRefused(address));
    }

    @Override
    public void plaintextRefused(InetSocketAddress address) {
        this.layers.forEach(m -> m.plaintextRefused(address));
    }

    @Override
    public void joinThrottled(InetSocketAddress address) {
        this.layers.forEach(m -> m.joinThrottled(address));
    }
}
