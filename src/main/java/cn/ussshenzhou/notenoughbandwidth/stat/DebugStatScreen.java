package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.network.payload.ClientPayloadBridge;
import cn.ussshenzhou.notenoughbandwidth.network.payload.NetworkPayloadSetup;
import cn.ussshenzhou.notenoughbandwidth.network.payload.PayloadRegistry;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.network.EnumConnectionState;
import net.minecraft.network.NetworkManager;

import java.util.Map;

public class DebugStatScreen extends GuiScreen {
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int LABEL_COLOR = 0xD0D0D0;
    private static final int VALUE_COLOR = 0x8FE38F;
    private static final int WARN_COLOR = 0xF2C879;
    private static final int DETAIL_COLOR = 0x9FC5E8;

    private int tick;

    private String heapUsed = "-";
    private String heapCommitted = "-";
    private String heapMax = "-";
    private String nonHeapUsed = "-";
    private String pendingConnections = "-";
    private String pendingPackets = "-";
    private String pendingBytes = "-";
    private String pendingMaxPerConnection = "-";
    private String zstdContextCount = "-";
    private String payloadRegistrationCount = "-";
    private String negotiatedPayloadCount = "-";
    private String negotiatedPlayPayloadCount = "-";
    private String transportChannelReady = "-";
    private String zstdAvailable = "-";
    private String clientPlayer = "-";
    private String worldSide = "-";

    @Override
    public void initGui() {
        super.initGui();
        refreshStats();
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (tick % 10 == 0) {
            refreshStats();
        }
        tick++;
    }

    private void refreshStats() {
        Runtime runtime = Runtime.getRuntime();
        long total = runtime.totalMemory();
        long free = runtime.freeMemory();
        long max = runtime.maxMemory();
        long used = Math.max(0L, total - free);

        heapUsed = readableSize(used);
        heapCommitted = readableSize(total);
        heapMax = readableSize(max);
        nonHeapUsed = readableSize(java.lang.management.ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed());

        AggregationManager.DebugSnapshot snapshot = AggregationManager.debugSnapshot();
        pendingConnections = String.valueOf(snapshot.connectionCount());
        pendingPackets = String.valueOf(snapshot.totalBufferedPackets());
        pendingBytes = readableSize(snapshot.totalEstimatedBytes());
        pendingMaxPerConnection = String.valueOf(snapshot.maxBufferedPacketsPerConnection());

        zstdContextCount = String.valueOf(ZstdHelper.debugContextCount());
        payloadRegistrationCount = String.valueOf(PayloadRegistry.registrations().size());
        zstdAvailable = String.valueOf(ZstdHelper.isAvailable());

        NetworkManager connection = ClientPayloadBridge.getClientNetworkManager();
        NetworkPayloadSetup payloadSetup = connection == null ? null : cn.ussshenzhou.notenoughbandwidth.network.payload.ChannelAttributes.getPayloadSetup(connection);
        negotiatedPayloadCount = payloadSetup == null ? "0" : String.valueOf(countAllChannels(payloadSetup));
        negotiatedPlayPayloadCount = payloadSetup == null ? "0" : String.valueOf(payloadSetup.getChannels(EnumConnectionState.PLAY).size());
        transportChannelReady = String.valueOf(payloadSetup != null && payloadSetup.hasChannel(cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket.CHANNEL_NAME));

        Minecraft minecraft = Minecraft.getMinecraft();
        clientPlayer = minecraft.player == null ? "-" : minecraft.player.getName();
        worldSide = minecraft.world == null ? "No World" : (minecraft.isSingleplayer() ? "Integrated Client" : "Remote Client");
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0x90000000);

        int x = 10;
        int y = 10;
        int line = 12;

        this.fontRenderer.drawString("NEB Debug Monitor", x, y, TITLE_COLOR);
        y += line * 2;

        drawPair(x, y, "Open Key", "Alt + P", DETAIL_COLOR); y += line;
        drawPair(x, y, "Client Player", clientPlayer, DETAIL_COLOR); y += line;
        drawPair(x, y, "World", worldSide, DETAIL_COLOR); y += line * 2;

        this.fontRenderer.drawString("JVM Memory", x, y, TITLE_COLOR); y += line;
        drawPair(x, y, "Heap Used", heapUsed, VALUE_COLOR); y += line;
        drawPair(x, y, "Heap Committed", heapCommitted, VALUE_COLOR); y += line;
        drawPair(x, y, "Heap Max", heapMax, VALUE_COLOR); y += line;
        drawPair(x, y, "Non-Heap Used", nonHeapUsed, VALUE_COLOR); y += line * 2;

        this.fontRenderer.drawString("NEB Runtime", x, y, TITLE_COLOR); y += line;
        drawPair(x, y, "Buffered Connections", pendingConnections, VALUE_COLOR); y += line;
        drawPair(x, y, "Buffered Packets", pendingPackets, VALUE_COLOR); y += line;
        drawPair(x, y, "Buffered Estimated Bytes", pendingBytes, WARN_COLOR); y += line;
        drawPair(x, y, "Max Buffered Packets/Conn", pendingMaxPerConnection, WARN_COLOR); y += line;
        drawPair(x, y, "Zstd Context Count", zstdContextCount, VALUE_COLOR); y += line;
        drawPair(x, y, "Payload Registrations", payloadRegistrationCount, VALUE_COLOR); y += line;
        drawPair(x, y, "Negotiated Payloads", negotiatedPayloadCount, VALUE_COLOR); y += line;
        drawPair(x, y, "Negotiated PLAY Payloads", negotiatedPlayPayloadCount, VALUE_COLOR); y += line;
        drawPair(x, y, "Transport Ready", transportChannelReady, VALUE_COLOR); y += line;
        drawPair(x, y, "Zstd Available", zstdAvailable, VALUE_COLOR); y += line * 2;

        this.fontRenderer.drawString("Notes", x, y, TITLE_COLOR); y += line;
        this.fontRenderer.drawString("- Buffered stats come from the aggregation waiting queue.", x, y, LABEL_COLOR); y += line;
        this.fontRenderer.drawString("- Negotiated payloads reflect the current connection setup snapshot.", x, y, LABEL_COLOR); y += line;
        this.fontRenderer.drawString("- PLAY payloads are the effective channels used by runtime packet transport.", x, y, LABEL_COLOR); y += line;
        this.fontRenderer.drawString("- Zstd context count is the current cache entry count, not native bytes.", x, y, LABEL_COLOR); y += line;
        this.fontRenderer.drawString("- This page helps observe trends, not prove leak existence by itself.", x, y, LABEL_COLOR);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawPair(int x, int y, String label, String value, int valueColor) {
        this.fontRenderer.drawString(label + ":", x, y, LABEL_COLOR);
        this.fontRenderer.drawString(value, x + 150, y, valueColor);
    }

    private static int countAllChannels(NetworkPayloadSetup setup) {
        int total = 0;
        for (Map<String, cn.ussshenzhou.notenoughbandwidth.network.payload.NetworkChannel> value : setup.channels().values()) {
            total += value.size();
        }
        return total;
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return String.format("%.1f KiB", bytes / 1024.0D);
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.2f MiB", bytes / (1024.0D * 1024.0D));
        }
        return String.format("%.2f GiB", bytes / (1024.0D * 1024.0D * 1024.0D));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
