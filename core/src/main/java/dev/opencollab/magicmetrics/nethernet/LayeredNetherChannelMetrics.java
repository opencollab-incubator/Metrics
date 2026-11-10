package dev.opencollab.magicmetrics.nethernet;

import org.cloudburstmc.netty.channel.nethernet.config.NetherChannelMetrics;
import org.cloudburstmc.netty.channel.nethernet.config.NetherConnectionFailure;
import tel.schich.libdatachannel.IceState;
import tel.schich.libdatachannel.PeerState;

import java.util.List;

public class LayeredNetherChannelMetrics implements NetherChannelMetrics {
    private final List<NetherChannelMetrics> layers;

    public LayeredNetherChannelMetrics(List<NetherChannelMetrics> layers) {
        this.layers = layers;
    }

    @Override
    public void bytesIn(int count) {
        this.layers.forEach(m -> m.bytesIn(count));
    }

    @Override
    public void bytesOut(int count) {
        this.layers.forEach(m -> m.bytesOut(count));
    }

    @Override
    public void messagesIn(int count) {
        this.layers.forEach(m -> m.messagesIn(count));
    }

    @Override
    public void messagesOut(int count) {
        this.layers.forEach(m -> m.messagesOut(count));
    }

    @Override
    public void fragmentsIn(int count) {
        this.layers.forEach(m -> m.fragmentsIn(count));
    }

    @Override
    public void fragmentsOut(int count) {
        this.layers.forEach(m -> m.fragmentsOut(count));
    }

    @Override
    public void decodeFail(int count) {
        this.layers.forEach(m -> m.decodeFail(count));
    }

    @Override
    public void peerStateChange(PeerState state) {
        this.layers.forEach(m -> m.peerStateChange(state));
    }

    @Override
    public void iceStateChange(IceState state) {
        this.layers.forEach(m -> m.iceStateChange(state));
    }

    @Override
    public void pathSelected(String localType, String remoteType) {
        this.layers.forEach(m -> m.pathSelected(localType, remoteType));
    }

    @Override
    public void connectionFailed(NetherConnectionFailure reason) {
        this.layers.forEach(m -> m.connectionFailed(reason));
    }

    @Override
    public void handshakeRetry() {
        this.layers.forEach(NetherChannelMetrics::handshakeRetry);
    }
}
