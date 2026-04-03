package cn.ussshenzhou.notenoughbandwidth.indextype;

import net.minecraft.util.Identifier;
import net.minecraft.network.PacketByteBuf;
import org.jetbrains.annotations.Nullable;

/**
 * 使用紧凑前缀编码自定义 payload 的 Identifier，减少包头开销。
 */
public class CustomPacketPrefixHelper {
    private static final ThreadLocal<CustomPacketPrefixHelper> INSTANCES = ThreadLocal.withInitial(CustomPacketPrefixHelper::new);

    private int prefix = 0;
    private Identifier type = null;

    private CustomPacketPrefixHelper() {
    }

    public static CustomPacketPrefixHelper get() {
        var instance = INSTANCES.get();
        instance.prefix = 0;
        instance.type = null;
        return instance;
    }

    public CustomPacketPrefixHelper index(Identifier type) {
        int index = NamespaceIndexManager.getNebIndex(type);
        this.type = type;
        if (index != 0) {
            prefix |= index;
        }
        return this;
    }

    public void save(PacketByteBuf buf) {
        if (prefix >>> 31 == 0) {
            buf.writeByte(prefix >>> 24);
            buf.writeIdentifier(type);
            return;
        }
        if ((prefix >>> 30 & 1) == 1) {
            buf.writeMedium(prefix >>> 8);
        } else {
            buf.writeInt(prefix);
        }
    }

    @Nullable
    public static Identifier getType(PacketByteBuf buf) {
        int fixed = buf.readUnsignedByte() & 0xff;
        if (fixed >>> 7 == 0) {
            return buf.readIdentifier();
        }
        if (fixed >>> 6 == 0) {
            return NamespaceIndexManager.getIdentifier(buf.readUnsignedMedium(), false);
        }
        return NamespaceIndexManager.getIdentifier(buf.readUnsignedShort(), true);
    }
}
