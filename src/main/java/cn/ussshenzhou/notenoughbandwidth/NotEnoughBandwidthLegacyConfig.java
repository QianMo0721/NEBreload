package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.config.TConfig;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import com.mojang.logging.LogUtils;
import net.minecraft.util.Mth;

import java.util.HashSet;
import java.util.regex.Pattern;

/**
 * @author USS_Shenzhou
 */
public class NotEnoughBandwidthLegacyConfig implements TConfig {

    @SerializedName("说明-文件")
    public String commentFile = "NEB 配置文件。为兼容旧版本，程序读取时同时支持旧英文键名；重新保存后会输出为中文键名。";

    @SerializedName("说明-兼容模式")
    public String commentCompatibleMode = "兼容模式开启后，会额外跳过黑名单中的数据包聚合。建议在经过代理、安装大量联机模组，或出现时序敏感问题（例如命令建议、玩家列表、聊天同步异常）时开启。";

    @SerializedName(value = "兼容模式", alternate = {"compatibleMode"})
    public boolean compatibleMode = false;

    @SerializedName("说明-兼容模式黑名单")
    public String commentBlackList = "仅在兼容模式开启时生效。列表内容为需要强制跳过聚合的数据包类型标识。默认会包含一批已知的时序敏感包；这些包会先触发 flush 再直通，以尽量避免 mod/代理兼容问题。";

    @SerializedName(value = "兼容模式黑名单", alternate = {"blackList"})
    public HashSet<String> blackList = new HashSet<>() {{
        // 这里只保留“仅在兼容模式下额外直通”的包；无条件必须直通的包统一收口到
        // COMMON_BLOCK_LIST，如果这些出问题就加入黑名单
        add("minecraft:command_suggestion");
        add("minecraft:command_suggestions");
        add("minecraft:commands");
        add("minecraft:chat");
        add("minecraft:chat_command");
        add("minecraft:chat_command_signed");
        add("minecraft:chat_session_update");
        add("minecraft:player_info_update");
        add("minecraft:player_info_remove");
    }};

    @SerializedName("说明-保持网络线程分发的自定义包")
    public String commentKeepCustomPayloadOnNetworkThread = "列表中的 game custom payload 会保持在网络线程直接分发，不会先切回主线程。可填写完整标识（如 voicechat:request_secret）或命名空间（如 voicechat）。适用于依赖原始网络线程上下文的联机模组。";

    @SerializedName(value = "保持网络线程分发的自定义包", alternate = {"keepCustomPayloadOnNetworkThread"})
    public HashSet<String> keepCustomPayloadOnNetworkThread = new HashSet<>() {{
        add("voicechat");
    }};

    @SerializedName("说明-调试日志")
    public String commentDebugLog = "是否输出更详细的调试日志。仅在排查问题时建议开启，平时开启会明显刷屏。";

    @SerializedName(value = "调试日志", alternate = {"debugLog"})
    public boolean debugLog = false;

    @SerializedName("说明-上下文等级")
    public String commentContextLevel = "数据包上下文压缩等级，程序内部会自动限制在 21 到 25 之间。数值越高，可能压缩更激进，但兼容性风险也更高。";

    @SerializedName(value = "上下文等级", alternate = {"contextLevel"})
    public int contextLevel = 23;

    @SerializedName("说明-zstd压缩等级")
    public String commentZstdCompressionLevel = "zstd 算法压缩等级。数值越高通常压缩率越高、CPU 开销也越大。程序内部会自动限制在 -5 到 22 之间。";

    @SerializedName(value = "zstd压缩等级", alternate = {"zstdCompressionLevel", "compressionLevel"})
    public int zstdCompressionLevel = 3;

    @SerializedName("说明-不复用Zstd上下文的玩家")
    public String commentPlayersDoNotUseContext = "仅在服务端生效。指定一组玩家 UUID，这些玩家的连接不会复用 Zstd 上下文，适合 Replay 等依赖网络包重放的模组。";

    @SerializedName(value = "不复用Zstd上下文的玩家", alternate = {"playersDoNotUseContext"})
    public HashSet<String> playersDoNotUseContext = new HashSet<>() {{
        add("00000000-0000-0000-0000-000000000000");
    }};

