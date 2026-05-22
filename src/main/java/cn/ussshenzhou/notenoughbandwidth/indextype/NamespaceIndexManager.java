package cn.ussshenzhou.notenoughbandwidth.indextype;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NetworkPayloadSetup;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

public final class NamespaceIndexManager {
    private static final List<String> VANILLA_TYPES = buildVanillaTypes();
    private static final WeakHashMap<NetworkManager, ConnectionIndexTable> CONNECTION_TABLE_CACHE = new WeakHashMap<NetworkManager, ConnectionIndexTable>();

    private NamespaceIndexManager() {
    }

    public static synchronized void initForConnection(@Nullable NetworkManager connection, @Nullable NetworkPayloadSetup setup) {
        if (connection == null || connection.channel() == null) {
            return;
        }
        List<String> customTypes = collectIndexedCustomTypes(setup);
        List<String> merged = new ArrayList<String>(VANILLA_TYPES.size() + customTypes.size());
        merged.addAll(VANILLA_TYPES);
        merged.addAll(customTypes);
        ConnectionIndexTable table = ConnectionIndexTable.create(merged);
        CONNECTION_TABLE_CACHE.put(connection, table);
        ChannelAttributes.setConnectionIndexTable(connection, table);
    }

    public static synchronized void clearConnection(@Nullable NetworkManager connection) {
        if (connection == null) {
            return;
        }
        CONNECTION_TABLE_CACHE.remove(connection);
        ChannelAttributes.setConnectionIndexTable(connection, null);
    }

