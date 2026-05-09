package cn.ussshenzhou.notenoughbandwidth.indextype;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Per-connection immutable NEB index table.
 *
 * <p>Forge 1.20.1 backport note: unlike the original NeoForge branch, this
 * project may derive payload visibility from different handshake/fallback paths
 * per connection. Therefore the final namespace/path table must be bound to the
 * negotiated channel set of the current connection rather than a single mutable
 * global table.</p>
 */
public final class ConnectionIndexTable {
    private final ArrayList<String> namespaces = new ArrayList<>();
    private final ArrayList<ArrayList<String>> paths = new ArrayList<>();
    private final Object2IntMap<String> namespaceMap = new Object2IntOpenHashMap<>();
    private final Int2ObjectArrayMap<Object2IntMap<String>> pathMaps = new Int2ObjectArrayMap<>();

    private ConnectionIndexTable() {
        namespaceMap.defaultReturnValue(-1);
    }

    public static ConnectionIndexTable create(List<ResourceLocation> vanillaTypes, List<ResourceLocation> customTypes) {
        ConnectionIndexTable table = new ConnectionIndexTable();
        AtomicInteger namespaceIndex = new AtomicInteger(1);
        table.namespaces.add("ILLEGAL");
        table.paths.add(new ArrayList<>());
        vanillaTypes.forEach(type -> table.fillSingle(namespaceIndex, type));

        var sorted = new ArrayList<>(customTypes.stream().filter(type -> type != null && !PacketAggregationPacket.TYPE.equals(type)).distinct().toList());
        sorted.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        sorted.forEach(type -> table.fillSingle(namespaceIndex, type));

        if (table.namespaces.size() > 4096 || table.paths.stream().anyMatch(list -> list.size() > 4096)) {
            throw new RuntimeException("There are too many namespaces and/or paths (Max 4096 namespaces, 4096 paths for each namespace). NEB is not designed to work with so many mods.");
        }
        return table;
    }

    private void fillSingle(AtomicInteger namespaceIndex, ResourceLocation packetId) {
        if (!namespaceMap.containsKey(packetId.getNamespace())) {
            namespaceMap.put(packetId.getNamespace(), namespaceIndex.get());
            namespaces.add(packetId.getNamespace());
            paths.add(new ArrayList<>());
            namespaceIndex.getAndIncrement();
        }
        int namespaceId = namespaceMap.getInt(packetId.getNamespace());
        pathMaps.compute(namespaceId, (namespaceId1, pathMap) -> {
            if (pathMap == null) {
                pathMap = new Object2IntOpenHashMap<>();
                pathMap.defaultReturnValue(-1);
            }
            if (!pathMap.containsKey(packetId.getPath())) {
                pathMap.put(packetId.getPath(), pathMap.size());
                paths.get(namespaceId).add(packetId.getPath());
            }
            return pathMap;
        });
    }

    public boolean contains(ResourceLocation type) {
        if (type == null) {
            return false;
        }
        int namespaceId = namespaceMap.getInt(type.getNamespace());
        if (namespaceId <= 0) {
            return false;
        }
        Object2IntMap<String> pathMap = pathMaps.get(namespaceId);
        return pathMap != null && pathMap.containsKey(type.getPath());
    }

    public Tuple<Integer, Integer> getCheckedIndex(ResourceLocation type) {
        int namespaceId = namespaceMap.getInt(type.getNamespace());
        Object2IntMap<String> pathMap = pathMaps.get(namespaceId);
        if (namespaceId <= 0 || pathMap == null || !pathMap.containsKey(type.getPath())) {
            throw new IndexOutOfBoundsException("Missing NEB index for " + type);
        }
        return new Tuple<>(namespaceId, pathMap.getInt(type.getPath()));
    }

    @Nullable
    public ResourceLocation getIdentifierOrNull(int namespaceIndex, int pathIndex) {
        if (namespaceIndex <= 0 || namespaceIndex >= namespaces.size()) {
            return null;
        }
        ArrayList<String> namespacePaths = paths.get(namespaceIndex);
        if (pathIndex < 0 || pathIndex >= namespacePaths.size()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(namespaces.get(namespaceIndex), namespacePaths.get(pathIndex));
    }
}
