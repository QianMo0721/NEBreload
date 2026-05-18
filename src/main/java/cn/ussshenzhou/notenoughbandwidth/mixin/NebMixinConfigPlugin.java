package cn.ussshenzhou.notenoughbandwidth.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

/**
 * Keep server-only mixins from being applied in client runtime.
 *
 * <p>Mixin's generic config loading is willing to consider every mixin listed in
 * the config whenever the target class is transformed. In large client modpacks,
 * other mods may cause dedicated-server-side classes such as
 * {@code ChunkMap} to be loaded in the client process, which then makes NEB's
 * server-only injections compete with unrelated client mixins and crash during
 * login. The original feature is only meaningful on the logical server anyway,
 * so on Forge client runtime we explicitly skip these mixins.</p>
 */
public class NebMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Set<String> SERVER_ONLY_MIXINS = Set.of(
            "cn.ussshenzhou.notenoughbandwidth.mixin.ServerGamePacketListenerImplMixin"
    );

    // DCC mixins && server-only
    private static final Set<String> DCC_MIXINS = Set.of(
            "cn.ussshenzhou.notenoughbandwidth.mixin.ChunkMapMixin",
            "cn.ussshenzhou.notenoughbandwidth.mixin.PlayerListMixin",
            "cn.ussshenzhou.notenoughbandwidth.mixin.ServerPlayerChunkTrackingViewMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
    }

    private static boolean getDccStatus() {
        Path configPath = Paths.get("config", "NotEnoughBandwidthLegacyConfig.json");
        if (!Files.exists(configPath)) {
            return false;
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (root.has("启用延迟区块缓存")) {
                return root.get("启用延迟区块缓存").getAsBoolean();
            }
            if (root.has("enableDelayedChunkCaching")) {
                return root.get("enableDelayedChunkCaching").getAsBoolean();
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (FMLEnvironment.dist == Dist.CLIENT && SERVER_ONLY_MIXINS.contains(mixinClassName)) {
            return false;
        }
        if (DCC_MIXINS.contains(mixinClassName) && (FMLEnvironment.dist == Dist.CLIENT || !getDccStatus())) {
            return false;
        }
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
