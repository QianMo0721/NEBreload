package cn.ussshenzhou.notenoughbandwidth.network.payload;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;

public class NebTransportSetupPayload implements NebPayload {
    public static final String TYPE = ModConstants.MOD_ID + ":transport_setup";
    public static final NebTransportSetupPayload REQUEST = new NebTransportSetupPayload(false);
    public static final NebTransportSetupPayload ACK = new NebTransportSetupPayload(true);
    public static final PayloadCodec<NebTransportSetupPayload> CODEC = new PayloadCodec<NebTransportSetupPayload>() {
        @Override
        public void encode(PacketBuffer buf, NebTransportSetupPayload payload) {
            buf.writeBoolean(payload.ack());
        }

        @Override
        public NebTransportSetupPayload decode(PacketBuffer buf) {
            return new NebTransportSetupPayload(buf.readBoolean());
        }
    };

    private final boolean ack;

    public NebTransportSetupPayload(boolean ack) {
        this.ack = ack;
    }

    public boolean ack() {
        return ack;
    }

    @Override
    public String type() {
        return TYPE;
    }

    public static void handle(NebTransportSetupPayload payload, PayloadContext context) {
        NetworkManager connection = context.connection();
        if (connection == null) {
            return;
        }
        NetworkPayloadSetup setup = ChannelAttributes.getPayloadSetup(connection);
        if (setup == null) {
            setup = NetworkPayloadSetup.empty();
        }
        setup.register(PacketAggregationPacket.CHANNEL_NAME, "1");
        setup.register(TYPE, "1");
        ChannelAttributes.setPayloadSetup(connection, setup);
        if (!AggregationManager.isInitialized()) {
            AggregationManager.init();
        }
        if (!payload.ack()) {
            context.reply(ACK);
        }
    }
}
