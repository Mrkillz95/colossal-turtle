package net.jj.colossalturtle.walk;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.List;
import net.jj.colossalturtle.ColossalTurtle;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * The dents his feet leave in the world: a bowl that fills with water, claw marks at the front and flattened trees
 * all round. Most of them land far from anyone, so each chunk gets its footprints pressed in when it next loads
 * (it remembers the last one it took).
 */
public final class Footprints {
    public record Print(int id, int x, int z, int r, int water, float yaw) {}

    private static final int REGION = 9;
    private static final int COLUMNS_PER_TICK = 4096;

    private final List<Print> prints = new ArrayList<>();
    private final Long2ObjectOpenHashMap<IntArrayList> byRegion = new Long2ObjectOpenHashMap<>();
    private final LongLinkedOpenHashSet todo = new LongLinkedOpenHashSet();
    private int nextId;

    public Print last() {
        return prints.isEmpty() ? null : prints.get(prints.size() - 1);
    }

    public Print first() {
        return prints.isEmpty() ? null : prints.get(0);
    }

    public int pending() {
        return todo.size();
    }

    public int count() {
        return prints.size();
    }

    public void add(ServerLevel level, int x, int z, int r, int water, float yaw) {
        Print p = new Print(nextId++, x, z, r, water, yaw);
        index(p);
        int reach = (int) (r * 1.3) + 16;
        for (int cx = (x - reach) >> 4; cx <= (x + reach) >> 4; cx++)
            for (int cz = (z - reach) >> 4; cz <= (z + reach) >> 4; cz++)
                if (level.getChunkSource().getChunkNow(cx, cz) != null) todo.add(ChunkPos.asLong(cx, cz));
    }

    private void index(Print p) {
        prints.add(p);
        int reach = (int) (p.r * 1.3) + 16;
        for (int rx = (p.x - reach) >> REGION; rx <= (p.x + reach) >> REGION; rx++)
            for (int rz = (p.z - reach) >> REGION; rz <= (p.z + reach) >> REGION; rz++)
                byRegion.computeIfAbsent(ChunkPos.asLong(rx, rz), k -> new IntArrayList()).add(prints.size() - 1);
    }

    public void onChunkLoad(LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        IntArrayList list = byRegion.get(ChunkPos.asLong(cp.x >> (REGION - 4), cp.z >> (REGION - 4)));
        if (list == null) return;
        int done = done(chunk);
        for (int i = list.size() - 1; i >= 0; i--)
            if (prints.get(list.getInt(i)).id > done) {
                todo.add(cp.toLong());
                return;
            }
    }

    private static int done(LevelChunk chunk) {
        Integer d = chunk.getAttached(ColossalTurtle.PRINTS_DONE);
        return d == null ? -1 : d;
    }

