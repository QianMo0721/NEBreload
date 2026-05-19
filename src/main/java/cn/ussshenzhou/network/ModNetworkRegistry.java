package cn.ussshenzhou.network;

import cn.ussshenzhou.notenoughbandwidth.ModConstants;
import cn.ussshenzhou.notenoughbandwidth.network.payload.HandlerThread;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PacketDistributor;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistrar;
import cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.Map;
import java.util.WeakHashMap;

public final class ModNetworkRegistry {
    public static final String PERMISSION_NODE = ModConstants.MOD_ID;
    public static final String STAT_QUERY_CHANNEL = ModConstants.MOD_ID + ":stat_query";
    public static final String STAT_RESP_CHANNEL = ModConstants.MOD_ID + ":stat_resp";

    private static final Map<EntityPlayerMP, Boolean> PENDING_SERVER_STAT = new WeakHashMap<EntityPlayerMP, Boolean>();
    private static volatile boolean initialized;

    private ModNetworkRegistry() {
    }

    public static void init() {
        if (initialized) {
            return;
        }
        PayloadRegistrar registrar = new PayloadRegistrar("1");
        registrar.executesOn(HandlerThread.MAIN).playToServer(StatQuery.SAMPLE, StatQuery.CODEC, StatQuery::handle);
        registrar.executesOn(HandlerThread.MAIN).playToClient(StatRespond.SAMPLE, StatRespond.CODEC, StatRespond::handle);
        MinecraftForge.EVENT_BUS.register(new ModNetworkRegistry());
        initialized = true;
    }

    public static void sendToServer(StatQuery query) {
        if (query == null) {
            query = StatQuery.SAMPLE;
        }
        PacketDistributor.sendToServer(query);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        synchronized (PENDING_SERVER_STAT) {
            for (Map.Entry<EntityPlayerMP, Boolean> entry : PENDING_SERVER_STAT.entrySet()) {
                EntityPlayerMP player = entry.getKey();
                if (player != null && Boolean.TRUE.equals(entry.getValue()) && player.canUseCommand(2, PERMISSION_NODE)) {
                    PacketDistributor.sendToPlayer(player, createLocalStatSnapshot());
                    entry.setValue(Boolean.FALSE);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.player instanceof EntityPlayerMP) {
            synchronized (PENDING_SERVER_STAT) {
                PENDING_SERVER_STAT.remove((EntityPlayerMP) event.player);
            }
        }
    }

    public static void markPending(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        synchronized (PENDING_SERVER_STAT) {
            PENDING_SERVER_STAT.put(player, Boolean.TRUE);
        }
    }

    public static StatRespond createLocalStatSnapshot() {
        return new StatRespond(
                SimpleStatManager.LOCAL.inboundBytesBaked().get(),
                SimpleStatManager.LOCAL.inboundBytesRaw().get(),
                SimpleStatManager.LOCAL.outboundBytesBaked().get(),
                SimpleStatManager.LOCAL.outboundBytesRaw().get(),
                SimpleStatManager.LOCAL.inboundSpeedBaked().averageIn1s(),
                SimpleStatManager.LOCAL.inboundSpeedRaw().averageIn1s(),
                SimpleStatManager.LOCAL.outboundSpeedBaked().averageIn1s(),
                SimpleStatManager.LOCAL.outboundSpeedRaw().averageIn1s()
        );
    }

    public static void deliverStatSnapshot(EntityPlayerMP player) {
        if (player == null) {
            return;
        }
        PacketDistributor.sendToPlayer(player, createLocalStatSnapshot());
    }
}
