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
    private static final ThreadLocal<CustomPacketPrefixHelper> INSTANCES = ThreadLocal.withInitial(CustomPacketPrefixHelper::new);

    private int prefix = 0;
    private ResourceLocation type = null;
    private boolean indexed = false;

    private CustomPacketPrefixHelper() {
    }

    public static CustomPacketPrefixHelper get() {
        var instance = INSTANCES.get();
        instance.prefix = 0;
        instance.type = null;
        instance.indexed = false;
        return instance;
    }

    public CustomPacketPrefixHelper index(ResourceLocation type) {
        int index = NamespaceIndexManager.getNebIndex(type);
        if (index == 0) {
            this.type = type;
            return this;
        }
        this.type = type;
        prefix |= index;
        indexed = true;
        return this;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void save(FriendlyByteBuf buf) {
        if (prefix >>> 31 == 0) {
            buf.writeByte(prefix >>> 24);
            buf.writeResourceLocation(type);
        }
        if (prefix >>> 31 == 1) {
            if ((prefix >>> 30 & 1) == 1) {
                buf.writeMedium(prefix >>> 8);
            } else {
                buf.writeInt(prefix);
            }
        }
    }

    @Nullable
    public static ResourceLocation getType(FriendlyByteBuf buf) {
        return read(buf).type();
    }

    public static DecodedTypeInfo read(FriendlyByteBuf buf) {
        int fixed = buf.readUnsignedByte() & 0xff;
        if ((fixed & 0x80) == 0) {
            return new DecodedTypeInfo(buf.readResourceLocation(), false);
        } else {
            // Header bits:
            // 10xxxxxx = indexed, not tight  -> remaining payload is 3 bytes
            // 11xxxxxx = indexed, tight      -> remaining payload is 2 bytes
            if ((fixed & 0x40) == 0) {
                return new DecodedTypeInfo(NamespaceIndexManager.getIdentifier(buf.readUnsignedMedium(), false), true);
            } else {
                return new DecodedTypeInfo(NamespaceIndexManager.getIdentifier(buf.readUnsignedShort(), true), true);
            }
        }
    }

    public record DecodedTypeInfo(@Nullable ResourceLocation type, boolean indexed) {
    }
}
