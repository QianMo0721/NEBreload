package cn.ussshenzhou.notenoughbandwidth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import cn.ussshenzhou.notenoughbandwidth.config.TMultiInstanceConfig;

/**
 * @author USS_Shenzhou
 */
public class MultiInstanceConfigHelper {
    private static final File CONFIG_DIR = Paths.get("config").toFile();
    private static final ConcurrentHashMap<Class<? extends TMultiInstanceConfig>, ConcurrentHashMap<String, TMultiInstanceConfig>> CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static void checkDir(File dir) {
        if (!dir.isDirectory()) {
            dir.mkdir();
        }
    }

    private static File checkChildDir(TMultiInstanceConfig config) {
        return checkChildDir(config.getClass(), config.getChildDirName());
    }

    private static File checkChildDir(Class<? extends TMultiInstanceConfig> clazz, String childDirName) {
        checkDir(CONFIG_DIR);
        checkDir(CONFIG_DIR.toPath().resolve(childDirName).toFile());
        File f = CONFIG_DIR.toPath().resolve(childDirName).resolve(clazz.getSimpleName()).toFile();
        checkDir(f);
        return f;
    }

    public static void loadConfigInstances(TMultiInstanceConfig newInstance) {
        loadConfigInstances(newInstance.getClass(), newInstance.getChildDirName());
    }

    public static void loadConfigInstances(Class<? extends TMultiInstanceConfig> clazz, String childDirName) {
        File childDir = checkChildDir(clazz, childDirName);
        File[] instances = childDir.listFiles();
        if (instances == null) {
            return;
        }
        for (File f : instances) {
            try {
                String json = FileUtils.readFileToString(f, StandardCharsets.UTF_8);
                TMultiInstanceConfig loaded = (TMultiInstanceConfig) GSON.fromJson(json, clazz);
                CACHE.computeIfAbsent(clazz, k -> new ConcurrentHashMap<>()).put(loaded.getFileName(), loaded);
            } catch (IOException e) {
                LogUtils.getLogger().error("Failed to read config file: " + f, e);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static <T extends TMultiInstanceConfig> T getConfigRead(Class<T> configClass, String fileName) {
        ConcurrentHashMap<String, TMultiInstanceConfig> map = CACHE.get(configClass);
        if (map == null) {
            return null;
        }
        return (T) map.get(fileName);
    }

    @SuppressWarnings("unchecked")
    public static <T extends TMultiInstanceConfig> void getConfigWrite(Class<T> configClass, String fileName, Consumer<T> setter) {
        ConcurrentHashMap<String, TMultiInstanceConfig> map = CACHE.get(configClass);
        if (map == null) {
            return;
        }
        T config = (T) map.get(fileName);
        if (config == null) {
            return;
        }
        setter.accept(config);
        saveConfig(config);
    }

    public static <T extends TMultiInstanceConfig> void saveConfig(T config) {
        File childDir = checkChildDir(config);
        File configFile = childDir.toPath().resolve(config.getFileName() + ".json").toFile();
        saveConfigInternal(config, configFile);
    }

    private static void saveConfigInternal(TMultiInstanceConfig config, File configFile) {
        CompletableFuture.runAsync(() -> {
            try {
                String json = GSON.toJson(config);
                FileUtils.writeStringToFile(configFile, json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LogUtils.getLogger().error("Failed to write config file: " + configFile, e);
            }
        });
    }
}
