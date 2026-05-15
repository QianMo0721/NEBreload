package cn.ussshenzhou.notenoughbandwidth.zstd;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraftforge.server.ServerLifecycleHooks;

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
    private static final Cache<Connection, Boolean> CONNECTION_USE_CONTEXT = CacheBuilder.newBuilder()
            .weakKeys()
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

    public static void clearCache(Connection connection) {
        ZSTD_CONTEXT_CACHE.invalidate(connection);
        CONNECTION_USE_CONTEXT.invalidate(connection);
    }

    public static ByteBuf compress(Connection connection, ByteBuf raw) {
        try {
            ByteBuffer compressed = get(connection).compress(raw.nioBuffer());
            return Unpooled.wrappedBuffer(compressed);
        } catch (Exception e) {
            if (connection != null) {
                clearCache(connection);
                try {
                    ByteBuffer compressed = get(connection).compress(raw.nioBuffer());
                    return Unpooled.wrappedBuffer(compressed);
                } catch (Exception retryErr) {
                    throw new RuntimeException("[NEB] Zstd compress failed even after cache clear", retryErr);
                }
            }
            throw new RuntimeException("[NEB] Zstd compress failed", e);
        }
    }

    public static ByteBuf decompress(Connection connection, ByteBuf compressed, int originalSize) {
        if (connection == null) {
            return decompressStateless(compressed, originalSize);
        }

        try {
            if (compressed.isDirect()) {
                return Unpooled.wrappedBuffer(get(connection).decompress(compressed.nioBuffer(), originalSize));
            } else {
                var directBuf = Unpooled.directBuffer(compressed.readableBytes());
                ByteBuffer decompressed = null;
                try {
                    directBuf.writeBytes(compressed, compressed.readerIndex(), compressed.readableBytes());
                    decompressed = get(connection).decompress(directBuf.nioBuffer(), originalSize);
                    return Unpooled.wrappedBuffer(decompressed);
                } finally {
                    directBuf.release();
                }
            }
        } catch (Exception e) {
            clearCache(connection);
            try {
                if (compressed.isDirect()) {
                    return Unpooled.wrappedBuffer(get(connection).decompress(compressed.nioBuffer(), originalSize));
                } else {
                    var directBuf = Unpooled.directBuffer(compressed.readableBytes());
                    ByteBuffer decompressed = null;
                    try {
                        directBuf.writeBytes(compressed, compressed.readerIndex(), compressed.readableBytes());
                        decompressed = get(connection).decompress(directBuf.nioBuffer(), originalSize);
                        return Unpooled.wrappedBuffer(decompressed);
                    } finally {
                        directBuf.release();
                    }
                }
            } catch (Exception retryErr) {
                throw new RuntimeException("[NEB] Zstd decompress failed even after cache clear", retryErr);
            }
        }
    }

    private static ByteBuf decompressStateless(ByteBuf compressed, int originalSize) {
        try (Context ctx = new Context(false)) {
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
        try (Context ctx = new Context(false)) {
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
        try (Context ctx = new Context(false)) {
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
            return ZSTD_CONTEXT_CACHE.get(connection, () -> new Context(shouldUseContext(connection)));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean shouldUseContext(Connection connection) {
        if (connection.getReceiving() == PacketFlow.CLIENTBOUND) {
            return true;
        }
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return true;
        }
        Boolean cached = CONNECTION_USE_CONTEXT.getIfPresent(connection);
        if (cached != null) {
            return cached;
        }
        var player = server.getPlayerList().getPlayers()
                .stream()
                .filter(serverPlayer -> serverPlayer.connection.connection.equals(connection))
                .findFirst()
                .orElse(null);
        boolean shouldUseContext = true;
        if (player != null) {
            shouldUseContext = NotEnoughBandwidthLegacyConfig.get().shouldUseZstdContextForPlayer(player.getUUID().toString());
        }
        CONNECTION_USE_CONTEXT.put(connection, shouldUseContext);
        return shouldUseContext;
    }
}
