package cn.ussshenzhou.notenoughbandwidth.network.payload;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class NetworkPayloadSetup {
    private final Map<String, NetworkChannel> channels = new ConcurrentHashMap<String, NetworkChannel>();

    public static NetworkPayloadSetup empty() {
        return new NetworkPayloadSetup();
    }

    public void register(PayloadRegistration<?> registration) {
        channels.put(registration.id(), new NetworkChannel(registration.id(), registration.version()));
    }

    public void register(String id, String version) {
        channels.put(id, new NetworkChannel(id, version));
    }

    public boolean hasChannel(String id) {
        return channels.containsKey(id);
    }

    public Map<String, NetworkChannel> channels() {
        return Collections.unmodifiableMap(channels);
    }

    public Collection<NetworkChannel> values() {
        return Collections.unmodifiableCollection(channels.values());
    }
}
