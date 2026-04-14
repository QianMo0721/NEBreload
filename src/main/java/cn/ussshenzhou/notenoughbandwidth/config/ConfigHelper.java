package cn.ussshenzhou.notenoughbandwidth.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * @author USS_Shenzhou
 */
public class ConfigHelper {
    private static final File CONFIG_DIR = Paths.get("config").toFile();
    private static final ConcurrentHashMap<Class<? extends TConfig>, TConfig> CACHE = new ConcurrentHashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final File UNIVERSAL_CONFIG_DIR = Path.of(System.getProperty("user.home"), "MinecraftT88Config").toFile();

    private static void checkDir(File dir) {
        if (!dir.isDirectory()) {
            dir.mkdirs();
        }
    }

    private static File checkFile(TConfig config, boolean universal) {
        checkDir(universal ? UNIVERSAL_CONFIG_DIR : CONFIG_DIR);
        String configFileName = config.getClass().getSimpleName();
        Path childDir = (universal ? UNIVERSAL_CONFIG_DIR : CONFIG_DIR).toPath().resolve(config.getChildDirName());
        checkDir(childDir.toFile());
        return childDir.resolve(configFileName + ".json").toFile();
    }

    public static void loadConfig(TConfig newInstance) {
        File configFile = checkFile(newInstance, false);
        loadConfigInternal(newInstance, configFile);
    }

    @SuppressWarnings("unchecked")
    public static <T extends TConfig> T getConfigRead(Class<T> configClass) {
        return (T) CACHE.get(configClass);
    }

    @SuppressWarnings("unchecked")
    public static <T extends TConfig> void getConfigWrite(Class<T> configClass, Consumer<T> setter) {
        T config = (T) CACHE.get(configClass);
        setter.accept(config);
        saveConfig(config);
    }

    public static <T extends TConfig> void saveConfig(T config) {
        File configFile = checkFile(config, false);
        saveConfigInternal(config, configFile);
    }

    protected static void loadConfigInternal(TConfig newInstance, File configFile) {
        Class<? extends TConfig> configClass = newInstance.getClass();
        if (configFile.exists()) {
            try {
                String json = Files.readString(configFile.toPath(), StandardCharsets.UTF_8);
                TConfig loaded = GSON.fromJson(json, configClass);
                if (loaded == null) {
                    loaded = newInstance;
                }
                CACHE.put(configClass, loaded);
                saveConfigInternal(loaded, configFile);
            } catch (Exception e) {
                LogUtils.getLogger().error("Failed to read config file: " + configFile, e);
                CACHE.put(configClass, newInstance);
            }
        } else {
            CACHE.put(configClass, newInstance);
            saveConfigInternal(newInstance, configFile);
        }
    }

    protected static void saveConfigInternal(TConfig config, File configFile) {
        CompletableFuture.runAsync(() -> {
            try {
                Path parent = configFile.toPath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                String json = GSON.toJson(config);
                Files.writeString(configFile.toPath(), json, StandardCharsets.UTF_8);
            } catch (IOException e) {
                LogUtils.getLogger().error("Failed to write config file: " + configFile, e);
            }
        });
    }

    public static class Universal extends ConfigHelper {

        public static void loadConfig(TConfig newInstance) {
            File configFile = checkFile(newInstance, true);
            loadConfigInternal(newInstance, configFile);
        }

        @SuppressWarnings("unchecked")
        public static <T extends TConfig> void getConfigWrite(Class<T> configClass, Consumer<T> setter) {
            T config = (T) CACHE.get(configClass);
            setter.accept(config);
            Universal.saveConfig(config);
        }

        public static <T extends TConfig> void saveConfig(T config) {
            File configFile = checkFile(config, true);
            saveConfigInternal(config, configFile);
        }
    }
}
