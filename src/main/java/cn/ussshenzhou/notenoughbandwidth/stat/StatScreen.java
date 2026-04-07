package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.network.StatQuery;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

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
            cn.ussshenzhou.network.ModNetworkRegistry.QUERY_CHANNEL.sendToServer(new StatQuery());
            actualC = "↓ Inbound  "
                    + getReadableSpeed((int) LOCAL.inboundSpeedBaked().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.inboundBytesBaked().get())
                    + "    ↑ Outbound  "
                    + getReadableSpeed((int) LOCAL.outboundSpeedBaked().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.outboundBytesBaked().get());
            rawC = "↓ Inbound  "
                    + getReadableSpeed((int) LOCAL.inboundSpeedRaw().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.inboundBytesRaw().get())
                    + "    ↑ Outbound  "
                    + getReadableSpeed((int) LOCAL.outboundSpeedRaw().averageIn1s())
                    + "  Total  "
                    + getReadableSize(LOCAL.outboundBytesRaw().get());
            ratioC = "Ratio                            "
                    + getRatio(LOCAL.inboundBytesBaked().get(), LOCAL.inboundBytesRaw().get())
                    + "                                        "
                    + getRatio(LOCAL.outboundBytesBaked().get(), LOCAL.outboundBytesRaw().get());

            actualS = "↓ Inbound  "
                    + getReadableSpeed((int) inboundSpeedBakedServer)
                    + "  Total  "
                    + getReadableSize(inboundBytesBakedServer)
                    + "    ↑ Outbound  "
                    + getReadableSpeed((int) outboundSpeedBakedServer)
                    + "  Total  "
                    + getReadableSize(outboundBytesBakedServer);
            rawS = "↓ Inbound  "
                    + getReadableSpeed((int) inboundSpeedRawServer)
                    + "  Total  "
                    + getReadableSize(inboundBytesRawServer)
                    + "    ↑ Outbound  "
                    + getReadableSpeed((int) outboundSpeedRawServer)
                    + "  Total  "
                    + getReadableSize(outboundBytesRawServer);
            ratioS = "Ratio                            "
                    + getRatio(inboundBytesBakedServer, inboundBytesRawServer)
                    + "                                        "
                    + getRatio(outboundBytesBakedServer, outboundBytesRawServer);
        }
        tick++;
    }

    private static String getRatio(double baked, double raw) {
        if (raw <= 0) {
            return "-";
        }
        return String.format("%.2f%%", 100d * baked / raw);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.fill(0, 0, this.width, this.height, 0x80000000);
        guiGraphics.drawString(this.font, client, 10, 10, 0xFFFFFF);
        guiGraphics.drawString(this.font, actual, 10, 30, 0xFFFFFF);
        guiGraphics.drawString(this.font, actualC, 10, 40, 0xAAFFAA);
        guiGraphics.drawString(this.font, raw, 10, 60, 0xFFFFFF);
        guiGraphics.drawString(this.font, rawC, 10, 70, 0xFFAAAA);
        guiGraphics.drawString(this.font, ratioC, 10, 90, 0xAAAAFF);

        guiGraphics.drawString(this.font, server, 10, 120, 0xFFFFFF);
        guiGraphics.drawString(this.font, actual, 10, 140, 0xFFFFFF);
        guiGraphics.drawString(this.font, actualS, 10, 150, 0xAAFFAA);
        guiGraphics.drawString(this.font, raw, 10, 170, 0xFFFFFF);
        guiGraphics.drawString(this.font, rawS, 10, 180, 0xFFAAAA);
        guiGraphics.drawString(this.font, ratioS, 10, 200, 0xAAAAFF);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
