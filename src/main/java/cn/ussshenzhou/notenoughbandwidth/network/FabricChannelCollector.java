package cn.ussshenzhou.notenoughbandwidth.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.indextype.NamespaceIndexManager;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

public final class FabricChannelCollector {
    private static final Set<Identifier> SERVER_CHANNELS = new TreeSet<>(Identifier::compareTo);
    private static final Set<Identifier> CLIENT_CHANNELS = new TreeSet<>(Identifier::compareTo);

    private FabricChannelCollector() {
    }

    public static synchronized void initServerPlayChannels(ServerPlayNetworkHandler handler) {
        SERVER_CHANNELS.clear();
        SERVER_CHANNELS.addAll(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getReceived(handler));
        SERVER_CHANNELS.addAll(net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.getSendable(handler));
        addNebChannels(SERVER_CHANNELS);
        rebuildIndex();
    }

    public static synchronized void initFromIdentifiers(Collection<Identifier> identifiers) {
        CLIENT_CHANNELS.clear();
        CLIENT_CHANNELS.addAll(identifiers);
        addNebChannels(CLIENT_CHANNELS);
        rebuildIndex();
    }

    private static void rebuildIndex() {
        Set<Identifier> channels = new TreeSet<>(Identifier::compareTo);
        channels.addAll(SERVER_CHANNELS);
        channels.addAll(CLIENT_CHANNELS);
        NamespaceIndexManager.init(new ArrayList<>(channels));
    }

    private static void addNebChannels(Set<Identifier> channels) {
        channels.add(Identifier.of(ModConstants.MOD_ID, "packet_aggregation_packet"));
        channels.add(Identifier.of(ModConstants.MOD_ID, "stat_query"));
        channels.add(Identifier.of(ModConstants.MOD_ID, "stat_resp"));
    }
}
