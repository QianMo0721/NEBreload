package cn.ussshenzhou.notenoughbandwidth.indextype;

import net.minecraft.network.NetworkManager;
import net.minecraft.network.PacketBuffer;

import javax.annotation.Nullable;

public final class CustomPacketPrefixHelper {
    private CustomPacketPrefixHelper() {
    }

    public static void write(@Nullable NetworkManager connection, String type, PacketBuffer buf) {
        if (connection != null && NamespaceIndexManager.ready(connection) && NamespaceIndexManager.contains(connection, type)) {
            ConnectionIndexTable.IndexPair index = NamespaceIndexManager.getCheckedIndex(connection, type);
            buf.writeVarInt(index.namespaceIndex());
            buf.writeVarInt(index.pathIndex());
            return;
        }
        buf.writeByte(0);
        buf.writeString(type);
    }

    @Nullable
    public static String read(@Nullable NetworkManager connection, PacketBuffer buf) {
        return readInfo(connection, buf).type();
    }

    public static DecodedTypeInfo readInfo(@Nullable NetworkManager connection, PacketBuffer buf) {
        int firstByte = buf.getByte(buf.readerIndex()) & 0xFF;
        if (firstByte == 0) {
            buf.readVarInt();
            return new DecodedTypeInfo(buf.readString(32767), false, true);
        }
        int namespaceIndex = buf.readVarInt();
        int pathIndex = buf.readVarInt();
        String id = NamespaceIndexManager.getIdentifierOrNull(connection, namespaceIndex, pathIndex);
        return new DecodedTypeInfo(id, true, id != null);
    }

    public static final class DecodedTypeInfo {
        private final String type;
        private final boolean indexed;
        private final boolean valid;

        public DecodedTypeInfo(@Nullable String type, boolean indexed, boolean valid) {
            this.type = type;
            this.indexed = indexed;
            this.valid = valid;
        }

        @Nullable
        public String type() {
            return type;
        }

        public boolean indexed() {
            return indexed;
        }

        public boolean valid() {
            return valid;
        }
    }
}
