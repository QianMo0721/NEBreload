package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * @author USS_Shenzhou
 */
@Mod(ModConstants.MOD_ID)
public class NotEnoughBandwidthLegacy {
    private static final Logger LOGGER = LogUtils.getLogger();

    public NotEnoughBandwidthLegacy() {
        ConfigHelper.loadConfig(new NotEnoughBandwidthLegacyConfig());

        // Register the Forge simple channel (PacketAggregationPacket, StatQuery, StatRespond)
        ModNetworkRegistry.register();

        // ModKey uses @EventBusSubscriber(value = Dist.CLIENT, bus = Bus.FORGE)
        // so key registration happens automatically via Forge event system.
    }
}
