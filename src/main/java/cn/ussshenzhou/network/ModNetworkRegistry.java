package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * Debug/statistics messages are kept off the aggregation transport channel.
 */
public class ModNetworkRegistry {
    public static final SimpleNetworkWrapper QUERY_CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(ModConstants.MOD_ID + "_q");

    public static final SimpleNetworkWrapper RESPOND_CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel(ModConstants.MOD_ID + "_r");

    private static boolean registered = false;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        QUERY_CHANNEL.registerMessage(StatQuery.Handler.class, StatQuery.class, 0, Side.SERVER);
        RESPOND_CHANNEL.registerMessage(StatRespond.Handler.class, StatRespond.class, 0, Side.CLIENT);
    }
}
