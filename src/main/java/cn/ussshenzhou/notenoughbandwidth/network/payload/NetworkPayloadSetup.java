package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.ConnectionProtocol;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public final class NetworkPayloadSetup {
    private final Map<ConnectionProtocol, Map<ResourceLocation, NetworkChannel>> channels;

    public NetworkPayloadSetup() {
        this.channels = new EnumMap<>(ConnectionProtocol.class);
    }

    public NetworkPayloadSetup(Map<ConnectionProtocol, Map<ResourceLocation, NetworkChannel>> channels) {
        this.channels = new EnumMap<>(ConnectionProtocol.class);
        channels.forEach((protocol, values) -> this.channels.put(protocol, new HashMap<>(values)));
    }

    public static NetworkPayloadSetup empty() {
        return new NetworkPayloadSetup();
    }

    public void register(PayloadRegistration<?> registration) {
        channels.computeIfAbsent(ConnectionProtocol.PLAY, protocol -> new HashMap<>())
                .put(registration.id(), new NetworkChannel(registration.id(), registration.version()));
    }

    public boolean hasChannel(ResourceLocation id) {
        return channels.values().stream().anyMatch(map -> map.containsKey(id));
    }

    public Map<ConnectionProtocol, Map<ResourceLocation, NetworkChannel>> channels() {
        return Collections.unmodifiableMap(channels);
    }

    public Map<ResourceLocation, NetworkChannel> getChannels(ConnectionProtocol protocol) {
        return Collections.unmodifiableMap(channels.getOrDefault(protocol, Collections.emptyMap()));
    }

    public Map<ResourceLocation, NetworkChannel> channels(PacketFlow flow) {
        return Collections.unmodifiableMap(getChannels(ConnectionProtocol.PLAY).entrySet().stream()
                .filter(entry -> {
                    var registration = PayloadRegistry.getRegistration(entry.getKey());
                    return registration != null && registration.matches(flow);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
