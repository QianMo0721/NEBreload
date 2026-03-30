package cn.ussshenzhou.notenoughbandwidth.indextype;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;

/**
 * Instead of vanilla full ResourceLocation identifier in bytebuf,
 * we here use such protocol to avoid putting a huge ResourceLocation into bytebuf.
 * <p>
 * <h4>Fixed 8 bits header</h4>
 * <pre>
 * ┌------------- 1 byte (8 bits) ---------------┐
 * │               function flags                │
 * ├---┬---┬-------------------------------------┤
 * │ i │ t │      reserved (6 bits)              │
 * └---┴---┴-------------------------------------┘
 *
 * i = indexed (1 bit)
 * t = tight_indexed (1 bit, only valid if i=1)
 * reserved = 6 bits (for future use)
 *
 * </pre>
 *
 * <h4>Indexed packet type</h4>
 * <pre>
 * - If i=0 (not indexed):
 *
 *   ┌---------------- N bytes ----------------
 *   │ ResourceLocation (packet type) in UTF-8
 *   └-----------------------------------------
 *
 * - If i=1 and t=0 (indexed, NOT tight):
 *
 *   ┌-------- 1 byte ---------┬-------- 1 byte --------┬-------- 1 byte --------┐
 *   ┌------------- 12 bits ---------------┬-------------- 12 bits --------------┐
 *   │    namespace-id (capacity 4096)     │       path-id (capacity 4096)       │
 *   └-------------------------------------┴-------------------------------------┘
 *
 * - If i=1 and t=1 (indexed, tight):
 *
 *   ┌--------- 1 byte ----------┬--------- 1 byte ---------┐
 *   ┌--------- 8 bits ----------┬--------- 8 bits ---------┐
 *   │namespace-id (capacity 256)│  path-id (capacity 256)  │
 *   └---------------------------┴--------------------------┘
 *
 * </pre>
 *
 * @author USS_Shenzhou
 */
public class CustomPacketPrefixHelper {

    public static void writeType(FriendlyByteBuf buf, ResourceLocation type) {
        if (!NamespaceIndexManager.isInitialized()) {
            // fallback: write full ResourceLocation
            buf.writeByte(0x00);
            buf.writeResourceLocation(type);
            return;
        }

        String namespace = type.getNamespace();
        String path = type.getPath();
        int nsIdx = NamespaceIndexManager.getNamespaceIndex(namespace);
        int pathIdx = nsIdx >= 0 ? NamespaceIndexManager.getPathIndex(nsIdx, path) : -1;

        if (nsIdx < 0 || pathIdx < 0) {
            // fallback
            buf.writeByte(0x00);
            buf.writeResourceLocation(type);
            return;
        }

        if (nsIdx < 16 && pathIdx < 16) {
            // tight: 1 byte prefix + 2 bytes (4+4 bits each)
            // Actually use: i=1, t=1 => 0xC0 flag
            // pack nsIdx (4 bits) + pathIdx (4 bits) into 1 byte
            // But we need to handle larger cases, use 2-byte tight form:
            // tight: nsIdx ≤ 255, pathIdx ≤ 255
            buf.writeByte(0x80 | 0x40); // i=1, t=1
            buf.writeByte(nsIdx & 0xFF);
            buf.writeByte(pathIdx & 0xFF);
        } else {
            // normal indexed: 3 bytes for 12+12 bits
            int combined = ((nsIdx & 0xFFF) << 12) | (pathIdx & 0xFFF);
            buf.writeByte(0x80); // i=1, t=0
            buf.writeMedium(combined);
        }
    }

    @Nullable
    public static ResourceLocation getType(FriendlyByteBuf buf) {
        int fixed = buf.readUnsignedByte() & 0xff;
        if ((fixed & 0x80) == 0) {
            // not indexed, read full ResourceLocation
            return buf.readResourceLocation();
        } else {
            if ((fixed & 0x40) != 0) {
                // tight indexed
                int nsIdx = buf.readUnsignedByte();
                int pathIdx = buf.readUnsignedByte();
                return NamespaceIndexManager.getIdentifier((nsIdx << 8) | pathIdx, true);
            } else {
                // normal indexed: 3 bytes = 24 bits = 12+12
                int combined = buf.readUnsignedMedium();
                return NamespaceIndexManager.getIdentifier(combined, false);
            }
        }
    }
}
