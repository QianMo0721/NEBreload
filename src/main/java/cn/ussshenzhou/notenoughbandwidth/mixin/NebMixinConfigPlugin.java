package cn.ussshenzhou.notenoughbandwidth.mixin;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

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
            "cn.ussshenzhou.notenoughbandwidth.mixin.ChunkMapMixin",
            "cn.ussshenzhou.notenoughbandwidth.mixin.DistanceManagerAccessor",
            "cn.ussshenzhou.notenoughbandwidth.mixin.PlayerListMixin",
            "cn.ussshenzhou.notenoughbandwidth.mixin.ServerGamePacketListenerImplMixin"
    );

    @Override
    public void onLoad(String mixinPackage) {
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
