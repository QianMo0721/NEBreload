package cn.ussshenzhou.notenoughbandwidth.indextype;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Fabric 侧的 payload 标识压缩索引表。
 */
public final class NamespaceIndexManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static volatile boolean initialized = false;
    private static final ArrayList<String> NAMESPACES = new ArrayList<>();
    private static final ArrayList<ArrayList<String>> PATHS = new ArrayList<>();
    private static final Object2IntMap<String> NAMESPACE_MAP = new Object2IntOpenHashMap<>();
    private static final HashMap<Integer, Object2IntMap<String>> PATH_MAPS = new HashMap<>();

    /**
     * 对齐原 NeoForge 版的大型 vanilla PLAY packet/path 预索引表。
     */
    private static final List<String> VANILLA_PATHS = List.of(
            "bundle",
            "bundle_delimiter",
            "add_entity",
            "animate",
            "award_stats",
            "block_changed_ack",
            "block_destruction",
            "block_entity_data",
            "block_event",
            "block_update",
            "boss_event",
            "change_difficulty",
            "chunk_batch_finished",
            "chunk_batch_start",
            "chunks_biomes",
            "clear_titles",
            "command_suggestions",
            "commands",
            "container_close",
            "container_set_content",
            "container_set_data",
            "container_set_slot",
            "cooldown",
            "custom_chat_completions",
            "damage_event",
            "debug/block_value",
            "debug/chunk_value",
            "debug/entity_value",
            "debug/event",
            "debug_sample",
            "delete_chat",
            "disguised_chat",
            "entity_event",
            "entity_position_sync",
            "explode",
            "forget_level_chunk",
            "game_event",
            "game_test_highlight_pos",
            "mount_screen_open",
            "hurt_animation",
            "initialize_border",
            "level_chunk_with_light",
            "level_event",
            "level_particles",
            "light_update",
            "login",
            "low_disk_space_warning",
            "map_item_data",
            "merchant_offers",
            "move_entity_pos",
            "move_entity_pos_rot",
            "move_minecart_along_track",
            "move_entity_rot",
            "move_vehicle",
            "open_book",
            "open_screen",
            "open_sign_editor",
            "place_ghost_recipe",
            "player_abilities",
            "player_chat",
            "player_combat_end",
            "player_combat_enter",
            "player_combat_kill",
            "player_info_remove",
            "player_info_update",
            "player_look_at",
            "player_position",
            "player_rotation",
            "recipe_book_add",
            "recipe_book_remove",
            "recipe_book_settings",
            "remove_entities",
            "remove_mob_effect",
            "respawn",
            "rotate_head",
            "section_blocks_update",
            "select_advancements_tab",
            "server_data",
            "set_action_bar_text",
            "set_border_center",
            "set_border_lerp_size",
            "set_border_size",
            "set_border_warning_delay",
            "set_border_warning_distance",
            "set_camera",
            "set_chunk_cache_center",
            "set_chunk_cache_radius",
            "set_default_spawn_position",
            "set_display_objective",
            "set_entity_data",
            "set_entity_link",
            "set_entity_motion",
            "set_equipment",
            "set_experience",
            "set_health",
            "set_held_slot",
            "set_objective",
            "set_passengers",
            "set_player_team",
            "set_score",
            "set_simulation_distance",
            "set_subtitle_text",
            "set_time",
            "set_title_text",
            "set_titles_animation",
            "sound_entity",
            "sound",
            "start_configuration",
            "stop_sound",
            "system_chat",
            "tab_list",
            "tag_query",
            "take_item_entity",
            "teleport_entity",
            "test_instance_block_status",
            "update_advancements",
            "update_attributes",
            "update_mob_effect",
            "update_recipes",
            "projectile_power",
            "waypoint",
            "accept_teleportation",
            "block_entity_tag_query",
            "bundle_item_selected",
            "change_game_mode",
            "chat_ack",
            "chat_command",
            "chat_command_signed",
            "chat",
            "chat_session_update",
            "chunk_batch_received",
            "client_command",
            "client_tick_end",
            "command_suggestion",
            "configuration_acknowledged",
            "container_button_click",
            "container_click",
            "container_slot_state_changed",
            "debug_subscription_request",
            "edit_book",
            "entity_tag_query",
            "interact",
            "jigsaw_generate",
            "lock_difficulty",
            "move_player_pos",
            "move_player_pos_rot",
            "move_player_rot",
            "move_player_status_only",
            "paddle_boat",
            "pick_item_from_block",
            "pick_item_from_entity",
            "place_recipe",
            "player_action",
            "player_command",
            "player_input",
            "player_loaded",
            "recipe_book_change_settings",
            "recipe_book_seen_recipe",
            "rename_item",
            "seen_advancements",
            "select_trade",
            "set_beacon",
            "set_carried_item",
            "set_command_block",
            "set_command_minecart",
            "set_creative_mode_slot",
            "set_jigsaw_block",
            "set_structure_block",
            "set_test_block",
            "test_instance_block_action",
            "sign_update",
            "swing",
            "teleport_to_entity",
            "use_item_on",
            "use_item",
            "reset_score",
            "ticking_state",
            "ticking_step",
            "set_cursor_item",
            "set_player_inventory"
    );

    private NamespaceIndexManager() {
    }

    public synchronized static void init(List<Identifier> types) {
        initialized = false;
        NAMESPACES.clear();
        PATHS.clear();
        NAMESPACE_MAP.clear();
        PATH_MAPS.clear();

        AtomicInteger namespaceIndex = new AtomicInteger();
        indexVanillaPackets(namespaceIndex);
        indexCustomPayloads(types, namespaceIndex);

        initTrace();
        if (NAMESPACES.size() > 4096 || PATHS.stream().anyMatch(l -> l.size() > 4096)) {
            throw new RuntimeException("There are too many namespaces and/or paths (Max 4096 namespaces, 4096 paths for each namespace). NEB is not designed to work with so many mods.");
        }
        initialized = true;
    }

    private static void indexVanillaPackets(AtomicInteger namespaceIndex) {
        VANILLA_PATHS.forEach(path -> fillSingle(namespaceIndex, new Identifier("minecraft", path)));
    }

    private static void indexCustomPayloads(List<Identifier> types, AtomicInteger namespaceIndex) {
        List<Identifier> sorted = new ArrayList<>(types);
        sorted.sort(Comparator.comparing(Identifier::getNamespace).thenComparing(Identifier::getPath));
        Set<Identifier> registered = new HashSet<>(types);
        for (Identifier type : sorted) {
            if (type == null || !registered.contains(type)) {
                continue;
            }
            fillSingle(namespaceIndex, type);
        }
    }

    private static void initTrace() {
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("PacketTypeIndexManager initialized.");
            NAMESPACE_MAP.forEach((namespace, id) -> {
                LOGGER.debug("namespace: {} id: {}", namespace, id);
                Object2IntMap<String> pathMap = PATH_MAPS.get(id);
                if (pathMap != null) {
                    pathMap.forEach((path, pathId) -> LOGGER.debug("- path: {} id: {}", path, pathId));
                }
            });
        }
    }

    private static void fillSingle(AtomicInteger namespaceIndex, Identifier packetId) {
        int namespaceId;
        if (!NAMESPACE_MAP.containsKey(packetId.getNamespace())) {
            namespaceId = namespaceIndex.getAndIncrement();
            NAMESPACE_MAP.put(packetId.getNamespace(), namespaceId);
            NAMESPACES.add(packetId.getNamespace());
            PATHS.add(new ArrayList<>());
            PATH_MAPS.put(namespaceId, new Object2IntOpenHashMap<>());
        } else {
            namespaceId = NAMESPACE_MAP.getInt(packetId.getNamespace());
        }

        Object2IntMap<String> pathMap = PATH_MAPS.get(namespaceId);
        if (!pathMap.containsKey(packetId.getPath())) {
            pathMap.put(packetId.getPath(), pathMap.size());
            PATHS.get(namespaceId).add(packetId.getPath());
        }
    }

    private static boolean contains(Identifier type) {
        if (!initialized) {
            return false;
        }
        return NAMESPACE_MAP.containsKey(type.getNamespace())
                && PATH_MAPS.containsKey(NAMESPACE_MAP.getInt(type.getNamespace()))
                && PATH_MAPS.get(NAMESPACE_MAP.getInt(type.getNamespace())).containsKey(type.getPath());
    }

    public static int getNebIndex(Identifier type) {
        if (initialized && contains(type)) {
            int namespaceIndex = NAMESPACE_MAP.getInt(type.getNamespace());
            int pathIndex = PATH_MAPS.get(namespaceIndex).getInt(type.getPath());
            if (namespaceIndex < 256 && pathIndex < 256) {
                return 0xc0000000 | (namespaceIndex << 16) | (pathIndex << 8);
            }
            return 0x80000000 | (namespaceIndex << 12) | pathIndex;
        }
        return 0;
    }

    public static int getNebIndexNotTight(Identifier type) {
        if (initialized && contains(type)) {
            int namespaceIndex = NAMESPACE_MAP.getInt(type.getNamespace());
            int pathIndex = PATH_MAPS.get(namespaceIndex).getInt(type.getPath());
            return 0x80000000 | (namespaceIndex << 12) | pathIndex;
        }
        return 0;
    }

    public static Identifier getIdentifier(int nebIndex, boolean tight) {
        if (!initialized) {
            return null;
        }
        int namespaceIndex;
        int pathIndex;
        if (tight) {
            namespaceIndex = (nebIndex & 0b11111111_00000000) >>> 8;
            pathIndex = nebIndex & 0b00000000_11111111;
        } else {
            namespaceIndex = (nebIndex & 0b11111111_11110000_00000000) >>> 12;
            pathIndex = nebIndex & 0b00000000_00001111_11111111;
        }
        return new Identifier(NAMESPACES.get(namespaceIndex), PATHS.get(namespaceIndex).get(pathIndex));
    }
}
