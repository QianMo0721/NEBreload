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

        // Keep the aggregation payload channel registered so Forge advertises and
        // accepts the custom payload id during channel negotiation, even though
        // the actual send/receive path is now handled directly via custom payload packets.
        ModNetworkRegistry.register();

        // Register debug/stat packets separately, matching the original project structure.
        cn.ussshenzhou.network.ModNetworkRegistry.register();

        // ModKey uses @EventBusSubscriber(value = Dist.CLIENT, bus = Bus.FORGE)
        // so key registration happens automatically via Forge event system.
    }
}
