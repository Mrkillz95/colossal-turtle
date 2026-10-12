package net.jj.colossalturtle.world;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.stream.Stream;
import net.jj.colossalturtle.body.TurtleShape;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/** Each of his lands is its own biome, picked from where you are on him. */
public class TurtleBiomeSource extends BiomeSource {
    public static final MapCodec<TurtleBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                    Biome.CODEC.fieldOf("highlands").forGetter(s -> s.biomes.get(0)),
                    Biome.CODEC.fieldOf("moss_valleys").forGetter(s -> s.biomes.get(1)),
                    Biome.CODEC.fieldOf("rim_forest").forGetter(s -> s.biomes.get(2)),
                    Biome.CODEC.fieldOf("underhang").forGetter(s -> s.biomes.get(3)),
                    Biome.CODEC.fieldOf("leg_cliffs").forGetter(s -> s.biomes.get(4)),
                    Biome.CODEC.fieldOf("neck_bridge").forGetter(s -> s.biomes.get(5)),
                    Biome.CODEC.fieldOf("head_lands").forGetter(s -> s.biomes.get(6)),
                    Biome.CODEC.fieldOf("tail_marsh").forGetter(s -> s.biomes.get(7)),
                    Biome.CODEC.fieldOf("inside").forGetter(s -> s.biomes.get(8)),
                    Biome.CODEC.fieldOf("stomach").forGetter(s -> s.biomes.get(9)))
            .apply(i, (a, b, c, d, e, f, g, h, j, k) -> new TurtleBiomeSource(List.of(a, b, c, d, e, f, g, h, j, k))));

    // in the same order as Zone
    private final List<Holder<Biome>> biomes;

    public TurtleBiomeSource(List<Holder<Biome>> biomes) {
        this.biomes = biomes;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return biomes.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int qx, int qy, int qz, Climate.Sampler sampler) {
        return biomes.get(TurtleShape.zone(qx * 4 + 2, qy * 4 + 2, qz * 4 + 2).ordinal());
    }
}
