package cn.ussshenzhou.notenoughbandwidth.chunk;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.world.level.ChunkPos;

import java.util.function.Consumer;

/**
 * 1.20.1-compatible backport of NeoForge's `ChunkTrackingView` semantics.
 */
public interface ChunkTrackingViewCompat {
    ChunkTrackingViewCompat EMPTY = new ChunkTrackingViewCompat() {
        @Override
        public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
            return false;
        }

        @Override
        public void forEach(Consumer<ChunkPos> action) {
        }
    };

    static ChunkTrackingViewCompat of(ChunkPos center, int viewDistance) {
        return new Positioned(center, viewDistance);
    }

    static void difference(ChunkTrackingViewCompat oldChunkTrackingView, ChunkTrackingViewCompat newChunkTrackingView,
                           Consumer<ChunkPos> chunkMarker, Consumer<ChunkPos> chunkDropper) {
        if (!oldChunkTrackingView.equals(newChunkTrackingView)) {
            if (oldChunkTrackingView instanceof Positioned oldPositioned
                    && newChunkTrackingView instanceof Positioned newPositioned
                    && oldPositioned.squareIntersects(newPositioned)) {
                int minX = Math.min(oldPositioned.minX(), newPositioned.minX());
                int minZ = Math.min(oldPositioned.minZ(), newPositioned.minZ());
                int maxX = Math.max(oldPositioned.maxX(), newPositioned.maxX());
                int maxZ = Math.max(oldPositioned.maxZ(), newPositioned.maxZ());

                for (int x = minX; x <= maxX; x++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        boolean oldContains = oldPositioned.contains(x, z);
                        boolean newContains = newPositioned.contains(x, z);
                        if (oldContains != newContains) {
                            if (newContains) {
                                chunkMarker.accept(new ChunkPos(x, z));
                            } else {
                                chunkDropper.accept(new ChunkPos(x, z));
                            }
                        }
                    }
                }
                return;
            }

            oldChunkTrackingView.forEach(chunkDropper);
            newChunkTrackingView.forEach(chunkMarker);
        }
    }

    default boolean contains(ChunkPos chunkPos) {
        return this.contains(chunkPos.x, chunkPos.z);
    }

    default boolean contains(int x, int z) {
        return this.contains(x, z, true);
    }

    boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder);

    void forEach(Consumer<ChunkPos> action);

    static boolean isWithinDistance(int centerX, int centerZ, int viewDistance, int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
        int dx = Math.max(0, Math.abs(x - centerX) - 1);
        int dz = Math.max(0, Math.abs(z - centerZ) - 1);
        long outer = Math.max(0, Math.max(dx, dz) - (includeOuterChunksAdjacentToViewBorder ? 1 : 0));
        long inner = Math.min(dx, dz);
        long distSq = inner * inner + outer * outer;
        int viewDistanceSq = viewDistance * viewDistance;
        return distSq < (long) viewDistanceSq;
    }

    record Positioned(ChunkPos center, int viewDistance) implements ChunkTrackingViewCompat {
        int minX() {
            return this.center.x - this.viewDistance - 1;
        }

        int minZ() {
            return this.center.z - this.viewDistance - 1;
        }

        int maxX() {
            return this.center.x + this.viewDistance + 1;
        }

        int maxZ() {
            return this.center.z + this.viewDistance + 1;
        }

        @VisibleForTesting
        protected boolean squareIntersects(Positioned other) {
            return this.minX() <= other.maxX() && this.maxX() >= other.minX()
                    && this.minZ() <= other.maxZ() && this.maxZ() >= other.minZ();
        }

        @Override
        public boolean contains(int x, int z, boolean includeOuterChunksAdjacentToViewBorder) {
            return ChunkTrackingViewCompat.isWithinDistance(this.center.x, this.center.z, this.viewDistance, x, z, includeOuterChunksAdjacentToViewBorder);
        }

        @Override
        public void forEach(Consumer<ChunkPos> action) {
            for (int x = this.minX(); x <= this.maxX(); x++) {
                for (int z = this.minZ(); z <= this.maxZ(); z++) {
                    if (this.contains(x, z)) {
                        action.accept(new ChunkPos(x, z));
                    }
                }
            }
        }
    }
}
