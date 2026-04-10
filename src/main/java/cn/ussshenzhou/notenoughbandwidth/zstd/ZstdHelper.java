package cn.ussshenzhou.notenoughbandwidth.zstd;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

/**
 * @author USS_Shenzhou
 */
public class ZstdHelper {
    private static final boolean ZSTD_AVAILABLE = detectAvailability();

    private static final Cache<Connection, Context> ZSTD_CONTEXT_CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .removalListener((RemovalListener<Connection, Context>) notification -> {
                if (notification.getValue() != null) {
                    notification.getValue().close();
                }
            })
            .build();

    private static boolean detectAvailability() {
        try {
            Class.forName("com.github.luben.zstd.ZstdCompressCtx");
            Class.forName("com.github.luben.zstd.ZstdDecompressCtx");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static boolean isAvailable() {
        return ZSTD_AVAILABLE;
    }

    public static ByteBuf compress(Connection connection, ByteBuf raw) {
        return Unpooled.wrappedBuffer(get(connection).compress(raw.nioBuffer()));
    }

    public static ByteBuf decompress(Connection connection, ByteBuf compressed, int originalSize) {
        if (connection == null) {
            return decompressStateless(compressed, originalSize);
        }
        if (compressed.isDirect()) {
            return Unpooled.wrappedBuffer(get(connection).decompress(compressed.nioBuffer(), originalSize));
        } else {
            var directBuf = Unpooled.directBuffer(compressed.readableBytes());
            compressed.getBytes(compressed.readerIndex(), directBuf);
            var decompressed = Unpooled.wrappedBuffer(get(connection).decompress(directBuf.nioBuffer(), originalSize));
            directBuf.release();
            return decompressed;
        }
    }

    private static ByteBuf decompressStateless(ByteBuf compressed, int originalSize) {
        try (Context ctx = new Context()) {
            if (compressed.isDirect()) {
                return Unpooled.wrappedBuffer(ctx.decompress(compressed.nioBuffer(), originalSize));
            }

            var directBuf = Unpooled.directBuffer(compressed.readableBytes());
            try {
                compressed.getBytes(compressed.readerIndex(), directBuf);
                return Unpooled.wrappedBuffer(ctx.decompress(directBuf.nioBuffer(), originalSize));
            } finally {
                directBuf.release();
            }
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Zstd decompress failed", e);
        }
    }

    /**
     * Stateless compress using a thread-local context (no Connection required).
     */
    public static byte[] compress(byte[] raw) {
        try (Context ctx = new Context()) {
            ByteBuffer result = ctx.compress(ByteBuffer.wrap(raw));
            byte[] out = new byte[result.remaining()];
            result.get(out);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Zstd compress failed", e);
        }
    }

    /**
     * Stateless decompress using a thread-local context (no Connection required).
     */
    public static byte[] decompress(byte[] compressed, int originalSize) {
        try (Context ctx = new Context()) {
            ByteBuffer result = ctx.decompress(ByteBuffer.wrap(compressed), originalSize);
            byte[] out = new byte[result.remaining()];
            result.get(out);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Zstd decompress failed", e);
        }
    }

    private static Context get(Connection connection) {
        ZSTD_CONTEXT_CACHE.asMap().entrySet().removeIf(e -> !e.getKey().isConnected());
        try {
            return ZSTD_CONTEXT_CACHE.get(connection, Context::new);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }
}
