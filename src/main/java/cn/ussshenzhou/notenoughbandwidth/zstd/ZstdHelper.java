package cn.ussshenzhou.notenoughbandwidth.zstd;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.RemovalListener;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.NetworkManager;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.ExecutionException;

public final class ZstdHelper {
    private static final boolean ZSTD_AVAILABLE = detectAvailability();

    private static final Cache<NetworkManager, Context> ZSTD_CONTEXT_CACHE = CacheBuilder.newBuilder()
            .weakKeys()
            .removalListener((RemovalListener<NetworkManager, Context>) notification -> {
                Context value = notification.getValue();
                if (value != null) {
                    value.close();
                }
            })
            .build();

    private static final Cache<NetworkManager, Boolean> CONNECTION_USE_CONTEXT = CacheBuilder.newBuilder()
            .weakKeys()
            .build();

    private ZstdHelper() {
    }

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

    public static ByteBuf compress(NetworkManager connection, ByteBuf raw) {
        return Unpooled.wrappedBuffer(get(connection).compress(raw.nioBuffer()));
    }

    public static ByteBuf decompress(NetworkManager connection, ByteBuf compressed, int originalSize) {
        if (connection == null) {
            return decompressStateless(compressed, originalSize);
        }
        if (compressed.isDirect()) {
            return Unpooled.wrappedBuffer(get(connection).decompress(compressed.nioBuffer(), originalSize));
        }
        ByteBuf direct = Unpooled.directBuffer(compressed.readableBytes());
        try {
            compressed.getBytes(compressed.readerIndex(), direct);
            return Unpooled.wrappedBuffer(get(connection).decompress(direct.nioBuffer(), originalSize));
        } finally {
            direct.release();
        }
    }

    public static byte[] compress(byte[] raw) {
        try (Context context = new Context(false)) {
            ByteBuffer result = context.compress(ByteBuffer.wrap(raw));
            byte[] out = new byte[result.remaining()];
            result.get(out);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Zstd compress failed", e);
        }
    }

    public static byte[] decompress(byte[] compressed, int originalSize) {
        try (Context context = new Context(false)) {
            ByteBuffer result = context.decompress(ByteBuffer.wrap(compressed), originalSize);
            byte[] out = new byte[result.remaining()];
            result.get(out);
            return out;
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Zstd decompress failed", e);
        }
    }

    private static ByteBuf decompressStateless(ByteBuf compressed, int originalSize) {
        try (Context context = new Context(false)) {
            if (compressed.isDirect()) {
                return Unpooled.wrappedBuffer(context.decompress(compressed.nioBuffer(), originalSize));
            }
            ByteBuf direct = Unpooled.directBuffer(compressed.readableBytes());
            try {
                compressed.getBytes(compressed.readerIndex(), direct);
                return Unpooled.wrappedBuffer(context.decompress(direct.nioBuffer(), originalSize));
            } finally {
                direct.release();
            }
        } catch (Exception e) {
            throw new RuntimeException("[NEB] Zstd decompress failed", e);
        }
    }

    private static Context get(NetworkManager connection) {
        ZSTD_CONTEXT_CACHE.asMap().entrySet().removeIf(entry -> entry.getKey() == null || !entry.getKey().isChannelOpen());
        try {
            return ZSTD_CONTEXT_CACHE.get(connection, () -> new Context(shouldUseContext(connection)));
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean shouldUseContext(NetworkManager connection) {
        if (connection == null) {
            return false;
        }
        Boolean cached = CONNECTION_USE_CONTEXT.getIfPresent(connection);
        if (cached != null) {
            return cached;
        }
        boolean shouldUse = true;
        MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        if (server != null) {
            List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
            for (EntityPlayerMP player : players) {
                if (player != null && player.connection != null && player.connection.netManager == connection) {
                    shouldUse = NotEnoughBandwidthLegacyConfig.get().shouldUseZstdContextForPlayer(player.getUniqueID().toString());
                    break;
                }
            }
        }
        CONNECTION_USE_CONTEXT.put(connection, shouldUse);
        return shouldUse;
    }
}