    /**
     * 延迟区块缓存开关。默认开启，以保持与原项目一致的“扩展视距 +
     * 区块缓存”能力；如需排查兼容性，可手动关闭。
     */

    @SerializedName("说明-延迟区块缓存")
    public String commentEnableDelayedChunkCaching = "延迟区块缓存总开关。开启后会启用扩展视距与区块缓存联动逻辑；关闭后将整体退回原版行为。";

    @SerializedName(value = "启用延迟区块缓存", alternate = {"enableDelayedChunkCaching"})
    public boolean enableDelayedChunkCaching = true;

    @SerializedName("说明-区块缓存上限")
    public String commentDccSizeLimit = "单个玩家最多允许保留在延迟区块缓存中的区块数量。超过后会优先淘汰最早进入缓存的区块。";

    @SerializedName(value = "区块缓存上限", alternate = {"dccSizeLimit"})
    public int dccSizeLimit = 200;

    @SerializedName("说明-区块缓存距离")
    public String commentDccDistance = "玩家离开主视野后，仍允许继续缓存的棋盘距离（按区块计）。数值越大，缓存保留范围越大。";

    @SerializedName(value = "区块缓存距离", alternate = {"dccDistance"})
    public int dccDistance = 15;

    @SerializedName("说明-区块缓存超时秒数")
    public String commentDccTimeout = "区块离开主视野后，最多还能在缓存中保留的秒数。超时后会被正式卸载。";

    @SerializedName(value = "区块缓存超时秒数", alternate = {"dccTimeout"})
    public int dccTimeout = 60;

    @SerializedName("说明-最大单包大小")
    public String commentMaxPacketSize = "单个聚合包允许的最大大小。支持 B/KB/MB 写法，程序内部会限制在 2MB 到 64MB 之间。";

    @SerializedName(value = "最大单包大小", alternate = {"maxPacketSize"})
    public String maxPacketSize = "4MB";

    @Expose(serialize = false, deserialize = false)
    private int maxPacketSizeByte = -1;

    @SerializedName("说明-区块缓存Raw估算系数")
    public String commentChunkCacheRawSizeMultiplier = "区块缓存命中时，补记到 Raw 统计中的区块主体大小估算系数。1.0 表示按主体 buffer 原样计入，可按需要手动调整。";

    @SerializedName(value = "区块缓存Raw估算系数", alternate = {"chunkCacheRawSizeMultiplier"})
    public double chunkCacheRawSizeMultiplier = 1.0D;

