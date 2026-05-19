package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.config.TConfig;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.util.HashSet;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class NotEnoughBandwidthLegacyConfig implements TConfig {

    @SerializedName("说明-文件")
    public String commentFile = "NEB 配置文件。该 1.12.2 版本仅保留聚合、压缩、配置与统计页面功能。";

    @SerializedName("说明-兼容模式")
    public String commentCompatibleMode = "兼容模式开启后，会额外跳过黑名单中的数据包聚合。建议在代理、联机模组较多，或出现时序敏感问题时开启。";

    @SerializedName(value = "兼容模式", alternate = {"compatibleMode"})
    public boolean compatibleMode = true;

    @SerializedName("说明-兼容模式黑名单")
    public String commentBlackList = "仅在兼容模式开启时生效。列表中的 channel 会先触发 flush，再直接发送，不参与聚合。";

    @SerializedName(value = "兼容模式黑名单", alternate = {"blackList"})
    public HashSet<String> blackList = new HashSet<String>() {{
        add("minecraft:chat");
        add("minecraft:resource_pack");
        add("minecraft:respawn");
        add("minecraft:game_event");
        add("minecraft:set_time");
        add("minecraft:player_position");
        add("minecraft:player_abilities");
        add("minecraft:set_health");
        add("minecraft:set_experience");
        add("minecraft:update_mob_effect");
        add("minecraft:remove_mob_effect");
        add("minecraft:set_chunk_cache_radius");
        add("MC|Brand");
        add("REGISTER");
        add("UNREGISTER");
        add("FML|HS");
        add("FML|MP");
        add("FML");
        add("FORGE|HS");
        add("FORGE|MP");
        add("FORGE");
        add("forge:split");
    }};

    @SerializedName("说明-调试日志")
    public String commentDebugLog = "是否输出更详细的调试日志。仅在排查问题时建议开启。";

    @SerializedName(value = "调试日志", alternate = {"debugLog"})
    public boolean debugLog = false;

    @SerializedName("说明-上下文等级")
    public String commentContextLevel = "数据包上下文压缩等级，程序内部会自动限制在 21 到 25 之间。";

    @SerializedName(value = "上下文等级", alternate = {"contextLevel"})
    public int contextLevel = 23;

    @SerializedName("说明-zstd压缩等级")
    public String commentZstdCompressionLevel = "zstd 算法压缩等级。程序内部会自动限制在 -5 到 22 之间。";

    @SerializedName(value = "zstd压缩等级", alternate = {"zstdCompressionLevel", "compressionLevel"})
    public int zstdCompressionLevel = 3;

    @SerializedName("说明-不复用Zstd上下文的玩家")
    public String commentPlayersDoNotUseContext = "仅在服务端生效。指定的玩家 UUID 连接不会复用 Zstd 上下文。";

    @SerializedName(value = "不复用Zstd上下文的玩家", alternate = {"playersDoNotUseContext"})
    public HashSet<String> playersDoNotUseContext = new HashSet<String>() {{
        add("00000000-0000-0000-0000-000000000000");
    }};

    @SerializedName("说明-最大单包大小")
    public String commentMaxPacketSize = "单个聚合包允许的最大大小。支持 B/KB/MB 写法，程序内部限制在 2MB 到 64MB 之间。";

    @SerializedName(value = "最大单包大小", alternate = {"maxPacketSize"})
    public String maxPacketSize = "4MB";

    @Expose(serialize = false, deserialize = false)
    private transient int maxPacketSizeByte = -1;

    public static final HashSet<String> COMMON_BLOCK_LIST = new HashSet<String>() {{
        add(PacketAggregationPacket.CHANNEL_NAME);
        add(ModConstants.MOD_ID + ":stat_query");
        add(ModConstants.MOD_ID + ":stat_resp");
        add("minecraft:disconnect");
        add("minecraft:keep_alive");
        add("minecraft:ping");
        add("minecraft:pong");
        add("minecraft:login");
        add("minecraft:register");
        add("minecraft:unregister");
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
        return ConfigHelper.getConfig();
    }

    public int getContextLevel() {
        return clamp(contextLevel, 21, 25);
    }

    public int getZstdCompressionLevel() {
        return clamp(zstdCompressionLevel, -5, 22);
    }

    public int getMaxPacketSize() {
        if (maxPacketSizeByte < 0) {
            int parsed = parseByteSize(maxPacketSize);
            int min = parseByteSize("2MB");
            int max = parseByteSize("64MB");
            maxPacketSizeByte = clamp(parsed, min, max);
        }
        return maxPacketSizeByte;
    }

    public boolean shouldUseZstdContextForPlayer(String playerUuid) {
        return playerUuid == null || !playersDoNotUseContext.contains(playerUuid);
    }

    public static boolean skipType(String type) {
        if (type == null || type.isEmpty()) {
            return true;
        }
        String normalized = normalizeType(type);
        if (normalized.startsWith("ftbquests:") || normalized.startsWith("ftbteams:") || normalized.startsWith("ftblibrary:")) {
            return true;
        }
        if (normalized.startsWith("voicechat:")) {
            return true;
        }
        if (normalized.startsWith("l2screentracker:")) {
            return true;
        }
        NotEnoughBandwidthLegacyConfig cfg = get();
        return COMMON_BLOCK_LIST.contains(normalized) || (cfg.compatibleMode && cfg.blackList.contains(normalized));
    }

    public static String normalizeType(String type) {
        if (type == null) {
            return "";
        }
        String lowered = type.toLowerCase(Locale.ROOT);
        if ("mc|brand".equals(lowered)) {
            return "MC|Brand";
        }
        if ("register".equals(lowered)) {
            return "REGISTER";
        }
        if ("unregister".equals(lowered)) {
            return "UNREGISTER";
        }
        if ("fml|hs".equals(lowered)) {
            return "FML|HS";
        }
        if ("fml|mp".equals(lowered)) {
            return "FML|MP";
        }
        if ("fml".equals(lowered)) {
            return "FML";
        }
        if ("forge|hs".equals(lowered)) {
            return "FORGE|HS";
        }
        if ("forge|mp".equals(lowered)) {
            return "FORGE|MP";
        }
        if ("forge".equals(lowered)) {
            return "FORGE";
        }
        return lowered;
    }

    private static int parseByteSize(String text) {
        if (text == null) {
            return 4 * 1024 * 1024;
        }
        Matcher matcher = Pattern.compile("^([\\d.]+)\\s*(B|KB|MB)?$", Pattern.CASE_INSENSITIVE).matcher(text.trim());
        if (!matcher.matches()) {
            return 4 * 1024 * 1024;
        }
        double value = Double.parseDouble(matcher.group(1));
        String unit = matcher.group(2);
        if (unit == null) {
            return (int) value;
        }
        String normalized = unit.toUpperCase(Locale.ROOT);
        if ("B".equals(normalized)) {
            return (int) value;
        }
        if ("KB".equals(normalized)) {
            return (int) (value * 1024.0D);
        }
        if ("MB".equals(normalized)) {
            return (int) (value * 1024.0D * 1024.0D);
        }
        return 4 * 1024 * 1024;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
