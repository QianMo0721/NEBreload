package cn.ussshenzhou.notenoughbandwidth.mixin;

import net.minecraftforge.fml.relauncher.IFMLLoadingPlugin;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;

import javax.annotation.Nullable;
import java.util.Map;

@IFMLLoadingPlugin.Name("NEBLMixinLoader")
@IFMLLoadingPlugin.MCVersion("1.12.2")
@IFMLLoadingPlugin.TransformerExclusions({"cn.ussshenzhou.notenoughbandwidth.mixin.MixinLoader"})
public class MixinLoader implements IFMLLoadingPlugin {
    private static boolean initialized;

    public MixinLoader() {
        if (!initialized) {
            initialized = true;
            MixinBootstrap.init();
            Mixins.addConfiguration("nebl.mixins.json");
            MixinEnvironment.getDefaultEnvironment().setObfuscationContext("searge");
        }
    }

    @Override
    public String[] getASMTransformerClass() {
        return new String[0];
    }

    @Override
    public String getModContainerClass() {
        return null;
    }

    @Nullable
    @Override
    public String getSetupClass() {
        return null;
    }

    @Override
    public void injectData(Map<String, Object> data) {
    }

    @Override
    public String getAccessTransformerClass() {
        return null;
    }
}
