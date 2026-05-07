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
        // 这些 channel 在代理链路下必须允许“远端缺失”，否则 Velocity 转发到 Forge 1.20.1
        // 子服时会被 Forge 视为 vanilla 连接，并在登录前因为 NEB 的必需 channel 拒绝接入。
        cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.networkPacketRegistry(
                new PayloadRegistrar("1").optional().executesOn(HandlerThread.NETWORK)
        );
        cn.ussshenzhou.network.ModNetworkRegistry.networkPacketRegistry(
                new PayloadRegistrar("1").optional().executesOn(HandlerThread.NETWORK)
        );

        // ModKey uses @EventBusSubscriber(value = Dist.CLIENT, bus = Bus.FORGE)
        // so key registration happens automatically via Forge event system.
    }
}