    @Nullable
    public static ConnectionIndexTable getConnectionTable(@Nullable NetworkManager connection) {
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

    public static boolean ready(@Nullable NetworkManager connection) {
        return getConnectionTable(connection) != null;
    }

    public static boolean contains(@Nullable NetworkManager connection, String type) {
        ConnectionIndexTable table = getConnectionTable(connection);
        return table != null && table.contains(type);
    }

    public static ConnectionIndexTable.IndexPair getCheckedIndex(@Nullable NetworkManager connection, String type) {
        ConnectionIndexTable table = getConnectionTable(connection);
        if (table == null) {
            throw new IndexOutOfBoundsException("Missing NEB connection index table for " + type);
        }
        return table.getCheckedIndex(type);
    }

    @Nullable
    public static String getIdentifierOrNull(@Nullable NetworkManager connection, int namespaceIndex, int pathIndex) {
        ConnectionIndexTable table = getConnectionTable(connection);
        if (table == null) {
            return null;
        }
        return table.getIdentifierOrNull(namespaceIndex, pathIndex);
    }

    private static List<String> collectIndexedCustomTypes(@Nullable NetworkPayloadSetup setup) {
        if (setup == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<String>();
        for (Map.Entry<net.minecraft.network.EnumConnectionState, Map<String, cn.ussshenzhou.notenoughbandwidth.network.payload.NetworkChannel>> protocolEntry : setup.channels().entrySet()) {
            for (Map.Entry<String, cn.ussshenzhou.notenoughbandwidth.network.payload.NetworkChannel> entry : protocolEntry.getValue().entrySet()) {
                String id = entry.getKey();
                if (id == null || PacketAggregationPacket.CHANNEL_NAME.equals(id)) {
                    continue;
                }
                if ((ModConstants.MOD_ID + ":transport_setup").equals(id)) {
                    continue;
                }
                cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistration<?> registration = PayloadRegistry.getRegistration(id);
                if (registration == null || registration.optional()) {
                    continue;
                }
                result.add(id);
            }
        }
        Collections.sort(result, Comparator.naturalOrder());
        return result;
    }

    private static List<String> buildVanillaTypes() {
        List<String> types = new ArrayList<String>();
        types.add("minecraft:spawn_object");
        types.add("minecraft:spawn_experience_orb");
        types.add("minecraft:spawn_global_entity");
        types.add("minecraft:spawn_mob");
        types.add("minecraft:scoreboard_objective");
        types.add("minecraft:spawn_painting");
        types.add("minecraft:spawn_player");
        types.add("minecraft:entity_animation");
        types.add("minecraft:statistics");
        types.add("minecraft:block_break_anim");
        types.add("minecraft:update_block_entity");
        types.add("minecraft:block_action");
        types.add("minecraft:block_change");
        types.add("minecraft:boss_bar");
        types.add("minecraft:server_difficulty");
        types.add("minecraft:tab_complete");
        types.add("minecraft:chat");
        types.add("minecraft:multi_block_change");
        types.add("minecraft:confirm_transaction");
        types.add("minecraft:close_window");
        types.add("minecraft:open_window");
        types.add("minecraft:window_items");
        types.add("minecraft:window_property");
        types.add("minecraft:set_slot");
        types.add("minecraft:set_cooldown");
        types.add("minecraft:custom_payload");
        types.add("minecraft:named_sound_effect");
        types.add("minecraft:kick_disconnect");
        types.add("minecraft:entity_status");
        types.add("minecraft:explosion");
        types.add("minecraft:unload_chunk");
        types.add("minecraft:change_game_state");
        types.add("minecraft:keep_alive");
        types.add("minecraft:chunk_data");
        types.add("minecraft:effect");
        types.add("minecraft:particle");
        types.add("minecraft:join_game");
        types.add("minecraft:map");
        types.add("minecraft:entity_move");
        types.add("minecraft:entity_look_and_move");
        types.add("minecraft:entity_look");
        types.add("minecraft:vehicle_move");
        types.add("minecraft:open_book");
        types.add("minecraft:open_sign_editor");
        types.add("minecraft:craft_recipe_response");
        types.add("minecraft:abilities");
        types.add("minecraft:combat_event");
        types.add("minecraft:player_info");
        types.add("minecraft:face_player");
        types.add("minecraft:position");
        types.add("minecraft:unlock_recipes");
        types.add("minecraft:destroy_entities");
        types.add("minecraft:remove_entity_effect");
        types.add("minecraft:resource_pack_send");
        types.add("minecraft:respawn");
        types.add("minecraft:entity_head_look");
        types.add("minecraft:select_advancement_tab");
        types.add("minecraft:world_border");
        types.add("minecraft:camera");
        types.add("minecraft:held_item_change");
        types.add("minecraft:display_objective");
        types.add("minecraft:entity_metadata");
        types.add("minecraft:attach_entity");
        types.add("minecraft:entity_velocity");
        types.add("minecraft:entity_equipment");
        types.add("minecraft:set_experience");
        types.add("minecraft:update_health");
        types.add("minecraft:scoreboard_score");
        types.add("minecraft:spawn_position");
        types.add("minecraft:teams");
        types.add("minecraft:update_time");
        types.add("minecraft:title");
        types.add("minecraft:sound_effect");
        types.add("minecraft:player_list_header_footer");
        types.add("minecraft:collect_item");
        types.add("minecraft:entity_teleport");
        types.add("minecraft:advancements");
        types.add("minecraft:entity_properties");
        types.add("minecraft:entity_effect");
        types.add("minecraft:recipes");
        types.add("minecraft:tags");
        types.add("minecraft:packet_placement");
        types.add("minecraft:packet_input");
        types.add("minecraft:use_item");
        types.add("minecraft:vehicle_move");
        types.add("minecraft:steer_boat");
        types.add("minecraft:craft_recipe_request");
        types.add("minecraft:client_status");
        types.add("minecraft:player_block_placement");
        types.add("minecraft:held_item_change");
        types.add("minecraft:animation");
        types.add("minecraft:entity_action");
        types.add("minecraft:use_entity");
        types.add("minecraft:close_window");
        types.add("minecraft:click_window");
        types.add("minecraft:confirm_transaction");
        types.add("minecraft:creative_inventory_action");
        types.add("minecraft:enchant_item");
        types.add("minecraft:update_sign");
        types.add("minecraft:keep_alive");
        types.add("minecraft:abilities");
        types.add("minecraft:tab_complete");
        types.add("minecraft:client_settings");
        types.add("minecraft:custom_payload");
        types.add("minecraft:spectate");
        types.add("minecraft:resource_pack_status");
        types.add("minecraft:advancement_tab");
        return Collections.unmodifiableList(types);
    }
}
