package net.jj.colossalturtle.body;

import static net.jj.colossalturtle.body.TurtleShape.*;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.TurtleEggBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;

/** What block is where in his body. */
public final class BodyBlocks {
    private BodyBlocks() {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LICHEN_DOWN = Blocks.GLOW_LICHEN.defaultBlockState()
            .setValue(BlockStateProperties.DOWN, true)
            .setValue(BlockStateProperties.WATERLOGGED, false);
    private static final BlockState[] PLATE_ROCK = {
        Blocks.STONE.defaultBlockState(),
        Blocks.ANDESITE.defaultBlockState(),
        Blocks.TUFF.defaultBlockState(),
        Blocks.COBBLESTONE.defaultBlockState()
    };
    private static final BlockState[] FLESH = {
        Blocks.RED_TERRACOTTA.defaultBlockState(),
        Blocks.TERRACOTTA.defaultBlockState(),
        Blocks.BROWN_TERRACOTTA.defaultBlockState(),
        Blocks.PACKED_MUD.defaultBlockState()
    };
    private static final BlockState[] RUIN = {
        Blocks.STONE_BRICKS.defaultBlockState(),
        Blocks.MOSSY_STONE_BRICKS.defaultBlockState(),
        Blocks.CRACKED_STONE_BRICKS.defaultBlockState(),
        Blocks.MOSSY_STONE_BRICKS.defaultBlockState()
    };

    // ships he swallowed: x, z, facing, and how long
    private static final double[][] SHIPS = {{-350, 120, 0.4, 22}, {320, -260, 2.2, 18}, {560, 330, -0.9, 25}};
    // broken pillars he swallowed: x, z, height
    private static final double[][] PILLARS = {{-600, -200, 22}, {-560, -170, 14}, {150, 380, 18}, {720, -80, 26}, {-120, -420, 11}};

    public static BlockState at(Column c, int x, int y, int z) {
        BlockState s;
        if ((s = inside(c, x, y, z)) != null) return s;
        if (c.shell && (s = shell(c, x, y, z)) != null) return s;
        if (c.leg >= 0 && (s = leg(c, x, y, z)) != null) return s;
        if (c.shell && y < c.bottom && (s = underhang(c, x, y, z, (int) Math.floor(c.bottom))) != null) return s;
        if (!Double.isNaN(c.headLo) && (s = head(c, x, y, z)) != null) return s;
        if (!Double.isNaN(c.neckLo) && (s = neck(c, x, y, z)) != null) return s;
        if (!Double.isNaN(c.tailLo) && (s = tail(c, x, y, z)) != null) return s;
        return AIR;
    }

    // stomach, throat, nostrils: hollow places that cut through everything else
    private static BlockState inside(Column c, int x, int y, int z) {
        double st = stomach(x, y, z);
        if (st < 1.12) {
            if (st >= 1) {
                double r = rnd(x, y, z);
                return r < 0.004 ? Blocks.OCHRE_FROGLIGHT.defaultBlockState()
                        : r < 0.5 ? Blocks.MUD.defaultBlockState() : Blocks.PACKED_MUD.defaultBlockState();
            }
            BlockState swallowed = swallowed(x, y, z);
            if (swallowed != null) return swallowed;
            if (y <= STOMACH_WATER) return WATER;
            if (stomach(x, y + 1, z) >= 1 && rnd(x, y, z + 7) < 0.08) return LICHEN_DOWN;
            return AIR;
        }
        if (inThroat(x, y, z)) return AIR;
        if (inNostril(x, y, z)) return AIR;
        if (inMouth(x, y, c)) return AIR;
        return null;
    }

    private static BlockState swallowed(int x, int y, int z) {
        double base = Math.floor(STOMACH_WATER) - 3;
        for (double[] s : SHIPS) {
            double dx = x - s[0], dz = z - s[1];
            double u = dx * Math.cos(s[2]) + dz * Math.sin(s[2]), v = -dx * Math.sin(s[2]) + dz * Math.cos(s[2]);
            double half = s[3];
            int h = (int) (y - base);
            if (Math.abs(u) > half + 1 || Math.abs(v) > 9 || h < 0 || h > 30) continue;
            boolean mast = (Math.abs(u) < 0.5 || Math.abs(u + half * 0.5) < 0.5) && Math.abs(v) < 0.5;
            if (mast && h >= 1 && h <= (u > -1 ? 26 : 18)) return Blocks.SPRUCE_LOG.defaultBlockState();
            if (Math.abs(u) > half || h > 8) continue;
            double beam = 7 * Math.sqrt(Math.max(0, 1 - sq(u / (half + 0.5))));
            double bh = beam * Math.sqrt(Math.min(1, (h + 1) / 5.0));
            if (Math.abs(v) > bh) continue;
            if (h == 8) return rnd(x, y, z) < 0.15 ? null : Blocks.SPRUCE_PLANKS.defaultBlockState();
            if (Math.abs(v) > bh - 1.2 || h == 0)
                return rnd(x, y, z) < 0.06 ? null : Blocks.DARK_OAK_PLANKS.defaultBlockState();
            return y <= STOMACH_WATER ? WATER : AIR;
        }
        for (double[] p : PILLARS) {
            if (sq(x - p[0]) + sq(z - p[1]) < 6.5 && y >= base - 6 && y <= base + p[2])
                return RUIN[(int) (rnd(x, y, z) * 4)];
        }
        return null;
    }

    private static BlockState shell(Column c, int x, int y, int z) {
        int top = (int) Math.floor(c.top), bot = (int) Math.floor(c.bottom);
        if (y > top) {
            if (c.ruinH > 0 && y <= top + c.ruinH) return RUIN[(int) (rnd(x, y, z) * 4)];
            return null;
        }
        if (y < bot) return null;
        if (c.crack && y > top - 90) return AIR;
        int dTop = top - y, dBot = y - bot;
        if (dBot < PLAST_T && dBot <= dTop) {
            if (c.r > 0.7 && dBot < 2) return Blocks.ROOTED_DIRT.defaultBlockState();
            if (dBot == 0 && rnd(x, y, z) < 0.003) return Blocks.SHROOMLIGHT.defaultBlockState();
            return dBot < 3 ? Blocks.CALCITE.defaultBlockState() : Blocks.DRIPSTONE_BLOCK.defaultBlockState();
        }
        if (dTop < SHELL_T) return shellRock(c, x, y, z, dTop);
        // inside his body
        if (cave(x, y, z)) {
            if (!cave(x, y + 1, z) && rnd(x, y, z + 3) < 0.06) return LICHEN_DOWN;
            return AIR;
        }
        return FLESH[(int) (rnd(x >> 2, y >> 2, z >> 2) * 4)];
    }

    private static BlockState shellRock(Column c, int x, int y, int z, int dTop) {
        BlockState rock = PLATE_ROCK[(int) ((c.plate >>> 3 & 0xFF) / 256.0 * 4)];
        if (c.r > 0.86) {
            if (dTop == 0)
                return noise(2, x / 30.0, z / 30.0) > 0.3 ? Blocks.PODZOL.defaultBlockState()
                        : Blocks.GRASS_BLOCK.defaultBlockState();
            if (dTop < 5) return Blocks.DIRT.defaultBlockState();
        } else if (c.groove) {
            if (dTop == 0) {
                if (c.edge < 3 && c.r < 0.8 && noise(3, x / 60.0, z / 60.0) > 0.1) return WATER;
                return Blocks.MOSS_BLOCK.defaultBlockState();
            }
            if (dTop == 1 && c.edge < 3) return Blocks.CLAY.defaultBlockState();
            if (dTop < 4) return Blocks.ROOTED_DIRT.defaultBlockState();
        } else if (dTop == 0) {
            if (c.top > G + 640) return Blocks.SNOW_BLOCK.defaultBlockState();
            if (c.edge < GROOVE + 3) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            if (noise(1, x / 50.0, z / 50.0) > 0.55) return Blocks.GRAVEL.defaultBlockState();
            return rock;
        }
        return dTop < 20 ? rock : Blocks.DEEPSLATE.defaultBlockState();
    }

    // the dark under his shell: glowing lichen, dripstone, and vines and roots under the rim
    private static BlockState underhang(Column c, int x, int y, int z, int bot) {
        int down = bot - y;
        double h = rnd(x, 5, z);
        if (c.r > 0.72) {
            if (h < 0.03) {
                int len = 4 + (int) (rnd(x, 6, z) * 28);
                if (down > len) return null;
                boolean berries = rnd(x, y, z) < 0.25;
                if (down == len)
                    return Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, berries);
                return Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(CaveVines.BERRIES, berries);
            }
            if (down == 1 && h < 0.18) return Blocks.HANGING_ROOTS.defaultBlockState();
            return null;
        }
        if (h < 0.012) {
            int len = 2 + (int) (rnd(x, 6, z) * 4);
            if (down > len) return null;
            int k = down - 1;
            DripstoneThickness t = k == len - 1 ? DripstoneThickness.TIP
                    : k == len - 2 ? DripstoneThickness.FRUSTUM
                    : k == 0 ? DripstoneThickness.BASE : DripstoneThickness.MIDDLE;
            return Blocks.POINTED_DRIPSTONE.defaultBlockState()
                    .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                    .setValue(PointedDripstoneBlock.THICKNESS, t);
        }
        if (down == 1 && h < 0.1) return LICHEN_DOWN;
        return null;
    }

