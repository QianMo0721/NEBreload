package cn.ussshenzhou.notenoughbandwidth.zstd;

import cn.ussshenzhou.notenoughbandwidth.NotEnoughBandwidthLegacyConfig;
import com.github.luben.zstd.EndDirective;
import com.github.luben.zstd.Zstd;
import com.github.luben.zstd.ZstdCompressCtx;
import com.github.luben.zstd.ZstdDecompressCtx;

import java.io.Closeable;
import java.nio.ByteBuffer;

/**
 * @author USS_Shenzhou
 */
public class Context implements Closeable {
    private static final boolean GRAALVM = isGraalVm();

    private final ZstdCompressCtx compressCtx;
    private final ZstdDecompressCtx decompressCtx;
    private final boolean useContext;

    public Context() {
        this(true);
    }

    public Context(boolean useContext) {
        compressCtx = new ZstdCompressCtx();
        compressCtx.setLevel(NotEnoughBandwidthLegacyConfig.get().getZstdCompressionLevel());
        compressCtx.setContentSize(false);
        compressCtx.setMagicless(true);
        compressCtx.setWindowLog(NotEnoughBandwidthLegacyConfig.get().getContextLevel());
        decompressCtx = new ZstdDecompressCtx();
        decompressCtx.setMagicless(true);
        this.useContext = useContext;
    }

    public ByteBuffer compress(ByteBuffer raw) {
        ByteBuffer directRaw = ensureDirect(raw);
        if (useContext && !GRAALVM) {
            int maxCompressedSize = (int) Zstd.compressBound(directRaw.remaining());
            ByteBuffer compressed = ByteBuffer.allocateDirect(maxCompressedSize);
            compressCtx.compressDirectByteBufferStream(compressed, directRaw, EndDirective.FLUSH);
            compressed.flip();
            return compressed;
        }
        return compressCtx.compress(directRaw);
    }

    public ByteBuffer decompress(ByteBuffer compressed, int originalSize) {
        ByteBuffer directCompressed = ensureDirect(compressed);
        if (GRAALVM) {
            return decompressCtx.decompress(directCompressed, originalSize);
        }
        ByteBuffer decompressed = ByteBuffer.allocateDirect(originalSize);
        decompressCtx.decompressDirectByteBufferStream(decompressed, directCompressed);
        decompressed.flip();
        return decompressed;
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

    private static boolean isGraalVm() {
        String vmName = System.getProperty("java.vm.name", "");
        String vmVendor = System.getProperty("java.vm.vendor", "");
        String runtimeName = System.getProperty("java.runtime.name", "");
        String combined = (vmName + " " + vmVendor + " " + runtimeName).toLowerCase(java.util.Locale.ROOT);
        return combined.contains("graalvm");
    }

    @Override
    public void close() {
        compressCtx.close();
        decompressCtx.close();
    }
}
