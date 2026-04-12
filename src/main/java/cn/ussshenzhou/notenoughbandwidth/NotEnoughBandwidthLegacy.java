package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import cn.ussshenzhou.notenoughbandwidth.network.CodecInterceptorInitializer;
import cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.network.FMLNetworkEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

/**
 * @author USS_Shenzhou
 */
@Mod(
        modid = ModConstants.MOD_ID,
        name = ModConstants.MOD_NAME,
        version = ModConstants.VERSION,
        acceptedMinecraftVersions = "[1.12.2]"
)
public class NotEnoughBandwidthLegacy {
    public static final Logger LOGGER = LogManager.getLogger(ModConstants.MOD_ID);

    @Mod.Instance(ModConstants.MOD_ID)
    public static NotEnoughBandwidthLegacy INSTANCE;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ConfigHelper.loadConfig(new NotEnoughBandwidthLegacyConfig());

        ModNetworkRegistry.register();

        cn.ussshenzhou.network.ModNetworkRegistry.register();

        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(this);
    }

    @Mod.EventHandler
    public void onServerStarting(FMLServerStartingEvent event) {
        cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager.init();
    }

    @SubscribeEvent
    public void onClientConnected(FMLNetworkEvent.ClientConnectedToServerEvent event) {
        CodecInterceptorInitializer.install(event.getManager());
    }

    @SubscribeEvent
    public void onServerConnection(FMLNetworkEvent.ServerConnectionFromClientEvent event) {
        CodecInterceptorInitializer.install(event.getManager());
    }

    @SubscribeEvent
    public void onCustomPacketRegistration(FMLNetworkEvent.CustomPacketRegistrationEvent<?> event) {
        NamespaceIndexManager.refreshFromRegistrationEvent(event.getRegistrations(), event.getSide(), event.getOperation());
    }
}