    private static BlockState leg(Column c, int x, int y, int z) {
        if (y < G || y > c.legTop) return null;
        double r = legRadius(c, y);
        if (c.legDist <= r) {
            double depth = r - c.legDist;
            if (depth > 3) return Blocks.TUFF.defaultBlockState();
            if ((y - G) % 7 == 0 && c.legDist > r - 2.5) return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            return scale(x, y, z, Math.atan2(c.legDz, c.legDx) * c.legDist, y - G < 30);
        }
        // claws on the front of each foot
        double out = c.legDist - (LEG_R + PAD);
        if (y <= G + 6 && out >= 0 && out < 40 && y <= G + 6 * (1 - out / 40)) {
            double a = Math.atan2(c.legDz, c.legDx);
            for (double claw = -0.5; claw <= 0.51; claw += 0.25)
                if (Math.abs(a - claw) < 0.035) return Blocks.BONE_BLOCK.defaultBlockState();
        }
        // vines to climb, and nests on the ledges
        if (c.legDist <= r + 1 && y > G + 4) {
            if (rnd(x, 3, z) < 0.12) return vine(c);
            if ((y - 1 - G) % 7 == 0 && c.legDist <= legRadius(c, y - 1) && rnd(x, y, z) < 0.01)
                return Blocks.TURTLE_EGG.defaultBlockState().setValue(TurtleEggBlock.EGGS, 1 + (int) (rnd(x, y, 4) * 4));
        }
        return null;
    }

