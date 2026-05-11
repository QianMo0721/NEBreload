package cn.ussshenzhou.notenoughbandwidth.indextype;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NetworkPayloadSetup;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.network.Connection;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author USS_Shenzhou
 */
public class NamespaceIndexManager {
    private static volatile boolean initialized = false;
    private static final ArrayList<String> NAMESPACES = new ArrayList<>();
    private static final ArrayList<ArrayList<String>> PATHS = new ArrayList<>();
    private static final Object2IntMap<String> NAMESPACE_MAP = new Object2IntOpenHashMap<>();
    private static final Int2ObjectArrayMap<Object2IntMap<String>> PATH_MAPS = new Int2ObjectArrayMap<>();
    private static final WeakHashMap<Connection, ConnectionIndexTable> CONNECTION_TABLE_CACHE = new WeakHashMap<>();

    static {
        NAMESPACE_MAP.defaultReturnValue(-1);
    }

    private static final List<String> VANILLA_PATHS = new ArrayList<>() {{
        add("bundle");
        add("bundle_delimiter");
        add("add_entity");
        add("animate");
        add("award_stats");
        add("block_changed_ack");
        add("block_destruction");
        add("block_entity_data");
        add("block_event");
        add("block_update");
        add("boss_event");
        add("change_difficulty");
        add("chunk_batch_finished");
        add("chunk_batch_start");
        add("chunks_biomes");
        add("clear_titles");
        add("command_suggestions");
        add("commands");
        add("container_close");
        add("container_set_content");
        add("container_set_data");
        add("container_set_slot");
        add("cooldown");
        add("custom_chat_completions");
        add("damage_event");
        add("debug/block_value");
        add("debug/chunk_value");
        add("debug/entity_value");
        add("debug/event");
        add("debug_sample");
        add("delete_chat");
        add("disguised_chat");
        add("entity_event");
        add("entity_position_sync");
        add("explode");
        add("forget_level_chunk");
        add("game_event");
        add("game_test_highlight_pos");
        add("mount_screen_open");
        add("hurt_animation");
        add("initialize_border");
        add("level_chunk_with_light");
        add("level_event");
        add("level_particles");
        add("light_update");
        add("login");
        add("low_disk_space_warning");
        add("map_item_data");
        add("merchant_offers");
        add("move_entity_pos");
        add("move_entity_pos_rot");
        add("move_minecart_along_track");
        add("move_entity_rot");
        add("move_vehicle");
        add("open_book");
        add("open_screen");
        add("open_sign_editor");
        add("place_ghost_recipe");
        add("player_abilities");
        add("player_chat");
        add("player_combat_end");
        add("player_combat_enter");
        add("player_combat_kill");
        add("player_info_remove");
        add("player_info_update");
        add("player_look_at");
        add("player_position");
        add("player_rotation");
        add("recipe_book_add");
        add("recipe_book_remove");
        add("recipe_book_settings");
        add("remove_entities");
        add("remove_mob_effect");
        add("respawn");
        add("rotate_head");
        add("section_blocks_update");
        add("select_advancements_tab");
        add("server_data");
        add("set_action_bar_text");
        add("set_border_center");
        add("set_border_lerp_size");
        add("set_border_size");
        add("set_border_warning_delay");
        add("set_border_warning_distance");
        add("set_camera");
        add("set_chunk_cache_center");
        add("set_chunk_cache_radius");
        add("set_default_spawn_position");
        add("set_display_objective");
        add("set_entity_data");
        add("set_entity_link");
        add("set_entity_motion");
        add("set_equipment");
        add("set_experience");
        add("set_health");
        add("set_held_slot");
        add("set_objective");
        add("set_passengers");
        add("set_player_team");
        add("set_score");
        add("set_simulation_distance");
        add("set_subtitle_text");
        add("set_time");
        add("set_title_text");
        add("set_titles_animation");
        add("sound_entity");
        add("sound");
        add("start_configuration");
        add("stop_sound");
        add("system_chat");
        add("tab_list");
        add("tag_query");
        add("take_item_entity");
        add("teleport_entity");
        add("test_instance_block_status");
        add("update_advancements");
        add("update_attributes");
        add("update_mob_effect");
        add("update_recipes");
        add("projectile_power");
        add("waypoint");
        add("accept_teleportation");
        add("block_entity_tag_query");
        add("bundle_item_selected");
        add("change_game_mode");
        add("chat_ack");
        add("chat_command");
        add("chat_command_signed");
        add("chat");
        add("chat_session_update");
        add("chunk_batch_received");
        add("client_command");
        add("client_tick_end");
        add("command_suggestion");
        add("configuration_acknowledged");
        add("container_button_click");
        add("container_click");
        add("container_slot_state_changed");
        add("debug_subscription_request");
        add("edit_book");
        add("entity_tag_query");
        add("interact");
        add("jigsaw_generate");
        add("lock_difficulty");
        add("move_player_pos");
        add("move_player_pos_rot");
        add("move_player_rot");
        add("move_player_status_only");
        add("paddle_boat");
        add("pick_item_from_block");
        add("pick_item_from_entity");
        add("place_recipe");
        add("player_action");
        add("player_command");
        add("player_input");
        add("player_loaded");
        add("recipe_book_change_settings");
        add("recipe_book_seen_recipe");
        add("rename_item");
        add("seen_advancements");
        add("select_trade");
        add("set_beacon");
        add("set_carried_item");
        add("set_command_block");
        add("set_command_minecart");
        add("set_creative_mode_slot");
        add("set_jigsaw_block");
        add("set_structure_block");
        add("set_test_block");
        add("test_instance_block_action");
        add("sign_update");
        add("swing");
        add("teleport_to_entity");
        add("use_item_on");
        add("use_item");
        add("reset_score");
        add("ticking_state");
        add("ticking_step");
        add("set_cursor_item");
        add("set_player_inventory");
    }};

