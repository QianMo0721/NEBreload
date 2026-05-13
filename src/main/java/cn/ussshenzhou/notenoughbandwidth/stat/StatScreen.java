package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.network.StatQuery;
import cn.ussshenzhou.notenoughbandwidth.aggregation.AggregationManager;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.*;

/**
 * @author USS_Shenzhou
 */
public class StatScreen extends Screen {
    private static final int TITLE_COLOR = 0xFFFFFF;
    private static final int ACTUAL_COLOR = 0xA8F7A8;
    private static final int RAW_COLOR = 0xF2A3A3;
    private static final int RATIO_COLOR = 0xB1B0FF;

    private final String clientTitle = "Client";
    private final String actualLabel = "Actual Transmission";
    private final String rawLabel = "Raw Payload";
    private final String ratioLabel = "Ratio";

    private String actualClientInboundText = "-";
    private String actualClientOutboundText = "-";
    private String rawClientInboundText = "-";
    private String rawClientOutboundText = "-";
    private String ratioClientInboundText = "-";
    private String ratioClientOutboundText = "-";

    private final String serverTitle = "Server";
    private String actualServerInboundText = "-";
    private String actualServerOutboundText = "-";
    private String rawServerInboundText = "-";
    private String rawServerOutboundText = "-";
    private String ratioServerInboundText = "-";
    private String ratioServerOutboundText = "-";

    private int tick = 0;

    private String directCountText = "-";
    private String directMemoryText = "-";
    private String directCapacityText = "-";
    private String nebZstdContextText = "-";
    private String nebBufferedConnectionText = "-";
    private String nebBufferedPacketText = "-";

