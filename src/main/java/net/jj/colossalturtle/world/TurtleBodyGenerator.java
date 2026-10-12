package net.jj.colossalturtle.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.jj.colossalturtle.body.BodyBlocks;
import net.jj.colossalturtle.body.CaveGrid;
import net.jj.colossalturtle.body.TurtleShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

/** Builds the shell world: him, block by block, and nothing else. */
public class TurtleBodyGenerator extends ChunkGenerator {
    public static final MapCodec<TurtleBodyGenerator> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource))
            .apply(i, TurtleBodyGenerator::new));

    public TurtleBodyGenerator(BiomeSource biomes) {
        super(biomes);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(
            Blender blender, RandomState random, StructureManager structures, ChunkAccess chunk) {
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        int minY = chunk.getMinBuildHeight(), maxY = chunk.getMaxBuildHeight() - 1;
        TurtleShape.Column c = new TurtleShape.Column();
        // how high his insides reach in this chunk, for the cave noise
        int fleshLo = Integer.MAX_VALUE, fleshHi = Integer.MIN_VALUE;
        for (int dx = 0; dx <= 16; dx += 4)
            for (int dz = 0; dz <= 16; dz += 4) {
                TurtleShape.column(Math.min(x0 + dx, x0 + 15), Math.min(z0 + dz, z0 + 15), c);
                if (!c.shell) continue;
                fleshLo = Math.min(fleshLo, (int) (c.bottom + TurtleShape.PLAST_T) - 8);
                fleshHi = Math.max(fleshHi, (int) (c.top - TurtleShape.SHELL_T) + 8);
            }
        if (fleshLo < fleshHi) CaveGrid.CURRENT.set(new CaveGrid(x0, z0, fleshLo, fleshHi));
        try {
            fill(chunk, x0, z0, minY, maxY, c);
        } finally {
            CaveGrid.CURRENT.remove();
        }
        Heightmap.primeHeightmaps(chunk, EnumSet.of(Heightmap.Types.OCEAN_FLOOR_WG, Heightmap.Types.WORLD_SURFACE_WG));
        return CompletableFuture.completedFuture(chunk);
    }

    private static void fill(ChunkAccess chunk, int x0, int z0, int minY, int maxY, TurtleShape.Column c) {
        for (int dx = 0; dx < 16; dx++)
            for (int dz = 0; dz < 16; dz++) {
                int x = x0 + dx, z = z0 + dz;
                TurtleShape.column(x, z, c);
                if (Double.isNaN(c.lowest)) continue;
                // (vines and roots hang a little below the body)
                int lo = Math.max(minY, (int) Math.floor(c.lowest) - 40), hi = Math.min(maxY, (int) Math.ceil(c.highest) + 2);
                for (int y = lo; y <= hi; y++) {
                    BlockState s = BodyBlocks.at(c, x, y, z);
                    if (s.isAir()) continue;
                    LevelChunkSection sec = chunk.getSection(chunk.getSectionIndex(y));
                    sec.setBlockState(dx, y & 15, dz, s, false);
                }
            }
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState random) {
        TurtleShape.Column c = TurtleShape.column(x, z);
        if (Double.isNaN(c.highest)) return level.getMinBuildHeight();
        for (int y = Math.min(level.getMaxBuildHeight() - 1, (int) Math.ceil(c.highest) + 2); y >= c.lowest; y--)
            if (type.isOpaque().test(BodyBlocks.at(c, x, y, z))) return y + 1;
        return level.getMinBuildHeight();
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState random) {
        int min = level.getMinBuildHeight();
        BlockState[] states = new BlockState[level.getHeight()];
        TurtleShape.Column c = TurtleShape.column(x, z);
        for (int i = 0; i < states.length; i++)
            states[i] = Double.isNaN(c.lowest) ? Blocks.AIR.defaultBlockState() : BodyBlocks.at(c, x, min + i, z);
        return new NoiseColumn(min, states);
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState random, BiomeManager biomes,
            StructureManager structures, ChunkAccess chunk, GenerationStep.Carving step) {}

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structures, RandomState random, ChunkAccess chunk) {}

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {}

    @Override
    public int getGenDepth() {
        return 1024;
    }

    @Override
    public int getSeaLevel() {
        return 0;
    }

    @Override
    public int getMinY() {
        return 0;
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState random, BlockPos pos) {
        info.add("Turtle: " + TurtleShape.zone(pos.getX(), pos.getY(), pos.getZ()));
    }
}
