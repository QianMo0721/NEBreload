package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import cn.ussshenzhou.notenoughbandwidth.config.TConfig;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import net.minecraft.util.Mth;

import java.util.HashSet;

/**
 * @author USS_Shenzhou
 */
public class NotEnoughBandwidthLegacyConfig implements TConfig {

    @SerializedName("说明-文件")
    public String commentFile = "NEB 配置文件。为兼容旧版本，程序读取时同时支持旧英文键名；重新保存后会输出为中文键名。";

    @SerializedName("说明-兼容模式")
    public String commentCompatibleMode = "兼容模式开启后，会额外跳过黑名单中的数据包聚合，适合在出现进服异常、回弹、丢包表现时排查兼容性问题。";

    @SerializedName(value = "兼容模式", alternate = {"compatibleMode"})
    public boolean compatibleMode = false;

    @SerializedName("说明-兼容模式黑名单")
    public String commentBlackList = "仅在兼容模式开启时生效。列表内容为需要强制跳过聚合的数据包类型标识。";

    @SerializedName(value = "兼容模式黑名单", alternate = {"blackList"})
    public HashSet<String> blackList = new HashSet<>() {{
        add("minecraft:command_suggestion");
        add("minecraft:command_suggestions");
        add("minecraft:commands");
        add("minecraft:chat_command");
        add("minecraft:chat_command_signed");
        add("minecraft:player_info_update");
        add("minecraft:player_info_remove");
    }};

    @SerializedName("说明-调试日志")
    public String commentDebugLog = "是否输出更详细的调试日志。仅在排查问题时建议开启，平时开启会明显刷屏。";

    @SerializedName(value = "调试日志", alternate = {"debugLog"})
    public boolean debugLog = false;

    @SerializedName("说明-上下文等级")
    public String commentContextLevel = "数据包上下文压缩等级，程序内部会自动限制在 21 到 25 之间。数值越高，可能压缩更激进，但兼容性风险也更高。";

    @SerializedName(value = "上下文等级", alternate = {"contextLevel"})
    public int contextLevel = 23;

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
    public int dccSizeLimit = 60;

    @SerializedName("说明-区块缓存距离")
    public String commentDccDistance = "玩家离开主视野后，仍允许继续缓存的棋盘距离（按区块计）。数值越大，缓存保留范围越大。";

    @SerializedName(value = "区块缓存距离", alternate = {"dccDistance"})
    public int dccDistance = 5;

    @SerializedName("说明-区块缓存超时秒数")
    public String commentDccTimeout = "区块离开主视野后，最多还能在缓存中保留的秒数。超时后会被正式卸载。";

    @SerializedName(value = "区块缓存超时秒数", alternate = {"dccTimeout"})
    public int dccTimeout = 60;

    @Expose(serialize = false, deserialize = false)
    public static final HashSet<String> COMMON_BLOCK_LIST = new HashSet<>() {{
        add("minecraft:finish_configuration");
        add(PacketAggregationPacket.TYPE.toString());
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
        add("minecraft:resource_pack");
        add("minecraft:client_information");
        add("minecraft:update_enabled_features");
        // Chunk streaming and chunk cache control packets are too timing-sensitive
        // in the current Forge 1.20.1 port. Let vanilla send them directly.
        add("minecraft:level_chunk_with_light");
        add("minecraft:chunks_biomes");
        add("minecraft:light_update");
        add("minecraft:set_chunk_cache_center");
        add("minecraft:set_chunk_cache_radius");
        add("minecraft:forget_level_chunk");
        add("minecraft:section_blocks_update");
        // Player movement / teleport synchronization is also timing-sensitive on Forge.
        // If these packets are reordered or buffered, the server-side position can lag
        // behind the client and cause rollback after reconnect.
        add("minecraft:move_player_pos");
        add("minecraft:move_player_pos_rot");
        add("minecraft:move_player_rot");
        add("minecraft:move_player_status_only");
        add("minecraft:player_position");
        add("minecraft:player_rotation");
        add("minecraft:accept_teleportation");
        // Forge internal channel packets – skip aggregation
        add("forge:tier_sorting");
        add("forge:registry_data");
        add("forge:spawn_type");
        add("forge:register");
        add("forge:unregister");
        add("minecraft:custom_payload");
    }};

    public static NotEnoughBandwidthLegacyConfig get() {
        return ConfigHelper.getConfigRead(NotEnoughBandwidthLegacyConfig.class);
    }

    public static boolean skipType(String type) {
        var cfg = get();
        return COMMON_BLOCK_LIST.contains(type) || (cfg.compatibleMode && cfg.blackList.contains(type));
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

    public boolean isDelayedChunkCachingUsable() {
        return enableDelayedChunkCaching
                && getDccSizeLimitSafe() > 0
                && getDccDistanceSafe() > 0
                && getDccTimeoutSafeSeconds() > 0;
    }

    public int getContextLevel() {
        return Mth.clamp(contextLevel, 21, 25);
    }
}
