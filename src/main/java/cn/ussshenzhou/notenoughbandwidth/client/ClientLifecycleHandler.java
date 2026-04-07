package cn.ussshenzhou.notenoughbandwidth.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

/**
 * @author USS_Shenzhou
 * Client-side NEB initialization now happens from the negotiated Forge channel
 * list during handshake, so no eager setup is needed here anymore.
 */
@Mod.EventBusSubscriber(value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientLifecycleHandler {

    private ClientLifecycleHandler() {
    }
}