    @Expose(serialize = false, deserialize = false)
    public static final HashSet<String> COMMON_BLOCK_LIST = new HashSet<>() {{
        add("minecraft:finish_configuration");
        add(PacketAggregationPacket.TYPE.toString());
        // NEB debug/stat query channel must stay out of aggregation, otherwise
        // client requests and server responses are delayed behind the flush
        // thread and the stat screen cannot reflect live server-side traffic.
        add(ModConstants.MOD_ID + ":stat_query");
        add(ModConstants.MOD_ID + ":stat_resp");
        add("minecraft:login");
        // NEB transport channel itself must never be re-aggregated,
        // otherwise PacketAggregationPacket is wrapped into nebl:main and
        // intercepted again by ConnectionMixin, causing packets to loop in
        // the aggregation buffer and never actually reach the remote side.
        add(ModConstants.MOD_ID + ":main");
        // Connection-control and synchronization-sensitive vanilla packets – skip aggregation
        add("minecraft:disconnect");
        add("minecraft:keep_alive");
        add("minecraft:ping");
        add("minecraft:pong");
        add("minecraft:register");
        add("minecraft:unregister");
        add("minecraft:resource_pack");
        add("minecraft:client_information");
        add("minecraft:update_enabled_features");
        // Forge 1.20.1 port: serverbound movement / interaction packets are far
        // more timing-sensitive than in the old branch. Keeping them behind the
        // optional compatibleMode blacklist still allows aggregation to delay
        // authoritative movement and menu actions, causing rubber-banding and
        // interaction stalls. Treat them as unconditional passthrough here.
        add("minecraft:move_player_pos");
        add("minecraft:move_player_pos_rot");
        add("minecraft:move_player_rot");
        add("minecraft:move_player_status_only");
        add("minecraft:accept_teleportation");
        add("minecraft:client_command");
        add("minecraft:player_action");
        add("minecraft:interact");
        add("minecraft:use_item");
        add("minecraft:use_item_on");
        add("minecraft:container_click");
        add("minecraft:container_button_click");
        add("minecraft:container_slot_state_changed");
        add("minecraft:container_set_content");
        add("minecraft:container_set_slot");
        add("minecraft:container_set_data");
        add("minecraft:container_close");
        add("minecraft:open_screen");
        add("minecraft:horse_screen_open");
        add("minecraft:mount_screen_open");
        add("minecraft:merchant_offers");
        add("minecraft:place_ghost_recipe");
        add("minecraft:place_recipe");
        add("minecraft:select_trade");
        add("minecraft:recipe_book_change_settings");
        add("minecraft:recipe_book_seen_recipe");
        add("minecraft:update_recipes");
        add("minecraft:set_carried_item");
        add("minecraft:set_held_slot");
        // Respawn and death-recovery flows are highly timing-sensitive, especially
        // when mods like gravestone/tombstone inject extra state sync right after
        // respawn. Keep these packets on the passthrough path to avoid delaying the
        // client reinitialization sequence behind the aggregation flush window.
        add("minecraft:respawn");
        add("minecraft:game_event");
        add("minecraft:initialize_border");
        add("minecraft:set_time");
        add("minecraft:change_difficulty");
        add("minecraft:set_default_spawn_position");
        add("minecraft:player_position");
        add("minecraft:player_abilities");
        add("minecraft:set_health");
        add("minecraft:set_experience");
        add("minecraft:update_mob_effect");
        add("minecraft:remove_mob_effect");
        // Stonecutter and other menu screens depend on immediate container/menu
        // synchronization. These packets must bypass aggregation even when
        // compatibleMode is off, otherwise the client can observe stale input
        // state and render no available recipes.
        // 自己解释，不能再叠加 NEB 的 transparent custom-payload 压缩，否则会在
        // 对端网络层先于 Minecraft 逻辑解析时读到 NEBZSTD1 魔数。
        add("fml:play");
        // Chunk cache control packets remain timing-sensitive in the current
        // Forge 1.20.1 port, but the bulk chunk payload packets need to stay
        // aggregatable, otherwise compression ratio collapses far below the
        // original mod because most of the bandwidth is no longer eligible.
        add("minecraft:set_chunk_cache_center");
        add("minecraft:set_chunk_cache_radius");
        // 按移植前项目实现，这些移动/同步包默认也应参与聚合；
        // Forge internal channel packets – skip aggregation
        add("forge:tier_sorting");
        add("forge:registry_data");
        add("forge:spawn_type");
        add("forge:register");
        add("forge:unregister");
        // FTB Quests 任务书与队伍同步链路对自定义 payload 的标识和顺序非常敏感，
        // 必须始终直通，不能依赖兼容模式黑名单，否则已有配置文件会让这些排除项失效。
        add("ftbquests:sync_quests");
        add("ftbquests:sync_team_data");
        add("ftbquests:update_task_progress");
        add("ftbquests:claim_reward_response");
        add("ftbquests:sync_editing_mode");
        add("ftbquests:create_other_team_data");
        add("ftbquests:display_completion_toast");
        add("ftbquests:display_reward_toast");
        add("ftbquests:display_item_reward_toast");
        add("ftbquests:toggle_pinned_response");
        add("ftbquests:toggle_chapter_pinned_response");
        add("ftbquests:update_team_data");
        add("ftbquests:object_started");
        add("ftbquests:object_completed");
        add("ftbquests:object_started_reset");
        add("ftbquests:object_completed_reset");
        add("ftbquests:sync_lock");
        add("ftbquests:reset_reward");
        add("ftbquests:team_data_changed");
        add("ftbquests:task_screen_config_req");
        add("ftbquests:create_object_response");
        add("ftbquests:delete_object_response");
        add("ftbquests:edit_object_response");
        add("ftbquests:move_chapter_response");
        add("ftbquests:move_quest_response");
        add("ftbquests:change_chapter_group_response");
        add("ftbquests:move_chapter_group_response");
        add("ftbquests:sync_reward_blocking");
        add("ftbquests:sync_structures_response");
        add("ftbquests:sync_editor_permission");
        add("ftbquests:open_quest_book");
        add("ftbquests:clear_display_cache");
        add("ftbquests:reorder_item_response");
        add("ftbquests:clear_repeat_cooldown");
    }};

