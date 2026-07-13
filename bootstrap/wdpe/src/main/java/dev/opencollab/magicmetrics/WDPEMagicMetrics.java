package dev.opencollab.magicmetrics;

import dev.opencollab.magicmetrics.health.HealthResult;

public class WDPEMagicMetrics extends MagicMetrics {
    private final WDPEPlugin plugin;

    public WDPEMagicMetrics(WDPEPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public HealthResult getCurrentHealthResult() {
        return null;
    }

    @Override
    public void logInfo(String message) {

    }

    @Override
    public void logWarn(String message) {

    }

    @Override
    public void logError(String message) {

    }

    @Override
    public void logError(String message, Throwable throwable) {

    }
}