    public void work(ServerLevel level) {
        int budget = COLUMNS_PER_TICK;
        while (budget > 0 && !todo.isEmpty()) {
            long key = todo.removeFirstLong();
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk == null) continue;
            budget -= press(level, chunk);
        }
    }

    private int press(ServerLevel level, LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        IntArrayList list = byRegion.get(ChunkPos.asLong(cp.x >> (REGION - 4), cp.z >> (REGION - 4)));
        int done = done(chunk), last = done, cost = 1;
        if (list != null) {
            for (int i = 0; i < list.size(); i++) {
                Print p = prints.get(list.getInt(i));
                if (p.id <= done) continue;
                last = Math.max(last, p.id);
                int reach = (int) (p.r * 1.3) + 16;
                if (cp.getMaxBlockX() < p.x - reach || cp.getMinBlockX() > p.x + reach
                        || cp.getMaxBlockZ() < p.z - reach || cp.getMinBlockZ() > p.z + reach) continue;
                for (int x = cp.getMinBlockX(); x <= cp.getMaxBlockX(); x++)
                    for (int z = cp.getMinBlockZ(); z <= cp.getMaxBlockZ(); z++) press(level, chunk, p, x, z);
                cost += 256;
            }
        }
        if (last != done) {
            chunk.setAttached(ColossalTurtle.PRINTS_DONE, last);
            chunk.setUnsaved(true);
        }
        return cost;
    }

    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static void press(ServerLevel level, LevelChunk chunk, Print p, int x, int z) {
        double dx = x - p.x, dz = z - p.z, d = Math.sqrt(dx * dx + dz * dz);
        double depth = -1;
        if (d <= p.r) depth = 1 + 9 * (1 - (d / p.r) * (d / p.r));
        // four claw marks dug in at the front
        double fx = Math.cos(p.yaw), fz = Math.sin(p.yaw);
        for (int k = -2; k <= 2; k++) {
            if (k == 0) continue;
            double a = p.yaw + k * 0.22, cx = p.x + Math.cos(a) * p.r * 0.98, cz = p.z + Math.sin(a) * p.r * 0.98;
            double along = (x - cx) * fx + (z - cz) * fz, side = -(x - cx) * fz + (z - cz) * fx;
            if (along > -p.r * 0.1 && along < p.r * 0.18 && Math.abs(side) < 6) depth = Math.max(depth, 7);
        }
        int lx = x & 15, lz = z & 15;
        int s = chunk.getHeight(Heightmap.Types.OCEAN_FLOOR, lx, lz);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (depth > 0) {
            int floor = p.water - (int) Math.round(depth);
            clearAbove(level, pos, x, z, s);
            if (floor >= s) return;
            boolean sea = level.getFluidState(pos.set(x, s + 1, z)).is(Fluids.WATER);
            for (int y = s; y > floor; y--) {
                BlockState to = sea || y <= Math.min(p.water, s) ? Blocks.WATER.defaultBlockState() : Blocks.AIR.defaultBlockState();
                level.setBlock(pos.set(x, y, z), to, FLAGS);
            }
            level.setBlock(pos.set(x, floor, z), d < p.r * 0.6 ? Blocks.MUD.defaultBlockState() : Blocks.COARSE_DIRT.defaultBlockState(), FLAGS);
        } else if (d < p.r * 1.3) {
            // the trees round the edge are knocked flat
            clearAbove(level, pos, x, z, s);
            if (Mth.floor(Math.abs(Math.sin(x * 12.9898 + z * 78.233) * 43758.5453) * 1000) % 61 == 0) {
                BlockState log = Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS,
                        Math.abs(dx) > Math.abs(dz) ? net.minecraft.core.Direction.Axis.X : net.minecraft.core.Direction.Axis.Z);
                if (level.getBlockState(pos.set(x, s + 1, z)).isAir()) level.setBlock(pos, log, FLAGS);
            }
        }
    }

    private static void clearAbove(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int z, int s) {
        for (int y = s + 1; y <= s + 40; y++) {
            BlockState b = level.getBlockState(pos.set(x, y, z));
            if (b.isAir()) {
                if (y > s + 3) {
                    // (keep going only while there's tree above)
                    BlockState up = level.getBlockState(pos.set(x, y + 1, z));
                    if (up.isAir()) break;
                }
                continue;
            }
            if (b.is(BlockTags.LOGS) || b.is(BlockTags.LEAVES) || b.canBeReplaced() && b.getFluidState().isEmpty())
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), FLAGS);
        }
    }

    public ListTag save() {
        ListTag list = new ListTag();
        for (Print p : prints) {
            CompoundTag t = new CompoundTag();
            t.putIntArray("p", new int[] {p.id, p.x, p.z, p.r, p.water, Float.floatToIntBits(p.yaw)});
            list.add(t);
        }
        return list;
    }

    public void load(CompoundTag tag) {
        prints.clear();
        byRegion.clear();
        nextId = tag.getInt("nextPrint");
        for (Tag t : tag.getList("prints", Tag.TAG_COMPOUND)) {
            int[] a = ((CompoundTag) t).getIntArray("p");
            if (a.length == 6) index(new Print(a[0], a[1], a[2], a[3], a[4], Float.intBitsToFloat(a[5])));
        }
    }

    public int nextId() {
        return nextId;
    }
}
