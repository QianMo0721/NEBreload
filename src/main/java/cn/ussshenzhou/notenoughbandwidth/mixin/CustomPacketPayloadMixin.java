package cn.ussshenzhou.notenoughbandwidth.mixin;

import cn.ussshenzhou.notenoughbandwidth.aggregation.PacketAggregationPacket;
import cn.ussshenzhou.notenoughbandwidth.util.EncodedTrafficStatHelper;
import cn.ussshenzhou.notenoughbandwidth.zstd.ZstdHelper;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * @author USS_Shenzhou
 * Keep mod custom payloads on their original channel path, but allow the payload
 * body itself to be zstd-compressed transparently. This avoids breaking
 * Architectury / FTB style custom networking while still preserving bandwidth
 * savings for large channel packets.
 */
@Mixin({ClientboundCustomPayloadPacket.class, ServerboundCustomPayloadPacket.class})
public class CustomPacketPayloadMixin {

    private static final int MIN_COMPRESS_SIZE = 32;

    @Inject(method = "write(Lnet/minecraft/network/FriendlyByteBuf;)V", at = @At("HEAD"), cancellable = true)
    private void nebwCompressPayloadBody(FriendlyByteBuf targetBuf, CallbackInfo ci) {
        ResourceLocation identifier = neb$getIdentifier();
        FriendlyByteBuf payload = neb$getPayloadData();
        if (identifier == null || payload == null) {
            return;
        }
        try {
            targetBuf.writeResourceLocation(identifier);
            writePayloadBody(targetBuf, identifier, payload);
            ci.cancel();
        } finally {
            payload.release();
        }
    }

    @Redirect(method = "<init>(Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/FriendlyByteBuf;readBytes(I)Lio/netty/buffer/ByteBuf;"))
    private ByteBuf nebwDecompressPayloadBody(FriendlyByteBuf targetBuf, int length) {
        ByteBuf body = targetBuf.readBytes(length);
        if (body.readableBytes() < Long.BYTES + 1) {
            return body;
        }

        FriendlyByteBuf probe = new FriendlyByteBuf(body.retainedDuplicate());
        try {
            long magic = probe.readLong();
            if (magic != EncodedTrafficStatHelper.NEB_CUSTOM_PAYLOAD_MAGIC) {
                return body;
            }

            int rawSize = probe.readVarInt();
            byte[] compressed = new byte[probe.readableBytes()];
            probe.readBytes(compressed);
            byte[] decompressed = ZstdHelper.decompress(compressed, rawSize);
            body.release();
            return Unpooled.wrappedBuffer(decompressed);
        } catch (Exception ignored) {
            return body;
        } finally {
            probe.release();
        }
    }

    @Unique
    private ResourceLocation neb$getIdentifier() {
        Object self = this;
        if (self instanceof ClientboundCustomPayloadPacket clientbound) {
            return clientbound.getIdentifier();
        }
        if (self instanceof ServerboundCustomPayloadPacket serverbound) {
            return serverbound.getIdentifier();
        }
        return null;
    }

    @Unique
    private FriendlyByteBuf neb$getPayloadData() {
        Object self = this;
        if (self instanceof ClientboundCustomPayloadPacket clientbound) {
            return clientbound.getData();
        }
        if (self instanceof ServerboundCustomPayloadPacket serverbound) {
            return serverbound.getData();
        }
        return null;
    }

    @Unique
    private static void writePayloadBody(FriendlyByteBuf targetBuf, ResourceLocation id, FriendlyByteBuf payload) {
        byte[] raw = new byte[payload.readableBytes()];
        payload.getBytes(payload.readerIndex(), raw);
        // Connection 层已经可能把 custom payload 包体压成了
        // NEBZSTD1 + rawSize + compressedData。这里如果再压一次，接收侧只解一层后
        // 仍会残留魔数头，像 minecraft:register 这种会直接把残留字节当频道名解析。
        if (isAlreadyCompressed(raw)) {
            targetBuf.writeBytes(raw);
            return;
        }
        if (!shouldCompress(id) || raw.length < MIN_COMPRESS_SIZE) {
            targetBuf.writeBytes(raw);
            return;
        }

        byte[] compressed = ZstdHelper.compress(raw);
        if (!isCompressionWorthwhile(raw.length, compressed.length)) {
            targetBuf.writeBytes(raw);
            return;
        }

        targetBuf.writeLong(EncodedTrafficStatHelper.NEB_CUSTOM_PAYLOAD_MAGIC);
        targetBuf.writeVarInt(raw.length);
        targetBuf.writeBytes(compressed);
    }

    @Unique
    private static boolean shouldCompress(ResourceLocation id) {
        return id != null
                && ZstdHelper.isAvailable()
                && !PacketAggregationPacket.TYPE.equals(id);
    }

    @Unique
    private static boolean isAlreadyCompressed(byte[] raw) {
        if (raw.length < Long.BYTES + 1) {
            return false;
        }
        long magic = 0L;
        for (int i = 0; i < Long.BYTES; i++) {
            magic = (magic << 8) | (raw[i] & 0xFFL);
        }
        return magic == EncodedTrafficStatHelper.NEB_CUSTOM_PAYLOAD_MAGIC;
    }

    @Unique
    private static boolean isCompressionWorthwhile(int rawLength, int compressedLength) {
        return compressedLength + Long.BYTES + 5 < rawLength;
    }
}