    private static BlockState vine(Column c) {
        BlockState v = Blocks.VINE.defaultBlockState();
        if (Math.abs(c.legDx) > Math.abs(c.legDz)) return v.setValue(c.legDx > 0 ? VineBlock.WEST : VineBlock.EAST, true);
        return v.setValue(c.legDz > 0 ? VineBlock.NORTH : VineBlock.SOUTH, true);
    }

    // his skin: rows of stone scales
    private static BlockState scale(int x, int y, int z, double u, boolean low) {
        int row = Math.floorDiv(y, 5);
        double uu = u / 7 + (row & 1) * 0.5;
        boolean border = uu - Math.floor(uu) < 0.16 || Math.floorMod(y, 5) == 0;
        if (border) return Blocks.POLISHED_TUFF.defaultBlockState();
        if (low && rnd(x, y, z) < 0.3) return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        return Blocks.TUFF_BRICKS.defaultBlockState();
    }

    private static BlockState neck(Column c, int x, int y, int z) {
        int lo = (int) Math.floor(c.neckLo), hi = (int) Math.floor(c.neckHi);
        if (y < lo || y > hi) return null;
        if (y == hi) return Math.abs(z) < NECK_W * 0.45 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState()
                : Blocks.MOSS_BLOCK.defaultBlockState();
        if (y - lo < 2 || hi - y < 2) return scale(x, y, z, x, false);
        return Blocks.TUFF.defaultBlockState();
    }

    private static BlockState head(Column c, int x, int y, int z) {
        int lo = (int) Math.floor(c.headLo), hi = (int) Math.floor(c.headHi);
        if (y < lo || y > hi) return null;
        if (x > BEAK_X) return y >= hi - 1 ? Blocks.POLISHED_BLACKSTONE.defaultBlockState()
                : Blocks.BLACKSTONE.defaultBlockState();
        if (c.eye >= 0) {
            if (c.eye < EYE_R) {
                int water = hi - 2;
                int floor = hi - (int) (14 * (1 - sq(c.eye / EYE_R)) + 3);
                if (y > water) return AIR;
                if (y > floor) return WATER;
                if (y == floor) return c.eye < 30 ? Blocks.BLACK_CONCRETE.defaultBlockState()
                        : c.eye < 52 ? Blocks.BROWN_CONCRETE.defaultBlockState() : Blocks.CALCITE.defaultBlockState();
            } else if (y >= hi - 1) return Blocks.CALCITE.defaultBlockState();
        }
        int dTop = hi - y;
        if (dTop == 0) return Blocks.GRASS_BLOCK.defaultBlockState();
        if (dTop < 4) return Blocks.DIRT.defaultBlockState();
        if (y - lo < 3) return scale(x, y, z, x, false);
        return Blocks.TUFF.defaultBlockState();
    }

    private static BlockState tail(Column c, int x, int y, int z) {
        int lo = (int) Math.floor(c.tailLo), hi = (int) Math.floor(c.tailHi);
        if (y < lo || y > hi) return null;
        int dTop = hi - y;
        if (dTop == 0 && noise(3, x / 25.0, z / 25.0) > 0.4) return WATER;
        if (dTop < 3) return Blocks.MUD.defaultBlockState();
        if (y - lo < 3) return scale(x, y, z, x, true);
        return Blocks.PACKED_MUD.defaultBlockState();
    }
}
