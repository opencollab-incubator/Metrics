package dev.opencollab.magicmetrics.config;

import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

import java.io.File;
import java.nio.file.Paths;

public class ConfigLoader {

    // testing only
    public static void main(String[] args) throws ConfigurateException {
        loadConfig(Paths.get(".", "test.yml").toFile());
    }

    public static Config loadConfig(File file) throws ConfigurateException {
        YamlConfigurationLoader loader = YamlConfigurationLoader.builder()
                .commentsEnabled(true)
                .indent(2)
                .nodeStyle(NodeStyle.BLOCK)
                .file(file)
                .defaultOptions(options -> options.header("MagicMetrics config file"))
                .build();

        var node = loader.load();
        Config config = node.get(Config.class);
        loader.save(node);
        return config;
    }
}
