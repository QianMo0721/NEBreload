package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.network.payload.HandlerThread;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistrar;
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

        // 聚合包与统计包都改为通过自建 payload 注册层注册，继续向 NeoForge 分支收口。
        cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.networkPacketRegistry(
                new PayloadRegistrar("1").executesOn(HandlerThread.NETWORK)
        );
        cn.ussshenzhou.network.ModNetworkRegistry.networkPacketRegistry(
                new PayloadRegistrar("1").executesOn(HandlerThread.NETWORK)
        );

        // ModKey uses @EventBusSubscriber(value = Dist.CLIENT, bus = Bus.FORGE)
        // so key registration happens automatically via Forge event system.
    }
}
