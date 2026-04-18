package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Optional;

/**
 * Debug/statistics messages are kept off the aggregation transport channel.
 */
public class ModNetworkRegistry {
    private static final String PROTOCOL_VERSION = "1";
    private static final ConcurrentHashMap<Class<?>, ResourceLocation> CLASS_TO_ID = new ConcurrentHashMap<>();

    public static final SimpleChannel QUERY_CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "stat_query"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    public static final SimpleChannel RESPOND_CHANNEL = NetworkRegistry.newSimpleChannel(
            ResourceLocation.fromNamespaceAndPath(ModConstants.MOD_ID, "stat_resp"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static boolean registered = false;

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        CLASS_TO_ID.put(StatQuery.class, StatQuery.TYPE);
        CLASS_TO_ID.put(StatRespond.class, StatRespond.TYPE);

        QUERY_CHANNEL.registerMessage(
                0,
                StatQuery.class,
                StatQuery::encode,
                StatQuery::new,
                (pkt, ctx) -> pkt.handle(ctx),
                Optional.of(NetworkDirection.PLAY_TO_SERVER)
        );

        RESPOND_CHANNEL.registerMessage(
                0,
                StatRespond.class,
                StatRespond::encode,
                StatRespond::new,
                (pkt, ctx) -> pkt.handle(ctx),
                Optional.of(NetworkDirection.PLAY_TO_CLIENT)
        );
    }

    public static ResourceLocation getPacketId(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return CLASS_TO_ID.get(clazz);
    }
}
