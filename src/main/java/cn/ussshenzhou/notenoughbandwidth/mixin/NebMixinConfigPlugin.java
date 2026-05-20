package cn.ussshenzhou.notenoughbandwidth.mixin;

import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.objectweb.asm.tree.ClassNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NebMixinConfigPlugin implements IMixinConfigPlugin {
    private static final Set<String> CLIENT_ONLY_MIXINS = new HashSet<String>(Arrays.asList(
            "cn.ussshenzhou.notenoughbandwidth.mixin.ClientPacketListenerMixin"
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
        if (CLIENT_ONLY_MIXINS.contains(mixinClassName)) {
            return isClientEnvironment();
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

    private static boolean isClientEnvironment() {
        try {
            Class<?> handlerClass = Class.forName("net.minecraftforge.fml.relauncher.FMLLaunchHandler");
            Object side = handlerClass.getMethod("side").invoke(null);
            return side != null && "CLIENT".equals(String.valueOf(side));
        } catch (Throwable ignored) {
            return false;
        }
    }
}
