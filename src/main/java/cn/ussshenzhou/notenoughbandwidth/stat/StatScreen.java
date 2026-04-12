package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.network.StatQuery;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.resources.I18n;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.*;

/**
 * @author USS_Shenzhou
 */
public class StatScreen extends GuiScreen {
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
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
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
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        drawRect(0, 0, this.width, this.height, 0x80000000);
        this.drawString(this.fontRenderer, client, 10, 10, 0xFFFFFF);
        this.drawString(this.fontRenderer, actual, 10, 30, 0xFFFFFF);
        this.drawString(this.fontRenderer, actualC, 10, 40, 0xAAFFAA);
        this.drawString(this.fontRenderer, raw, 10, 60, 0xFFFFFF);
        this.drawString(this.fontRenderer, rawC, 10, 70, 0xFFAAAA);
        this.drawString(this.fontRenderer, ratioC, 10, 90, 0xAAAAFF);

        this.drawString(this.fontRenderer, server, 10, 120, 0xFFFFFF);
        this.drawString(this.fontRenderer, actual, 10, 140, 0xFFFFFF);
        this.drawString(this.fontRenderer, actualS, 10, 150, 0xAAFFAA);
        this.drawString(this.fontRenderer, raw, 10, 170, 0xFFFFFF);
        this.drawString(this.fontRenderer, rawS, 10, 180, 0xFFAAAA);
        this.drawString(this.fontRenderer, ratioS, 10, 200, 0xAAAAFF);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    public boolean isPauseScreen() {
        return false;
    }
}
