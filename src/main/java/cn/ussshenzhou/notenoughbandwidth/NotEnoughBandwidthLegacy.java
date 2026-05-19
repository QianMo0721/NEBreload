package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

@Mod(modid = ModConstants.MOD_ID, name = ModConstants.MOD_NAME, version = ModConstants.VERSION, acceptableRemoteVersions = "*")
public class NotEnoughBandwidthLegacy {

    @Mod.Instance(ModConstants.MOD_ID)
    public static NotEnoughBandwidthLegacy INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHelper.load(event.getSuggestedConfigurationFile());
        ModNetworkRegistry.init();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        AggregationManager.init();
    }
}
