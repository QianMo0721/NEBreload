package cn.ussshenzhou.notenoughbandwidth.config;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;

public final class ConfigHelper {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static File configFile;
    private static NotEnoughBandwidthLegacyConfig config;

    private ConfigHelper() {
    }

    public static void load(File suggestedFile) {
        configFile = suggestedFile;
        if (configFile == null) {
            config = new NotEnoughBandwidthLegacyConfig();
            return;
        }

        File parent = configFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (!configFile.exists()) {
            config = new NotEnoughBandwidthLegacyConfig();
            save();
            return;
        }

        try (Reader reader = new FileReader(configFile)) {
            config = GSON.fromJson(reader, NotEnoughBandwidthLegacyConfig.class);
        } catch (Exception ignored) {
            config = new NotEnoughBandwidthLegacyConfig();
        }

        if (config == null) {
            config = new NotEnoughBandwidthLegacyConfig();
        }
        save();
    }

    public static NotEnoughBandwidthLegacyConfig getConfig() {
        if (config == null) {
            config = new NotEnoughBandwidthLegacyConfig();
        }
        return config;
    }

    public static void save() {
        if (configFile == null || config == null) {
            return;
        }
        try (Writer writer = new FileWriter(configFile)) {
            GSON.toJson(config, writer);
        } catch (IOException ignored) {
        }
    }
}