    public static boolean isInitialized() {
        return initialized;
    }

    public synchronized static void initFromNegotiatedChannels(Map<ResourceLocation, String> remoteChannels) {
        init(buildNegotiatedPayloadTypes(remoteChannels));
    }

    public synchronized static void initFromPayloadSetup(NetworkPayloadSetup setup) {
        if (setup == null) {
            init(List.of());
            return;
        }
        init(collectIndexedCustomTypes(setup));
    }

    public synchronized static void init(List<ResourceLocation> types) {
        initialized = false;
        NAMESPACES.clear();
        PATHS.clear();
        NAMESPACE_MAP.clear();
        PATH_MAPS.clear();

        AtomicInteger namespaceIndex = new AtomicInteger(1);
        NAMESPACES.add("ILLEGAL");
        PATHS.add(new ArrayList<>());

        indexVanillaPackets(namespaceIndex);
        indexCustomPayloads(types, namespaceIndex);

        initTrace();
        if (NAMESPACES.size() > 4096 || PATHS.stream().anyMatch(l -> l.size() > 4096)) {
            throw new RuntimeException("There are too many namespaces and/or paths (Max 4096 namespaces, 4096 paths for each namespace). NEB is not designed to work with so many mods.");
        }
        initialized = true;
    }

    public synchronized static void initForConnection(@Nullable Connection connection, @Nullable NetworkPayloadSetup setup) {
        if (connection == null || connection.channel() == null) {
            return;
        }
        List<ResourceLocation> customTypes = setup == null
                ? List.of()
                : collectIndexedCustomTypes(setup);
        ConnectionIndexTable table = ConnectionIndexTable.create(getVanillaTypes(), customTypes);
        CONNECTION_TABLE_CACHE.put(connection, table);
        ChannelAttributes.setConnectionIndexTable(connection, table);
        traceConnectionTable(connection, table, customTypes);
    }

    public synchronized static void clearConnection(@Nullable Connection connection) {
        if (connection == null) {
            return;
        }
        CONNECTION_TABLE_CACHE.remove(connection);
        ChannelAttributes.setConnectionIndexTable(connection, null);
    }

    @Nullable
    public static ConnectionIndexTable getConnectionTable(@Nullable Connection connection) {
        if (connection == null || connection.channel() == null) {
            return null;
        }
        ConnectionIndexTable table = ChannelAttributes.getConnectionIndexTable(connection);
        if (table != null) {
            return table;
        }
        synchronized (NamespaceIndexManager.class) {
            table = CONNECTION_TABLE_CACHE.get(connection);
            if (table != null) {
                ChannelAttributes.setConnectionIndexTable(connection, table);
            }
            return table;
        }
    }

    public static boolean contains(@Nullable Connection connection, ResourceLocation type) {
        ConnectionIndexTable table = getConnectionTable(connection);
        return table != null && table.contains(type);
    }

    public static Tuple<Integer, Integer> getCheckedIndex(@Nullable Connection connection, ResourceLocation type) {
        ConnectionIndexTable table = getConnectionTable(connection);
        if (table == null) {
            throw new IndexOutOfBoundsException("Missing NEB connection index table for " + type);
        }
        return table.getCheckedIndex(type);
    }

    @Nullable
    public static ResourceLocation getIdentifierOrNull(@Nullable Connection connection, int namespaceIndex, int pathIndex) {
        ConnectionIndexTable table = getConnectionTable(connection);
        if (table == null) {
            return null;
        }
        return table.getIdentifierOrNull(namespaceIndex, pathIndex);
    }

    public static boolean canAggregate(@Nullable Connection connection, ResourceLocation type) {
        return type != null && contains(connection, type);
    }

    public static boolean ready(@Nullable Connection connection) {
        return getConnectionTable(connection) != null;
    }

    private static void indexVanillaPackets(AtomicInteger namespaceIndex) {
        VANILLA_PATHS.forEach(path -> fillSingle(namespaceIndex, ResourceLocation.fromNamespaceAndPath("minecraft", path)));
    }

    private static void indexCustomPayloads(List<ResourceLocation> types, AtomicInteger namespaceIndex) {
        var sorted = new ArrayList<>(types.stream().distinct().toList());
        sorted.sort(Comparator.comparing(ResourceLocation::getNamespace).thenComparing(ResourceLocation::getPath));
        sorted.forEach(type -> {
            if (type == null || isInternalAggregationTransport(type)) {
                return;
            }
            fillSingle(namespaceIndex, type);
        });
    }

