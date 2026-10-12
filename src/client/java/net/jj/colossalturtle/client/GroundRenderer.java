package net.jj.colossalturtle.client;

import net.jj.colossalturtle.body.TurtleShape;
import net.jj.colossalturtle.net.GroundPayload;
import net.jj.colossalturtle.walk.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;

/**
 * Up on his back the real world isn't there, so the land below is drawn as a patchwork of its biomes' colours, the
 * one the server says he's walking over. It slides past and turns as he walks and turns.
 */
public final class GroundRenderer {
    private GroundRenderer() {}

    private static int[] colours;

    public static void forget() {
        colours = null;
    }

    private static int[] colours(GroundPayload g) {
        if (colours != null) return colours;
        Minecraft mc = Minecraft.getInstance();
        Registry<Biome> reg = mc.level.registryAccess().registryOrThrow(Registries.BIOME);
        colours = new int[g.biomes().length];
        for (int i = 0; i < colours.length; i++) {
            int x = g.x0() + (i / g.n()) * g.cell(), z = g.z0() + (i % g.n()) * g.cell();
            colours[i] = reg.getHolder(g.biomes()[i]).map(h -> colour(h, x, z)).orElse(0x6a8a4a);
        }
        return colours;
    }

    private static int colour(Holder<Biome> h, int x, int z) {
        Biome b = h.value();
        if (h.is(BiomeTags.IS_OCEAN) || h.is(BiomeTags.IS_DEEP_OCEAN) || h.is(BiomeTags.IS_RIVER)) return b.getWaterColor();
        if (h.is(BiomeTags.IS_BEACH) || h.is(BiomeTags.IS_BADLANDS)) return 0xd8c890;
        if (b.getBaseTemperature() < 0.15f) return 0xe8ecec;
        if (b.getBaseTemperature() > 1.5f) return 0xd8c890;
        if (h.is(BiomeTags.IS_FOREST) || h.is(BiomeTags.IS_TAIGA) || h.is(BiomeTags.IS_JUNGLE)) return TurtleRenderer.mul(b.getFoliageColor(), 0.75);
        return b.getGrassColor(x, z);
    }

    static void draw(Pose p) {
        GroundPayload g = TurtleClient.ground;
        // (up here the shell world's blocks are his own frame, so the land is turned into it)
        double y = TurtleShape.G;
        // far off, past the map: plain land to the horizon
        double r = 40000;
        int far = 0x6f8a58;
        TurtleRenderer.vertex(-r, y - 2, -r, far);
        TurtleRenderer.vertex(-r, y - 2, r, far);
        TurtleRenderer.vertex(r, y - 2, r, far);
        TurtleRenderer.vertex(r, y - 2, -r, far);
        if (g == null) return;
        int[] col = colours(g);
        int n = g.n(), c = g.cell();
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double x0 = g.x0() + i * c, z0 = g.z0() + j * c;
                int rgb = TurtleRenderer.mul(col[i * n + j], 0.9 + 0.2 * TurtleShape.rnd(i + g.x0() / c, j + g.z0() / c, 5));
                corner(p, x0, y, z0, rgb);
                corner(p, x0, y, z0 + c, rgb);
                corner(p, x0 + c, y, z0 + c, rgb);
                corner(p, x0 + c, y, z0, rgb);
            }
    }

    private static void corner(Pose p, double wx, double y, double wz, int rgb) {
        TurtleRenderer.vertex(p.localX(wx, wz), y, p.localZ(wx, wz), rgb);
    }
}
