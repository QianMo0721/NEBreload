package cn.ussshenzhou.notenoughbandwidth.util;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Aggregation transport registry only.
 */
public class ModNetworkRegistry {
    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            PacketAggregationPacket.TYPE,
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static final ConcurrentHashMap<ResourceLocation, Class<?>> ID_TO_CLASS = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<Class<?>, ResourceLocation> CLASS_TO_ID = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, BiConsumer<Object, Supplier<NetworkEvent.Context>>> HANDLERS =
            new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Class<?>, Function<FriendlyByteBuf, Object>> DECODERS =
            new ConcurrentHashMap<>();

    public static void register() {
        registerPacket(
                PacketAggregationPacket.class,
                PacketAggregationPacket.TYPE,
                PacketAggregationPacket::encode,
                PacketAggregationPacket::new,
                (pkt, ctx) -> pkt.handler(ctx)
        );
    }

    @SuppressWarnings("unchecked")
    private static <MSG> void registerPacket(
            Class<MSG> clazz,
            ResourceLocation rl,
            BiConsumer<MSG, FriendlyByteBuf> encoder,
            Function<FriendlyByteBuf, MSG> decoder,
            BiConsumer<MSG, Supplier<NetworkEvent.Context>> handler
    ) {
        CHANNEL.registerMessage(id++, clazz, encoder, decoder, handler);
        ID_TO_CLASS.put(rl, clazz);
        CLASS_TO_ID.put(clazz, rl);
        HANDLERS.put(clazz, (BiConsumer<Object, Supplier<NetworkEvent.Context>>) (BiConsumer<?, ?>) handler);
        DECODERS.put(clazz, (Function<FriendlyByteBuf, Object>) (Function<?, ?>) decoder);
    }

    public static ResourceLocation getPacketId(Class<?> clazz) {
        if (clazz == null) return null;
        return CLASS_TO_ID.get(clazz);
    }

    @SuppressWarnings("unchecked")
    public static void dispatchPacket(
            ResourceLocation typeId,
            FriendlyByteBuf buf,
            NetworkEvent.Context context) {
        Class<?> clazz = ID_TO_CLASS.get(typeId);
        if (clazz == null) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "[NEB] dispatchPacket: no class for {}", typeId);
            return;
        }
        var decoder = (Function<FriendlyByteBuf, Object>) DECODERS.get(clazz);
        var handler = (BiConsumer<Object, Supplier<NetworkEvent.Context>>) HANDLERS.get(clazz);
        if (decoder == null || handler == null) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "[NEB] dispatchPacket: no decoder/handler for {}", typeId);
            return;
        }
        try {
            Object pkt = decoder.apply(buf);
            Supplier<NetworkEvent.Context> ctxSupplier = () -> context;
            handler.accept(pkt, ctxSupplier);
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error(
                    "[NEB] dispatchPacket failed for {}", typeId, e);
        }
    }
}
