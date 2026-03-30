package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.network.StatQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.network.PacketDistributor;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.*;

/**
 * @author USS_Shenzhou
 */
public class StatScreen extends Screen {
    private final String client = "Client";
    private final String actual = "Actual Transmission";
    private String actualC = "";
    private String raw = "Raw Payload";
    private String rawC = "";
    private String ratioC = "";

    private final String server = "Server";
    private String actualS = "-";
    private String rawS = "-";
    private String ratioS = "-";

    private int tick = 0;

    public StatScreen() {
        super(Component.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (tick % 10 == 0) {
            cn.ussshenzhou.notenoughbandwidth.util.ModNetworkRegistry.CHANNEL.sendToServer(new StatQuery());
            actualC = "\u2193 Inbound  "
                    + getReadableSpeed((int) LOCAL.inboundSpeedBaked().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.inboundBytesBaked().get())
                    + "    \u2191 Outbound  "
                    + getReadableSpeed((int) LOCAL.outboundSpeedBaked().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.outboundBytesBaked().get());
            rawC = "\u2193 Inbound  "
                    + getReadableSpeed((int) LOCAL.inboundSpeedRaw().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.inboundBytesRaw().get())
                    + "    \u2191 Outbound  "
                    + getReadableSpeed((int) LOCAL.outboundSpeedRaw().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.outboundBytesRaw().get());
            double bakedC = LOCAL.inboundBytesBaked().get() + LOCAL.outboundBytesBaked().get();
            double rawCVal = LOCAL.inboundBytesRaw().get() + LOCAL.outboundBytesRaw().get();
            ratioC = rawCVal == 0 ? "-" : String.format("%.2f%%", bakedC / rawCVal * 100);

            actualS = "\u2193 Inbound  "
                    + getReadableSpeed((int) inboundSpeedBakedServer)
                    + "  Total  "
                    + getReadableSize(inboundBytesBakedServer)
                    + "    \u2191 Outbound  "
                    + getReadableSpeed((int) outboundSpeedBakedServer)
                    + "  Total  "
                    + getReadableSize(outboundBytesBakedServer);
            rawS = "\u2193 Inbound  "
                    + getReadableSpeed((int) inboundSpeedRawServer)
                    + "  Total  "
                    + getReadableSize(inboundBytesRawServer)
                    + "    \u2191 Outbound  "
                    + getReadableSpeed((int) outboundSpeedRawServer)
                    + "  Total  "
                    + getReadableSize(outboundBytesRawServer);
            double bakedS = inboundBytesBakedServer + outboundBytesBakedServer;
            double rawSVal = inboundBytesRawServer + outboundBytesRawServer;
            ratioS = rawSVal == 0 ? "-" : String.format("%.2f%%", bakedS / rawSVal * 100);
        }
        tick++;
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        int y = 20;
        int lineHeight = 12;
        guiGraphics.drawString(this.font, client, 10, y, 0xFFFFFF);
        y += lineHeight;
        guiGraphics.drawString(this.font, actual + ": " + actualC, 10, y, 0xAAFFAA);
        y += lineHeight;
        guiGraphics.drawString(this.font, raw + ": " + rawC, 10, y, 0xFFAAAA);
        y += lineHeight;
        guiGraphics.drawString(this.font, "Ratio (Baked/Raw): " + ratioC, 10, y, 0xAAAAFF);
        y += lineHeight * 2;
        guiGraphics.drawString(this.font, server, 10, y, 0xFFFFFF);
        y += lineHeight;
        guiGraphics.drawString(this.font, actual + ": " + actualS, 10, y, 0xAAFFAA);
        y += lineHeight;
        guiGraphics.drawString(this.font, raw + ": " + rawS, 10, y, 0xFFAAAA);
        y += lineHeight;
        guiGraphics.drawString(this.font, "Ratio (Baked/Raw): " + ratioS, 10, y, 0xAAAAFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
