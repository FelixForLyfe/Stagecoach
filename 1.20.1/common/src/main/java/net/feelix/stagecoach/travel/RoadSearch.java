package net.feelix.stagecoach.travel;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;

/** A flood fill along road blocks from one stop, run a budget at a time, noting every other stop it comes near. */
public final class RoadSearch {
    public static final int ROAD = 1;
    public static final int NOT_ROAD = 0;
    /** The block's chunk isn't loaded and may not be loaded this step: the search pauses there. */
    public static final int NOT_LOADED = -1;

    /** A road this long (blocks visited) is given up on, so a gravel beach can't run forever. */
    public static final int MAX_VISITED = 2_000_000;
    private static final int TARGET_DY = 4;

    @FunctionalInterface
    public interface Ground {
        int at(int x, int y, int z);
    }

    private final int reach;
    private final LongOpenHashSet visited = new LongOpenHashSet();
    private final LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
    /** Targets by 16x16 column, so each block visited costs one lookup however many stops there are. */
    private final Long2ObjectOpenHashMap<LongArrayList> buckets = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<int[]> targets = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet reached = new LongOpenHashSet();
    private boolean done;

    /** {@code reach}: how far from a stop (horizontally) its road may start; at most 16. */
    public RoadSearch(int reach) {
        this.reach = reach;
    }

    public void addTarget(long id, int x, int y, int z) {
        targets.put(id, new int[] {x, y, z});
        for (int bx = (x - reach) >> 4; bx <= (x + reach) >> 4; bx++) {
            for (int bz = (z - reach) >> 4; bz <= (z + reach) >> 4; bz++) {
                buckets.computeIfAbsent(bucket(bx, bz), k -> new LongArrayList()).add(id);
            }
        }
    }

    /** Every road block around the stop the search starts from. */
    public void seed(Ground ground, int x, int y, int z) {
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dz = -reach; dz <= reach; dz++) {
                for (int dy = -3; dy <= 1; dy++) visit(ground, x + dx, y + dy, z + dz);
            }
        }
        if (queue.isEmpty()) done = true;
    }

    /** Visits up to {@code budget} blocks; true once there is nothing left to visit. */
    public boolean step(Ground ground, int budget) {
        while (!done && budget-- > 0) {
            if (queue.isEmpty() || visited.size() > MAX_VISITED) {
                done = true;
                break;
            }
            long n = queue.dequeueLong();
            int x = x(n), y = y(n), z = z(n);
            note(x, y, z);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (dx == 0 && dz == 0) continue;
                    for (int dy = -1; dy <= 1; dy++) {
                        if (visit(ground, x + dx, y + dy, z + dz) == NOT_LOADED) {
                            queue.enqueueFirst(n);
                            return false;
                        }
                    }
                }
            }
        }
        return done;
    }

    public boolean isDone() {
        return done;
    }

    public boolean reached(long id) {
        return reached.contains(id);
    }

    public LongSet reachedIds() {
        return reached;
    }

    private int visit(Ground ground, int x, int y, int z) {
        long k = pack(x, y, z);
        if (visited.contains(k)) return NOT_ROAD;
        int g = ground.at(x, y, z);
        if (g == ROAD) {
            visited.add(k);
            queue.enqueue(k);
        }
        return g;
    }

    private void note(int x, int y, int z) {
        LongArrayList ids = buckets.get(bucket(x >> 4, z >> 4));
        if (ids == null) return;
        for (int i = 0; i < ids.size(); i++) {
            long id = ids.getLong(i);
            int[] t = targets.get(id);
            if (Math.abs(x - t[0]) <= reach && Math.abs(z - t[2]) <= reach && Math.abs(y - t[1]) <= TARGET_DY) reached.add(id);
        }
    }

    private static long bucket(int bx, int bz) {
        return ((long) bx << 32) ^ (bz & 0xFFFFFFFFL);
    }

    // x and z in 26 signed bits each (the world border is 30M), y in 12
    static long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    static int x(long p) {
        return (int) (p >> 38) << 6 >> 6;
    }

    static int z(long p) {
        return (int) (p >> 12 & 0x3FFFFFF) << 6 >> 6;
    }

    static int y(long p) {
        return (int) (p & 0xFFF) << 20 >> 20;
    }
}
