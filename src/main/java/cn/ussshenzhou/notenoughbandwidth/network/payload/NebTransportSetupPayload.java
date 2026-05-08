package cn.ussshenzhou.notenoughbandwidth.network.payload;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Lightweight PLAY-stage fallback negotiation for proxy chains that do not preserve
 * Forge 1.20.1's FML3 hostname token.
 *
 * Important: this fallback must only recover the NEB aggregation transport itself,
 * not recreate a full synthetic negotiated payload set, otherwise the resulting
 * semantics would be wider than the original Forge handshake result.
 */
public record NebTransportSetupPayload(boolean ack) implements NebPayload {
    public static final ResourceLocation TYPE = ResourceLocation.fromNamespaceAndPath("nebl", "transport_setup");
    public static final NebTransportSetupPayload REQUEST = new NebTransportSetupPayload(false);
    public static final NebTransportSetupPayload ACK = new NebTransportSetupPayload(true);

    public static final PayloadCodec<NebTransportSetupPayload> CODEC = new PayloadCodec<>() {
        @Override
        public void encode(FriendlyByteBuf buf, NebTransportSetupPayload payload) {
            buf.writeBoolean(payload.ack());
        }

        @Override
        public NebTransportSetupPayload decode(FriendlyByteBuf buf) {
            return new NebTransportSetupPayload(buf.readBoolean());
        }
    };

    @Override
    public ResourceLocation type() {
        return TYPE;
    }

    public static void handle(NebTransportSetupPayload payload, PayloadContext context) {
        if (context.connection() == null) {
            return;
        }
        var transportRegistration = PayloadRegistry.getRegistration(PacketAggregationPacket.TYPE);
        if (transportRegistration == null) {
            return;
        }
        NetworkPayloadSetup existingSetup = ChannelAttributes.getPayloadSetup(context.connection());
        NetworkPayloadSetup setup = existingSetup == null
                ? NetworkPayloadSetup.empty()
                : new NetworkPayloadSetup(existingSetup.channels());
        try {
            var connectionData = net.minecraftforge.network.NetworkHooks.getConnectionData(context.connection());
            if (connectionData != null && !connectionData.getChannels().isEmpty()) {
                connectionData.getChannels().forEach(setup::register);
            }
        } catch (Throwable ignored) {
        }
        setup.register(transportRegistration);
        if (!setup.hasChannel(PacketAggregationPacket.TYPE)) {
            return;
        }
        ChannelAttributes.setPayloadSetup(context.connection(), setup);
        NamespaceIndexManager.initFromPayloadSetup(setup);
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
        if (!payload.ack()) {
            context.reply(ACK);
        }
    }
}
