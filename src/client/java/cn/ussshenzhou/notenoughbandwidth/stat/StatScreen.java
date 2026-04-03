package cn.ussshenzhou.notenoughbandwidth.stat;

import cn.ussshenzhou.network.StatQuery;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import static cn.ussshenzhou.notenoughbandwidth.stat.SimpleStatManager.*;

public class StatScreen extends Screen {
    private final String client = "Client";
    private final String actual = "Actual Transmission";
    private String actualC = "";
    private final String raw = "Raw Payload";
    private String rawC = "";
    private String ratioC = "";
    private String savedC = "";

    private final String server = "Server";
    private String actualS = "-";
    private String rawS = "-";
    private String ratioS = "-";
    private String savedS = "-";

    private int tick = 0;

    public StatScreen() {
        super(Text.empty());
    }

    @Override
    public void tick() {
        super.tick();
        if (tick % 10 == 0) {
            StatQuery.send();
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
                    + getReadableRatio(LOCAL.inboundBytesBaked().get(), LOCAL.inboundBytesRaw().get())
                    + "                                        "
                    + getReadableRatio(LOCAL.outboundBytesBaked().get(), LOCAL.outboundBytesRaw().get());
            savedC = "Saved                            "
                    + getReadableSaved(LOCAL.inboundBytesBaked().get(), LOCAL.inboundBytesRaw().get())
                    + "                                        "
                    + getReadableSaved(LOCAL.outboundBytesBaked().get(), LOCAL.outboundBytesRaw().get());

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
                    + getReadableRatio(inboundBytesBakedServer, inboundBytesRawServer)
                    + "                                        "
                    + getReadableRatio(outboundBytesBakedServer, outboundBytesRawServer);
            savedS = "Saved                            "
                    + getReadableSaved(inboundBytesBakedServer, inboundBytesRawServer)
                    + "                                        "
                    + getReadableSaved(outboundBytesBakedServer, outboundBytesRawServer);
        }
        tick++;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x80000000);
        context.drawText(textRenderer, Text.literal(client), 10, 10, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(actual), 10, 30, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(actualC), 10, 40, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(raw), 10, 60, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(rawC), 10, 70, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(ratioC), 10, 90, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(savedC), 10, 100, 0xFFFFFF, false);

        context.drawText(textRenderer, Text.literal(server), 10, 130, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(actual), 10, 150, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(actualS), 10, 160, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(raw), 10, 180, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(rawS), 10, 190, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(ratioS), 10, 210, 0xFFFFFF, false);
        context.drawText(textRenderer, Text.literal(savedS), 10, 220, 0xFFFFFF, false);
    }

    private String getReadableSpeed(int bytes) {
        if (bytes < 1000) {
            return bytes + " Bytes/S";
        } else if (bytes < 1000 * 1000) {
            return String.format("%.1f KiB/S", bytes / 1024f);
        } else {
            return String.format("%.2f MiB/S", bytes / (1024f * 1024f));
        }
    }

    private String getReadableSize(long bytes) {
        if (bytes < 1000) {
            return bytes + " Bytes";
        } else if (bytes < 1000L * 1000L) {
            return String.format("%.1f KiB", bytes / 1024d);
        } else if (bytes < 1000L * 1000L * 1000L) {
            return String.format("%.2f MiB", bytes / (1024d * 1024d));
        } else {
            return String.format("%.2f GiB", bytes / (1024d * 1024d * 1024d));
        }
    }

    private String getReadableRatio(long baked, long raw) {
        if (raw <= 0L) {
            return "-";
        }
        return String.format("%.2f%%", 100d * baked / raw);
    }

    private String getReadableSaved(long baked, long raw) {
        if (raw <= 0L || raw <= baked) {
            return raw <= 0L ? "-" : "0.00%";
        }
        return String.format("%.2f%%", 100d * (raw - baked) / raw);
    }
}
