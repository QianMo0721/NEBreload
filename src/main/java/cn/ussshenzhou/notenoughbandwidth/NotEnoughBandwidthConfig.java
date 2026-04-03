package cn.ussshenzhou.notenoughbandwidth;

import cn.ussshenzhou.notenoughbandwidth.config.ConfigHelper;
import com.google.gson.annotations.Expose;
import net.minecraft.util.math.MathHelper;

import java.util.HashSet;

public class NotEnoughBandwidthConfig implements cn.ussshenzhou.notenoughbandwidth.config.TConfig {
    public boolean compatibleMode = false;
    public HashSet<String> blackList = new HashSet<>() {{
        add("minecraft:command_suggestion");
        add("minecraft:command_suggestions");
        add("minecraft:commands");
        add("minecraft:chat_command");
        add("minecraft:chat_command_signed");
        add("minecraft:player_info_update");
        add("minecraft:player_info_remove");
    }};
    public boolean debugLog = false;
    public int contextLevel = 23;
    public int dccSizeLimit = 60;
    public int dccDistance = 5;
    public int dccTimeout = 60;

    @Expose(serialize = false, deserialize = false)
    public static final HashSet<String> COMMON_BLOCK_LIST = new HashSet<>() {{
        add("minecraft:finish_configuration");
        add("minecraft:login");
        add("minecraft:register");
        add("minecraft:unregister");
        add(ModConstants.MOD_ID + ":packet_aggregation_packet");
        add(ModConstants.MOD_ID + ":stat_query");
        add(ModConstants.MOD_ID + ":stat_resp");
    }};

    public static NotEnoughBandwidthConfig get() {
        return ConfigHelper.getConfigRead(NotEnoughBandwidthConfig.class);
    }

    public static boolean skipType(String type) {
        var cfg = get();
        return COMMON_BLOCK_LIST.contains(type)
                || (type.startsWith("minecraft:clientbound/") || type.startsWith("minecraft:serverbound/"))
                || (cfg != null && cfg.compatibleMode && cfg.blackList.contains(type));
    }

    public int getContextLevel() {
        return MathHelper.clamp(contextLevel, 21, 25);
    }
}
