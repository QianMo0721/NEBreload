package cn.ussshenzhou.notenoughbandwidth.indextype;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Tuple;

import javax.annotation.Nullable;

/**
 * Forge 1.20.1 adaptation of the NeoForge 1.21.1 payload prefix codec.
 * 依旧用neoforge打法
 */
public class CustomPacketPrefixHelper {
    public static void write(@Nullable net.minecraft.network.Connection connection, ResourceLocation type, FriendlyByteBuf buf) {
        // 优先使用“每连接索引表”压缩 type 前缀；
        // 当连接上下文不可用或该 type 未进入当前连接协商集时，安全回退到原始 RL。
        if (connection != null && NamespaceIndexManager.ready(connection) && NamespaceIndexManager.contains(connection, type)) {
            Tuple<Integer, Integer> index = NamespaceIndexManager.getCheckedIndex(connection, type);
            buf.writeVarInt(index.getA());
            buf.writeVarInt(index.getB());
            return;
        }
        buf.writeByte(0);
        buf.writeResourceLocation(type);
    }

    @Deprecated
    public static void write(ResourceLocation type, FriendlyByteBuf buf) {
        write(null, type, buf);
    }

    @Nullable
    public static ResourceLocation read(FriendlyByteBuf buf) {
        return readInfo(buf).type();
    }

    @Nullable
    public static ResourceLocation getType(FriendlyByteBuf buf) {
        return readInfo(buf).type();
    }

    public static DecodedTypeInfo readInfo(@Nullable net.minecraft.network.Connection connection, FriendlyByteBuf buf) {
        byte firstByte = buf.getByte(buf.readerIndex());
        if (firstByte == 0) {
            buf.readVarInt();
            return new DecodedTypeInfo(buf.readResourceLocation(), false, true);
        }
        int namespaceIndex = buf.readVarInt();
        int pathIndex = buf.readVarInt();
        ResourceLocation id = NamespaceIndexManager.getIdentifierOrNull(connection, namespaceIndex, pathIndex);
        return new DecodedTypeInfo(id, true, id != null);
    }

    @Deprecated
    public static DecodedTypeInfo readInfo(FriendlyByteBuf buf) {
        return readInfo(null, buf);
    }

    public record DecodedTypeInfo(@Nullable ResourceLocation type, boolean indexed, boolean valid) {
    }
}
