package cn.ussshenzhou.notenoughbandwidth.network.payload;

import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class NetworkPayloadSetup {
    private final Map<ResourceLocation, NetworkChannel> channels = new ConcurrentHashMap<>();

    public static NetworkPayloadSetup empty() {
        return new NetworkPayloadSetup();
    }

    public void register(PayloadRegistration<?> registration) {
        channels.put(registration.id(), new NetworkChannel(registration.id(), registration.version()));
    }

    public boolean hasChannel(ResourceLocation id) {
        return channels.containsKey(id);
    }

    public Map<ResourceLocation, NetworkChannel> channels() {
        return Collections.unmodifiableMap(channels);
    }

    public Map<ResourceLocation, NetworkChannel> channels(PacketFlow flow) {
        return Collections.unmodifiableMap(channels.entrySet().stream()
                .filter(entry -> {
                    var registration = PayloadRegistry.getRegistration(entry.getKey());
                    return registration != null && registration.matches(flow);
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue)));
    }
}
