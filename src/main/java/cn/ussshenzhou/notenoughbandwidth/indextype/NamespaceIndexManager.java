package cn.ussshenzhou.notenoughbandwidth.indextype;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author USS_Shenzhou
 */
public class NamespaceIndexManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static volatile boolean initialized = false;
    private static volatile IndexState STATE = new IndexState(
            new ArrayList<String>(),
            new ArrayList<ArrayList<String>>(),
            new Object2IntOpenHashMap<String>(),
            new HashMap<Integer, Object2IntMap<String>>()
    );

    private static final List<String> VANILLA_PATHS = new ArrayList<String>() {{
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

    /**
     * Initialize from the negotiated PLAY channel set of the current connection.
     * This mirrors the original project's behavior more closely than scanning all
     * locally registered channels, because only mutually visible channels may be
     * indexed safely on both sides.
     */
    public synchronized static void initFromNegotiatedChannels(java.util.Map<ResourceLocation, String> remoteChannels) {
        ArrayList<ResourceLocation> types = new ArrayList<ResourceLocation>();
        types.addAll(collectNegotiatedChannelNames(remoteChannels));
        init(types);
    }

    public synchronized static void init(List<ResourceLocation> types) {
        if (initialized) {
            return;
        }
        rebuild(types);
    }

    public synchronized static void refreshFromRegisteredChannels() {
        ArrayList<ResourceLocation> types = new ArrayList<ResourceLocation>();
        collectRegisteredChannels(types, Side.CLIENT);
        collectRegisteredChannels(types, Side.SERVER);
        rebuild(types);
    }

    public synchronized static void refreshFromRegistrationEvent(Set<String> registrations, Side side) {
        refreshFromRegistrationEvent(registrations, side, "REGISTER");
    }

    public synchronized static void refreshFromRegistrationEvent(Set<String> registrations, Side side, String operation) {
        ArrayList<ResourceLocation> types = new ArrayList<ResourceLocation>();
        collectRegisteredChannels(types, Side.CLIENT);
        collectRegisteredChannels(types, Side.SERVER);
        if (!"UNREGISTER".equalsIgnoreCase(operation)) {
            collectRegistrationEventChannels(types, registrations);
        }
        rebuild(types);
    }

    private static void rebuild(List<ResourceLocation> types) {
        initialized = false;
        ArrayList<String> namespaces = new ArrayList<String>();
        ArrayList<ArrayList<String>> paths = new ArrayList<ArrayList<String>>();
        Object2IntMap<String> namespaceMap = new Object2IntOpenHashMap<String>();
        HashMap<Integer, Object2IntMap<String>> pathMaps = new HashMap<Integer, Object2IntMap<String>>();

        AtomicInteger namespaceIndex = new AtomicInteger();
        indexVanillaPackets(namespaceIndex, namespaces, paths, namespaceMap, pathMaps);
        indexCustomPayloads(types, namespaceIndex, namespaces, paths, namespaceMap, pathMaps);

        STATE = new IndexState(namespaces, paths, namespaceMap, pathMaps);

        initTrace();
        if (STATE.namespaces.size() > 4096 || STATE.paths.stream().anyMatch(l -> l.size() > 4096)) {
            throw new RuntimeException("There are too many namespaces and/or paths (Max 4096 namespaces, 4096 paths for each namespace). NEB is not designed to work with so many mods.");
        }
        initialized = true;
    }

    public synchronized static void initFromRegisteredChannels() {
        if (initialized) {
            return;
        }
        refreshFromRegisteredChannels();
    }

    private static void indexVanillaPackets(AtomicInteger namespaceIndex,
                                            ArrayList<String> namespaces,
                                            ArrayList<ArrayList<String>> paths,
                                            Object2IntMap<String> namespaceMap,
                                            HashMap<Integer, Object2IntMap<String>> pathMaps) {
        for (String path : VANILLA_PATHS) {
            fillSingle(namespaceIndex, new ResourceLocation("minecraft", path), namespaces, paths, namespaceMap, pathMaps);
        }
    }

    private static void indexCustomPayloads(List<ResourceLocation> types,
                                            AtomicInteger namespaceIndex,
                                            ArrayList<String> namespaces,
                                            ArrayList<ArrayList<String>> paths,
                                            Object2IntMap<String> namespaceMap,
                                            HashMap<Integer, Object2IntMap<String>> pathMaps) {
        Set<ResourceLocation> unique = new HashSet<>(types);
        ArrayList<ResourceLocation> sorted = new ArrayList<ResourceLocation>(unique);
        sorted.sort(Comparator.comparing(ResourceLocation::getResourceDomain).thenComparing(ResourceLocation::getResourcePath));
        for (ResourceLocation type : sorted) {
            fillSingle(namespaceIndex, type, namespaces, paths, namespaceMap, pathMaps);
        }
    }

    private static List<ResourceLocation> collectNegotiatedChannelNames(java.util.Map<ResourceLocation, String> remoteChannels) {
        ArrayList<ResourceLocation> result = new ArrayList<ResourceLocation>();
        if (remoteChannels == null || remoteChannels.isEmpty()) {
            return result;
        }
        try {
            for (ResourceLocation rl : remoteChannels.keySet()) {
                if (!"fml".equals(rl.getResourceDomain())) {
                    result.add(rl);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to collect negotiated Forge channel names for NamespaceIndexManager", e);
        }
        return result;
    }

    private static void collectRegisteredChannels(List<ResourceLocation> result, Side side) {
        try {
            for (String channelName : NetworkRegistry.INSTANCE.channelNamesFor(side)) {
                addChannelName(result, channelName);
            }
        } catch (Exception e) {
            LOGGER.debug("Failed to collect registered Forge channel names for NamespaceIndexManager", e);
        }
    }

    private static void collectRegistrationEventChannels(List<ResourceLocation> result, Set<String> registrations) {
        if (registrations == null || registrations.isEmpty()) {
            return;
        }
        for (String registration : registrations) {
            addChannelName(result, registration);
        }
    }

    private static void addChannelName(List<ResourceLocation> result, String channelName) {
        if (channelName == null) {
            return;
        }
        if (channelName.startsWith("FML") || channelName.startsWith("MC|") || channelName.startsWith("\u0001")) {
            return;
        }
        if (channelName.indexOf(':') >= 0) {
            result.add(new ResourceLocation(channelName));
        } else {
            result.add(new ResourceLocation("legacy", channelName.toLowerCase()));
        }
    }

    private static void initTrace() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("PacketTypeIndexManager initialized.");
            STATE.namespaceMap.forEach((namespace, id) -> {
                LOGGER.debug("namespace: {} id: {}", namespace, id);
                STATE.pathMaps.get(id).forEach((path, id1) -> LOGGER.debug("- path: {} id: {}", path, id1));
            });
        }
    }

    private static void fillSingle(AtomicInteger namespaceIndex,
                                   ResourceLocation packetId,
                                   ArrayList<String> namespaces,
                                   ArrayList<ArrayList<String>> paths,
                                   Object2IntMap<String> namespaceMap,
                                   HashMap<Integer, Object2IntMap<String>> pathMaps) {
        if (!namespaceMap.containsKey(packetId.getResourceDomain())) {
            namespaceMap.put(packetId.getResourceDomain(), namespaceIndex.get());
            namespaces.add(packetId.getResourceDomain());
            paths.add(new ArrayList<String>());
            namespaceIndex.getAndIncrement();
        }
        int namespaceId = namespaceMap.getInt(packetId.getResourceDomain());
        pathMaps.compute(namespaceId, (namespaceId1, pathMap) -> {
            if (pathMap == null) {
                pathMap = new Object2IntOpenHashMap<String>();
            }
            if (!pathMap.containsKey(packetId.getResourcePath())) {
                pathMap.put(packetId.getResourcePath(), pathMap.size());
                paths.get(namespaceId).add(packetId.getResourcePath());
            }
            return pathMap;
        });
    }

    private static boolean contains(ResourceLocation type) {
        if (!initialized) {
            return false;
        }
        if (type == null) {
            return false;
        }
        IndexState state = STATE;
        if (!state.namespaceMap.containsKey(type.getResourceDomain())) {
            return false;
        }
        Object2IntMap<String> pathMap = state.pathMaps.get(Integer.valueOf(state.namespaceMap.getInt(type.getResourceDomain())));
        return pathMap != null && pathMap.containsKey(type.getResourcePath());
    }

    public static boolean canAggregate(ResourceLocation type) {
        return type != null && contains(type);
    }

    public static int getNebIndex(ResourceLocation type) {
        if (initialized && contains(type)) {
            IndexState state = STATE;
            int namespaceIndex = state.namespaceMap.getInt(type.getResourceDomain());
            int pathIndex = state.pathMaps.get(namespaceIndex).getInt(type.getResourcePath());
            if (namespaceIndex < 256 && pathIndex < 256) {
                return 0xc0000000 | (namespaceIndex << 16) | (pathIndex << 8);
            } else {
                return 0x80000000 | (namespaceIndex << 12) | (pathIndex);
            }
        }
        return 0;
    }

    public static int getNebIndexNotTight(ResourceLocation type) {
        if (initialized && contains(type)) {
            IndexState state = STATE;
            int namespaceIndex = state.namespaceMap.getInt(type.getResourceDomain());
            int pathIndex = state.pathMaps.get(namespaceIndex).getInt(type.getResourcePath());
            return 0x80000000 | (namespaceIndex << 12) | (pathIndex);
        }
        return 0;
    }

    public static ResourceLocation getIdentifier(int nebIndex, boolean tight) {
        if (!initialized) {
            return null;
        }
        IndexState state = STATE;
        int namespaceIndex, pathIndex;
        if (tight) {
            namespaceIndex = (nebIndex & 0b11111111_00000000) >>> 8;
            pathIndex = (nebIndex & 0b00000000_11111111);
        } else {
            namespaceIndex = (nebIndex & 0b11111111_11110000_00000000) >>> 12;
            pathIndex = (nebIndex & 0b00000000_00001111_11111111);
        }
        return new ResourceLocation(state.namespaces.get(namespaceIndex), state.paths.get(namespaceIndex).get(pathIndex));
    }

    private static final class IndexState {
        private final ArrayList<String> namespaces;
        private final ArrayList<ArrayList<String>> paths;
        private final Object2IntMap<String> namespaceMap;
        private final HashMap<Integer, Object2IntMap<String>> pathMaps;

        private IndexState(ArrayList<String> namespaces,
                           ArrayList<ArrayList<String>> paths,
                           Object2IntMap<String> namespaceMap,
                           HashMap<Integer, Object2IntMap<String>> pathMaps) {
            this.namespaces = namespaces;
            this.paths = paths;
            this.namespaceMap = namespaceMap;
            this.pathMaps = pathMaps;
        }
    }
}
