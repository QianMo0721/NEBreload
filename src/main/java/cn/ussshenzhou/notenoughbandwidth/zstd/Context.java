package cn.ussshenzhou.notenoughbandwidth.zstd;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * @author USS_Shenzhou
 */
public class Context implements Closeable {
    private final ZstdCompressCtx compressCtx;
    private final ZstdDecompressCtx decompressCtx;

    public Context() {
        compressCtx = new ZstdCompressCtx();
        compressCtx.setLevel(3);
        compressCtx.setContentSize(false);
        compressCtx.setMagicless(true);
        compressCtx.setWindowLog(NotEnoughBandwidthLegacyConfig.get().getContextLevel());
        decompressCtx = new ZstdDecompressCtx();
        decompressCtx.setMagicless(true);
    }

    public ByteBuffer compress(ByteBuffer src) {
        return compressCtx.compress(ensureDirect(src));
    }

    public ByteBuffer decompress(ByteBuffer src, int originalSize) {
        ByteBuffer dst = ByteBuffer.allocateDirect(originalSize);
        decompressCtx.decompress(dst, ensureDirect(src));
        dst.flip();
        return dst;
    }

    private static ByteBuffer ensureDirect(ByteBuffer src) {
        ByteBuffer slice = src.slice();
        if (slice.isDirect()) {
            return slice;
        }
        ByteBuffer direct = ByteBuffer.allocateDirect(slice.remaining());
        direct.put(slice);
        direct.flip();
        return direct;
    }

    @Override
    public void close() {
        compressCtx.close();
        decompressCtx.close();
    }
}
