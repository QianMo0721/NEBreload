package cn.ussshenzhou.notenoughbandwidth.mixin;

import net.minecraftforge.fml.relauncher.FMLLaunchHandler;
import net.minecraftforge.fml.relauncher.Side;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 在客户端运行时跳过纯服务端 mixin，避免某些客户端环境下无关类被提前加载时造成注入冲突。
 */
public class NebMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Set<String> SERVER_ONLY_MIXINS = new HashSet<String>(Arrays.asList(
            "cn.ussshenzhou.notenoughbandwidth.mixin.PlayerChunkMapMixin",
            "cn.ussshenzhou.notenoughbandwidth.mixin.PlayerListMixin",
            "cn.ussshenzhou.notenoughbandwidth.mixin.ServerPayloadMixin"
    ));
    private static final Set<String> CLIENT_ONLY_MIXINS = new HashSet<String>(Arrays.asList(
            "cn.ussshenzhou.notenoughbandwidth.mixin.ClientPayloadMixin"
    ));

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Side side = FMLLaunchHandler.side();
        if (side == Side.CLIENT && SERVER_ONLY_MIXINS.contains(mixinClassName)) {
            return false;
        }
        if (side == Side.SERVER && CLIENT_ONLY_MIXINS.contains(mixinClassName)) {
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