    public StatScreen() {
        super(Component.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (tick % 10 == 0) {
            cn.ussshenzhou.network.ModNetworkRegistry.sendToServer(new StatQuery());

            actualClientInboundText = formatInbound((int) LOCAL.inboundSpeedBaked().averageIn1s(), LOCAL.inboundBytesBaked().get());
            actualClientOutboundText = formatOutbound((int) LOCAL.outboundSpeedBaked().averageIn1s(), LOCAL.outboundBytesBaked().get());
            rawClientInboundText = formatInbound((int) LOCAL.inboundSpeedRaw().averageIn1s(), LOCAL.inboundBytesRaw().get());
            rawClientOutboundText = formatOutbound((int) LOCAL.outboundSpeedRaw().averageIn1s(), LOCAL.outboundBytesRaw().get());
            ratioClientInboundText = getRatio(LOCAL.inboundBytesBaked().get(), LOCAL.inboundBytesRaw().get());
            ratioClientOutboundText = getRatio(LOCAL.outboundBytesBaked().get(), LOCAL.outboundBytesRaw().get());

            actualServerInboundText = formatInbound((int) inboundSpeedBakedServer, inboundBytesBakedServer);
            actualServerOutboundText = formatOutbound((int) outboundSpeedBakedServer, outboundBytesBakedServer);
            rawServerInboundText = formatInbound((int) inboundSpeedRawServer, inboundBytesRawServer);
            rawServerOutboundText = formatOutbound((int) outboundSpeedRawServer, outboundBytesRawServer);
            ratioServerInboundText = getRatio(inboundBytesBakedServer, inboundBytesRawServer);
            ratioServerOutboundText = getRatio(outboundBytesBakedServer, outboundBytesRawServer);

            directCountText = "DirectByteBuffer实例数(JMX): " + getDirectBufferCount();
            directMemoryText = "Direct内存占用: " + getReadableSize(getDirectMemoryUsed());
            directCapacityText = "Direct总容量: " + getReadableSize(getDirectCapacity());
            nebZstdContextText = "NEB Zstd Context Cache: " + ZstdHelper.getContextCacheSize();
            nebBufferedConnectionText = "NEB Aggregation Buffered Connections: " + AggregationManager.getBufferedConnectionCount();
            nebBufferedPacketText = "NEB Aggregation Buffered Packets: " + AggregationManager.getBufferedPacketCount();
        }
        tick++;
    }

    private static String formatInbound(int speedBytes, long totalBytes) {
        return "↓ Inbound  " + getReadableSpeed(speedBytes) + "  Total  " + getReadableSize(totalBytes);
    }

    private static String formatOutbound(int speedBytes, long totalBytes) {
        return "↑ Outbound  " + getReadableSpeed(speedBytes) + "  Total  " + getReadableSize(totalBytes);
    }

    private static String getRatio(double baked, double raw) {
        if (raw <= 0) {
            return "-";
        }
        return String.format("%.2f%%", 100d * baked / raw);
    }

    private static String getReadableSpeed(int bytes) {
        if (bytes < 1000) {
            return bytes + " Bytes/S";
        } else if (bytes < 1000 * 1000) {
            return String.format("%.1f KiB/S", bytes / 1024f);
        } else {
            return String.format("%.2f MiB/S", bytes / (1024 * 1024f));
        }
    }

    private static String getReadableSize(long bytes) {
        if (bytes < 1000) {
            return bytes + " Bytes";
        } else if (bytes < 1000 * 1000) {
            return String.format("%.1f KiB", bytes / 1024d);
        } else if (bytes < 1000 * 1000 * 1000) {
            return String.format("%.2f MiB", bytes / (1024 * 1024d));
        } else {
            return String.format("%.2f GiB", bytes / (1024 * 1024 * 1024d));
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);

        int leftX = 10;
        int rightX = Math.max(this.width / 2 + 10, 220);

        guiGraphics.drawString(this.font, clientTitle, leftX, 10, TITLE_COLOR);
        guiGraphics.drawString(this.font, actualLabel, leftX, 30, TITLE_COLOR);
        guiGraphics.drawString(this.font, actualClientInboundText, leftX, 40, ACTUAL_COLOR);
        guiGraphics.drawString(this.font, actualClientOutboundText, rightX, 40, ACTUAL_COLOR);
        guiGraphics.drawString(this.font, rawLabel, leftX, 60, TITLE_COLOR);
        guiGraphics.drawString(this.font, rawClientInboundText, leftX, 70, RAW_COLOR);
        guiGraphics.drawString(this.font, rawClientOutboundText, rightX, 70, RAW_COLOR);
        guiGraphics.drawString(this.font, ratioLabel, leftX, 90, TITLE_COLOR);
        guiGraphics.drawString(this.font, ratioClientInboundText, leftX + 80, 90, RATIO_COLOR);
        guiGraphics.drawString(this.font, ratioClientOutboundText, rightX, 90, RATIO_COLOR);

        if (hasSufficientPermissions()) {
            guiGraphics.drawString(this.font, serverTitle, leftX, 120, TITLE_COLOR);
            guiGraphics.drawString(this.font, actualLabel, leftX, 140, TITLE_COLOR);
            guiGraphics.drawString(this.font, actualServerInboundText, leftX, 150, ACTUAL_COLOR);
            guiGraphics.drawString(this.font, actualServerOutboundText, rightX, 150, ACTUAL_COLOR);
            guiGraphics.drawString(this.font, rawLabel, leftX, 170, TITLE_COLOR);
            guiGraphics.drawString(this.font, rawServerInboundText, leftX, 180, RAW_COLOR);
            guiGraphics.drawString(this.font, rawServerOutboundText, rightX, 180, RAW_COLOR);
            guiGraphics.drawString(this.font, ratioLabel, leftX, 200, TITLE_COLOR);
            guiGraphics.drawString(this.font, ratioServerInboundText, leftX + 80, 200, RATIO_COLOR);
            guiGraphics.drawString(this.font, ratioServerOutboundText, rightX, 200, RATIO_COLOR);
        }

        int memBaseY = this.height - 36;
        int panelTop = memBaseY - 4;
        int panelBottom = this.height - 6;
        guiGraphics.fill(6, panelTop, this.width - 6, panelBottom, 0xB0000000);
        final float memScale = 0.78f;
        drawScaledString(guiGraphics, "[NEB Memory Analyzer]", leftX, memBaseY, TITLE_COLOR, memScale);
        drawScaledString(guiGraphics, directCountText, leftX, memBaseY + 8, ACTUAL_COLOR, memScale);
        drawScaledString(guiGraphics, directMemoryText, leftX, memBaseY + 15, RAW_COLOR, memScale);
        drawScaledString(guiGraphics, directCapacityText, leftX, memBaseY + 22, RATIO_COLOR, memScale);
        drawScaledString(guiGraphics, nebZstdContextText, rightX, memBaseY + 8, ACTUAL_COLOR, memScale);
        drawScaledString(guiGraphics, nebBufferedConnectionText, rightX, memBaseY + 15, RAW_COLOR, memScale);
        drawScaledString(guiGraphics, nebBufferedPacketText, rightX, memBaseY + 22, RATIO_COLOR, memScale);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private static long getDirectBufferCount() {
        BufferPoolMXBean bean = getBufferPool("direct");
        return bean == null ? -1 : bean.getCount();
    }

    private static long getDirectMemoryUsed() {
        BufferPoolMXBean bean = getBufferPool("direct");
        return bean == null ? -1 : bean.getMemoryUsed();
    }

    private static long getDirectCapacity() {
        BufferPoolMXBean bean = getBufferPool("direct");
        return bean == null ? -1 : bean.getTotalCapacity();
    }

    private static BufferPoolMXBean getBufferPool(String name) {
        for (BufferPoolMXBean bean : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class)) {
            if (name.equalsIgnoreCase(bean.getName())) {
                return bean;
            }
        }
        return null;
    }

    private void drawScaledString(GuiGraphics guiGraphics, String text, int x, int y, int color, float scale) {
        guiGraphics.pose().pushPose();
        guiGraphics.pose().scale(scale, scale, 1.0f);
        guiGraphics.drawString(this.font, text, Math.round(x / scale), Math.round(y / scale), color);
        guiGraphics.pose().popPose();
    }

    private boolean hasSufficientPermissions() {
        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.hasPermissions(2);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
