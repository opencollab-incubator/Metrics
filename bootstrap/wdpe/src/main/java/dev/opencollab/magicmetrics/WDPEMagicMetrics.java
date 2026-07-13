package dev.opencollab.magicmetrics;

public class WDPEMagicMetrics extends MagicMetrics {
    private final WDPEPlugin plugin;

    public WDPEMagicMetrics(WDPEPlugin plugin) {
        super();
        this.plugin = plugin;
    }

    @Override
    public void logInfo(String message) {
        plugin.getLogger().info(message);
    }

    @Override
    public void logWarn(String message) {
        plugin.getLogger().warn(message);
    }

    @Override
    public void logError(String message) {
        plugin.getLogger().error(message);
    }

    @Override
    public void logError(String message, Throwable throwable) {
        plugin.getLogger().error(message, throwable);
    }
}
