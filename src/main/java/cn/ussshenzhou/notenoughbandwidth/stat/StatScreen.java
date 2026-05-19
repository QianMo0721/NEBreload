package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.network.ModNetworkRegistry;
import cn.ussshenzhou.network.StatQuery;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.LOCAL;

public class StatScreen extends GuiScreen {
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

    @Override
    public void updateScreen() {
        super.updateScreen();
        if (tick % 10 == 0) {
            ModNetworkRegistry.sendToServer(new StatQuery());

            actualClientInboundText = formatInbound((int) LOCAL.inboundSpeedBaked().averageIn1s(), LOCAL.inboundBytesBaked().get());
            actualClientOutboundText = formatOutbound((int) LOCAL.outboundSpeedBaked().averageIn1s(), LOCAL.outboundBytesBaked().get());
            rawClientInboundText = formatInbound((int) LOCAL.inboundSpeedRaw().averageIn1s(), LOCAL.inboundBytesRaw().get());
            rawClientOutboundText = formatOutbound((int) LOCAL.outboundSpeedRaw().averageIn1s(), LOCAL.outboundBytesRaw().get());
            ratioClientInboundText = getRatio(LOCAL.inboundBytesBaked().get(), LOCAL.inboundBytesRaw().get());
            ratioClientOutboundText = getRatio(LOCAL.outboundBytesBaked().get(), LOCAL.outboundBytesRaw().get());

            actualServerInboundText = formatInbound((int) SimpleStatManager.inboundSpeedBakedServer, SimpleStatManager.inboundBytesBakedServer);
            actualServerOutboundText = formatOutbound((int) SimpleStatManager.outboundSpeedBakedServer, SimpleStatManager.outboundBytesBakedServer);
            rawServerInboundText = formatInbound((int) SimpleStatManager.inboundSpeedRawServer, SimpleStatManager.inboundBytesRawServer);
            rawServerOutboundText = formatOutbound((int) SimpleStatManager.outboundSpeedRawServer, SimpleStatManager.outboundBytesRawServer);
            ratioServerInboundText = getRatio(SimpleStatManager.inboundBytesBakedServer, SimpleStatManager.inboundBytesRawServer);
            ratioServerOutboundText = getRatio(SimpleStatManager.outboundBytesBakedServer, SimpleStatManager.outboundBytesRawServer);
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
            return String.format("%.2f GiB", bytes / (1024d * 1024d * 1024d));
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0x80000000);

        int leftX = 10;
        int rightX = Math.max(this.width / 2 + 10, 220);

        this.fontRenderer.drawString(clientTitle, leftX, 10, TITLE_COLOR);
        this.fontRenderer.drawString(actualLabel, leftX, 30, TITLE_COLOR);
        this.fontRenderer.drawString(actualClientInboundText, leftX, 40, ACTUAL_COLOR);
        this.fontRenderer.drawString(actualClientOutboundText, rightX, 40, ACTUAL_COLOR);
        this.fontRenderer.drawString(rawLabel, leftX, 60, TITLE_COLOR);
        this.fontRenderer.drawString(rawClientInboundText, leftX, 70, RAW_COLOR);
        this.fontRenderer.drawString(rawClientOutboundText, rightX, 70, RAW_COLOR);
        this.fontRenderer.drawString(ratioLabel, leftX, 90, TITLE_COLOR);
        this.fontRenderer.drawString(ratioClientInboundText, leftX + 80, 90, RATIO_COLOR);
        this.fontRenderer.drawString(ratioClientOutboundText, rightX, 90, RATIO_COLOR);

        if (hasSufficientPermissions()) {
            this.fontRenderer.drawString(serverTitle, leftX, 120, TITLE_COLOR);
            this.fontRenderer.drawString(actualLabel, leftX, 140, TITLE_COLOR);
            this.fontRenderer.drawString(actualServerInboundText, leftX, 150, ACTUAL_COLOR);
            this.fontRenderer.drawString(actualServerOutboundText, rightX, 150, ACTUAL_COLOR);
            this.fontRenderer.drawString(rawLabel, leftX, 170, TITLE_COLOR);
            this.fontRenderer.drawString(rawServerInboundText, leftX, 180, RAW_COLOR);
            this.fontRenderer.drawString(rawServerOutboundText, rightX, 180, RAW_COLOR);
            this.fontRenderer.drawString(ratioLabel, leftX, 200, TITLE_COLOR);
            this.fontRenderer.drawString(ratioServerInboundText, leftX + 80, 200, RATIO_COLOR);
            this.fontRenderer.drawString(ratioServerOutboundText, rightX, 200, RATIO_COLOR);
        }
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private boolean hasSufficientPermissions() {
        return Minecraft.getMinecraft().player != null && Minecraft.getMinecraft().player.canUseCommand(2, ModNetworkRegistry.PERMISSION_NODE);
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
