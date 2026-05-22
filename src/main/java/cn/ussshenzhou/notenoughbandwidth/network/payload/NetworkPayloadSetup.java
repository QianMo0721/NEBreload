package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.EnumConnectionState;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

public final class NetworkPayloadSetup {
    private final Map<EnumConnectionState, Map<String, NetworkChannel>> channels;

    public NetworkPayloadSetup() {
        this.channels = new EnumMap<EnumConnectionState, Map<String, NetworkChannel>>(EnumConnectionState.class);
    }

    public NetworkPayloadSetup(Map<EnumConnectionState, Map<String, NetworkChannel>> channels) {
        this.channels = new EnumMap<EnumConnectionState, Map<String, NetworkChannel>>(EnumConnectionState.class);
        for (Map.Entry<EnumConnectionState, Map<String, NetworkChannel>> entry : channels.entrySet()) {
            this.channels.put(entry.getKey(), new HashMap<String, NetworkChannel>(entry.getValue()));
        }
    }

    public static NetworkPayloadSetup empty() {
        return new NetworkPayloadSetup();
    }

    public void register(PayloadRegistration<?> registration) {
        register(EnumConnectionState.PLAY, registration.id(), registration.version());
    }

    public void register(String id, String version) {
        register(EnumConnectionState.PLAY, id, version);
    }

    public void register(EnumConnectionState protocol, String id, String version) {
        channels.computeIfAbsent(protocol, p -> new HashMap<String, NetworkChannel>())
                .put(id, new NetworkChannel(id, version));
    }

    public boolean hasChannel(String id) {
        for (Map<String, NetworkChannel> map : channels.values()) {
            if (map.containsKey(id)) {
                return true;
            }
        }
        return false;
    }

    public Map<EnumConnectionState, Map<String, NetworkChannel>> channels() {
        Map<EnumConnectionState, Map<String, NetworkChannel>> copy = new EnumMap<EnumConnectionState, Map<String, NetworkChannel>>(EnumConnectionState.class);
        for (Map.Entry<EnumConnectionState, Map<String, NetworkChannel>> entry : channels.entrySet()) {
            copy.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(copy);
    }

    public Map<String, NetworkChannel> getChannels(EnumConnectionState protocol) {
        Map<String, NetworkChannel> result = channels.get(protocol);
        if (result == null) {
            return Collections.emptyMap();
        }
        return Collections.unmodifiableMap(result);
    }

    public Map<String, NetworkChannel> channels(boolean clientbound) {
        Map<String, NetworkChannel> filtered = new HashMap<String, NetworkChannel>();
        for (Map.Entry<String, NetworkChannel> entry : getChannels(EnumConnectionState.PLAY).entrySet()) {
            PayloadRegistration<?> registration = PayloadRegistry.getRegistration(entry.getKey());
            if (registration != null && registration.matchesFlow(clientbound)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        return Collections.unmodifiableMap(filtered);
    }

    public Collection<NetworkChannel> values() {
        return Collections.unmodifiableCollection(getChannels(EnumConnectionState.PLAY).values());
    }
}
