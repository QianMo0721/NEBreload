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
    public static void write(ResourceLocation type, FriendlyByteBuf buf) {
        if (type != null && NamespaceIndexManager.contains(type)) {
            Tuple<Integer, Integer> index = NamespaceIndexManager.getCheckedIndex(type);
            buf.writeVarInt(index.getA());
            buf.writeVarInt(index.getB());
        } else {
            buf.writeByte(0);
            buf.writeResourceLocation(type);
        }
    }

    @Nullable
    public static ResourceLocation read(FriendlyByteBuf buf) {
        return readInfo(buf).type();
    }

    @Nullable
    public static ResourceLocation getType(FriendlyByteBuf buf) {
        return readInfo(buf).type();
    }

    public static DecodedTypeInfo readInfo(FriendlyByteBuf buf) {
        byte firstByte = buf.getByte(buf.readerIndex());
        if (firstByte == 0) {
            buf.readVarInt();
            return new DecodedTypeInfo(buf.readResourceLocation(), false);
        } else {
            return new DecodedTypeInfo(NamespaceIndexManager.getIdentifier(buf.readVarInt(), buf.readVarInt()), true);
        }
    }

    public record DecodedTypeInfo(@Nullable ResourceLocation type, boolean indexed) {
    }
}