    private static boolean isInternalAggregationTransport(ResourceLocation type) {
        return PacketAggregationPacket.TYPE.equals(type);
    }

    private static List<ResourceLocation> buildNegotiatedPayloadTypes(Map<ResourceLocation, String> remoteChannels) {
        var result = new ArrayList<ResourceLocation>();
        if (remoteChannels == null || remoteChannels.isEmpty()) {
            return result;
        }
        PayloadRegistry.registrations().forEach(registration -> {
            ResourceLocation id = registration.id();
            if (remoteChannels.containsKey(id)) {
                result.add(id);
            }
        });
        return result;
    }

    private static List<ResourceLocation> collectIndexedCustomTypes(NetworkPayloadSetup setup) {
        if (setup == null) {
            return List.of();
        }
        var result = new ArrayList<ResourceLocation>();
        setup.getChannels(net.minecraft.network.ConnectionProtocol.PLAY).forEach((id, channel) -> {
            if (id == null || isInternalAggregationTransport(id)) {
                return;
            }
            var registration = PayloadRegistry.getRegistration(id);
            if (registration == null || registration.optional()) {
                return;
            }
            result.add(id);
        });
        return result;
    }

    private static List<ResourceLocation> getVanillaTypes() {
        return VANILLA_PATHS.stream()
                .map(path -> ResourceLocation.fromNamespaceAndPath("minecraft", path))
                .toList();
    }

    private static void traceConnectionTable(Connection connection, ConnectionIndexTable table, List<ResourceLocation> customTypes) {
        var logger = LogUtils.getLogger();
        if (logger.isDebugEnabled()) {
            logger.debug("[NEB] Built connection index table for {} with {} custom payload types.",
                    connection.getRemoteAddress(), customTypes.stream().filter(type -> type != null && !PacketAggregationPacket.TYPE.equals(type)).count());
        }
    }

    private static void initTrace() {
        var logger = LogUtils.getLogger();
        if (logger.isDebugEnabled()) {
            logger.debug("PacketTypeIndexManager initialized.");
            NAMESPACE_MAP.forEach((namespace, id) -> {
                logger.debug("namespace: {} id: {}", namespace, id);
                PATH_MAPS.get(id).forEach((path, id1) -> logger.debug("- path: {} id: {}", path, id1));
            });
        }
    }

    private static void fillSingle(AtomicInteger namespaceIndex, ResourceLocation packetId) {
        if (!NAMESPACE_MAP.containsKey(packetId.getNamespace())) {
            NAMESPACE_MAP.put(packetId.getNamespace(), namespaceIndex.get());
            NAMESPACES.add(packetId.getNamespace());
            PATHS.add(new ArrayList<>());
            namespaceIndex.getAndIncrement();
        }
        int namespaceId = NAMESPACE_MAP.getInt(packetId.getNamespace());
        PATH_MAPS.compute(namespaceId, (namespaceId1, pathMap) -> {
            if (pathMap == null) {
                pathMap = new Object2IntOpenHashMap<>();
            }
            if (!pathMap.containsKey(packetId.getPath())) {
                pathMap.put(packetId.getPath(), pathMap.size());
                PATHS.get(namespaceId).add(packetId.getPath());
            }
            return pathMap;
        });
    }

    @Deprecated
    public static boolean contains(ResourceLocation type) {
        if (!initialized) {
            return false;
        }
        return NAMESPACE_MAP.containsKey(type.getNamespace()) && PATH_MAPS.get(NAMESPACE_MAP.getInt(type.getNamespace())).containsKey(type.getPath());
    }

    @Deprecated
    public static Tuple<Integer, Integer> getCheckedIndex(ResourceLocation type) {
        int namespaceId = NAMESPACE_MAP.getInt(type.getNamespace());
        return new Tuple<>(namespaceId, PATH_MAPS.get(namespaceId).getInt(type.getPath()));
    }

    @Deprecated
    @Nullable
    public static ResourceLocation getIdentifierOrNull(int namespaceIndex, int pathIndex) {
        if (!initialized || namespaceIndex <= 0 || namespaceIndex >= NAMESPACES.size()) {
            return null;
        }
        ArrayList<String> paths = PATHS.get(namespaceIndex);
        if (pathIndex < 0 || pathIndex >= paths.size()) {
            return null;
        }
        return ResourceLocation.fromNamespaceAndPath(NAMESPACES.get(namespaceIndex), paths.get(pathIndex));
    }

    @Deprecated
    public static ResourceLocation getIdentifier(int namespaceIndex, int pathIndex) {
        ResourceLocation id = getIdentifierOrNull(namespaceIndex, pathIndex);
        if (id == null) {
            throw new IndexOutOfBoundsException("Invalid NEB indexed payload prefix: namespace=" + namespaceIndex + ", path=" + pathIndex);
        }
        return id;
    }

    @Deprecated
    public static boolean canAggregate(ResourceLocation type) {
        return type != null && contains(type);
    }

    @Deprecated
    public static boolean ready() {
        return initialized;
    }
}
