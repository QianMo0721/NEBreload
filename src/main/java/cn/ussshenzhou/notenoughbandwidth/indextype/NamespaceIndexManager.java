package cn.ussshenzhou.notenoughbandwidth.indextype;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author USS_Shenzhou
 */
public class NamespaceIndexManager {
    private static volatile boolean initialized = false;
    private static final ArrayList<String> NAMESPACES = new ArrayList<>();
    private static final ArrayList<ArrayList<String>> PATHS = new ArrayList<>();
    private static final Object2IntMap<String> NAMESPACE_MAP = new Object2IntOpenHashMap<>();
    private static final HashMap<Integer, Object2IntMap<String>> PATH_MAPS = new HashMap<>();

    /**
     * @see net.minecraft.network.protocol.game.GamePacketTypes
     */
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
        add("custom_payload");
        add("damage_event");
        add("debug_sample");
        add("delete_chat");
        add("disconnect");
        add("disguised_chat");
        add("entity_event");
        add("explode");
        add("forget_level_chunk");
        add("game_event");
        add("game_profile");
        add("horse_screen_open");
        add("hurt_animation");
        add("initialize_border");
        add("level_chunk_with_light");
        add("level_event");
        add("level_particles");
        add("light_update");
        add("login");
        add("map_item_data");
        add("merchant_offers");
        add("move_entity_pos");
        add("move_entity_pos_rot");
        add("move_entity_rot");
        add("move_vehicle");
        add("open_book");
        add("open_screen");
        add("open_sign_editor");
        add("ping");
        add("place_ghost_recipe");
        add("player_abilities");
        add("player_chat");
        add("player_combat_end");
        add("player_combat_kill");
        add("player_combat_start");
        add("player_info_remove");
        add("player_info_update");
        add("player_look_at");
        add("player_position");
        add("recipe");
        add("remove_entities");
        add("remove_mob_effect");
        add("reset_score");
        add("resource_pack_pop");
        add("resource_pack_push");
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
        add("set_cursor_item");
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
        add("set_player_inventory");
        add("set_player_team");
        add("set_score");
        add("set_simulation_distance");
        add("set_subtitle_text");
        add("set_time");
        add("set_title_text");
        add("set_titles_animation");
        add("sound");
        add("sound_entity");
        add("start_configuration");
        add("stop_sound");
        add("store_cookie");
        add("system_chat");
        add("tab_list");
        add("tag_query");
        add("take_item_entity");
        add("teleport_entity");
        add("ticking_state");
        add("ticking_step");
        add("transfer");
        add("update_advancements");
        add("update_attributes");
        add("update_enabled_features");
        add("update_mob_effect");
        add("update_recipes");
        add("update_tags");
    }};

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * Initialize by collecting packet types from the Forge network registry.
     * Gathers all ResourceLocations registered in ModNetworkRegistry.CLASS_TO_ID,
     * plus pre-seeded vanilla paths.
     */
    public synchronized static void initFromRegistry() {
        var types = new java.util.ArrayList<ResourceLocation>(
                cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.CLASS_TO_ID.values());
        init(types);
    }

    public synchronized static void init(List<ResourceLocation> types) {
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER && initialized) {
            return;
        }
        initialized = false;
        NAMESPACES.clear();
        PATHS.clear();
        NAMESPACE_MAP.clear();
        PATH_MAPS.clear();

        AtomicInteger namespaceIndex = new AtomicInteger(0);
        indexVanillaPackets(namespaceIndex);
        indexModdedPackets(namespaceIndex, types);

        initTrace();
        if (NAMESPACES.size() > 4096 || PATHS.stream().anyMatch(l -> l.size() > 4096)) {
            LogUtils.getLogger().warn("[NEB] Too many packet types, disabling NEB index optimization.");
        }
        initialized = true;
    }

    private static void indexVanillaPackets(AtomicInteger namespaceIndex) {
        VANILLA_PATHS.forEach(path -> fillSingle(namespaceIndex, new ResourceLocation("minecraft", path)));
    }

    private static void indexModdedPackets(AtomicInteger namespaceIndex, List<ResourceLocation> types) {
        types.forEach(type -> fillSingle(namespaceIndex, type));
    }

    private static void initTrace() {
        var logger = LogUtils.getLogger();
        if (logger.isDebugEnabled()) {
            logger.debug("PacketTypeIndexManager initialized.");
            NAMESPACES.forEach(ns -> logger.debug("  Namespace[{}]: {}", NAMESPACE_MAP.getInt(ns), ns));
        }
    }

    private static void fillSingle(AtomicInteger namespaceIndex, ResourceLocation type) {
        String namespace = type.getNamespace();
        String path = type.getPath();
        if (!NAMESPACE_MAP.containsKey(namespace)) {
            int idx = namespaceIndex.getAndIncrement();
            NAMESPACE_MAP.put(namespace, idx);
            NAMESPACES.add(namespace);
            PATHS.add(new ArrayList<>());
            PATH_MAPS.put(idx, new Object2IntOpenHashMap<>());
        }
        int nsIdx = NAMESPACE_MAP.getInt(namespace);
        Object2IntMap<String> pathMap = PATH_MAPS.get(nsIdx);
        if (!pathMap.containsKey(path)) {
            int pathIdx = PATHS.get(nsIdx).size();
            pathMap.put(path, pathIdx);
            PATHS.get(nsIdx).add(path);
        }
    }

    public static int getNamespaceIndex(String namespace) {
        return NAMESPACE_MAP.getOrDefault(namespace, -1);
    }

    public static int getPathIndex(int namespaceIndex, String path) {
        Object2IntMap<String> pathMap = PATH_MAPS.get(namespaceIndex);
        if (pathMap == null) return -1;
        return pathMap.getOrDefault(path, -1);
    }

    public static ResourceLocation getIdentifier(int combined, boolean shortForm) {
        int nsIdx, pathIdx;
        if (shortForm) {
            nsIdx = (combined >>> 12) & 0xF;
            pathIdx = combined & 0xFFF;
        } else {
            nsIdx = (combined >>> 12) & 0xFFF;
            pathIdx = combined & 0xFFF;
        }
        if (nsIdx >= NAMESPACES.size()) return null;
        ArrayList<String> paths = PATHS.get(nsIdx);
        if (pathIdx >= paths.size()) return null;
        return new ResourceLocation(NAMESPACES.get(nsIdx), paths.get(pathIdx));
    }
}