    public static NotEnoughBandwidthLegacyConfig get() {
        return ConfigHelper.getConfigRead(NotEnoughBandwidthLegacyConfig.class);
    }

    public static boolean skipType(String type) {
        var cfg = get();
        if (type.startsWith("ftbquests:") || type.startsWith("ftbteams:") || type.startsWith("ftblibrary:")) {
            return true;
        }
        // Simple Voice Chat 在代理链路与重放场景里都对包顺序和原始 payload 头更敏感，
        // 这里统一直通，避免 NEB 聚合/索引头改写破坏其兼容性。
        if (type.startsWith("voicechat:")) {
            return true;
        }
        return COMMON_BLOCK_LIST.contains(type) || (cfg.compatibleMode && cfg.blackList.contains(type));
    }

    public static boolean shouldKeepCustomPayloadOnNetworkThread(String type) {
        if (type == null || type.isEmpty()) {
            return false;
        }
        int split = type.indexOf(':');
        String namespace = split >= 0 ? type.substring(0, split) : type;
        // Simple Voice Chat Forge 1.20.1 客户端网络监听明确要求 custom payload
        // 在非主线程到达，否则会直接丢弃 clientbound 事件，导致 secret 握手收不到。
        // 这里对 voicechat 命名空间做硬保证，不依赖用户配置是否保留默认项。
        if ("voicechat".equals(namespace)) {
            return true;
        }
        var cfg = get();
        if (cfg.keepCustomPayloadOnNetworkThread.contains(type)) {
            return true;
        }
        return cfg.keepCustomPayloadOnNetworkThread.contains(namespace);
    }

    public int getDccSizeLimitSafe() {
        return Math.max(0, dccSizeLimit);
    }

    public int getDccDistanceSafe() {
        return Math.max(0, dccDistance);
    }

    public int getDccTimeoutSafeSeconds() {
        return Math.max(0, dccTimeout);
    }

    public int getMaxPacketSize() {
        if (maxPacketSizeByte == -1) {
            maxPacketSizeByte = parseByteSize(maxPacketSize);
            int min = parseByteSize("2MB");
            int max = parseByteSize("64MB");
            if (maxPacketSizeByte < min || maxPacketSizeByte > max) {
                LogUtils.getLogger().error("maxPacketSize should be between 2MB and 64MB");
            }
            maxPacketSizeByte = Mth.clamp(maxPacketSizeByte, min, max);
        }
        return maxPacketSizeByte;
    }

    public double getChunkCacheRawSizeMultiplierSafe() {
        return Math.max(0.0D, chunkCacheRawSizeMultiplier);
    }

    private static int parseByteSize(String s) {
        var matcher = Pattern.compile("^([\\d.]+)\\s*(B|KB|MB)?$", Pattern.CASE_INSENSITIVE).matcher(s.trim());
        if (!matcher.matches()) {
            LogUtils.getLogger().error("NEB: Invalid packet size: {} , use default 4MB instead.", s);
            return parseByteSize("4MB");
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null || "B".equalsIgnoreCase(unit)) {
            return (int) value;
        }
        return (int) switch (unit.toUpperCase()) {
            case "KB" -> value * 1024;
            case "MB" -> value * 1024 * 1024;
            default -> {
                LogUtils.getLogger().error("NEB: Invalid packet size: {} , use default 4MB instead.", s);
                yield parseByteSize("4MB");
            }
        };
    }

    public boolean isDelayedChunkCachingUsable() {
        return enableDelayedChunkCaching
                && getDccSizeLimitSafe() > 0
                && getDccDistanceSafe() > 0
                && getDccTimeoutSafeSeconds() > 0;
    }

    public int getContextLevel() {
        return Mth.clamp(contextLevel, 21, 25);
    }

    public int getZstdCompressionLevel() {
        return Mth.clamp(zstdCompressionLevel, -5, 22);
    }

    public boolean shouldUseZstdContextForPlayer(String playerUuid) {
        return playerUuid == null || !playersDoNotUseContext.contains(playerUuid);
    }
}
