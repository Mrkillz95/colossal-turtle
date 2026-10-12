package net.jj.colossalturtle.client;

import static net.jj.colossalturtle.body.TurtleShape.*;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import net.jj.colossalturtle.body.BodyBlocks;
import net.jj.colossalturtle.body.TurtleShape;
import net.jj.colossalturtle.walk.Gait;
import net.jj.colossalturtle.walk.Pose;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Him in the world, made of his real blocks, the same ones as up on his back. Close by every block is there; further
 * off he's drawn in bigger and bigger blocks (2, 4, 8... wide), in square tiles that get finer the nearer you are,
 * the way the giants have their far-away looks. His legs, head and tail are their own tiles, so they can step,
 * lie down and pull in.
 */
public final class BlockBody {
    private BlockBody() {}

    static final int CELLS = 32;
    static final int BODY = 0, HEAD = 1, TAIL = 2, LEG = 3;
    private static final int PARTS = 7;
    private static final double DETAIL = 1.6;
    private static final int MAX_BUILDS = 4, MAX_TILES = 1600;
    private static final int LIGHT = LightTexture.pack(0, 15), GLOW = LightTexture.FULL_BRIGHT;
    private static final Direction[] DIRS = {Direction.DOWN, Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    private static final Vector3f LIGHT0 = new Vector3f(0.2f, 1f, -0.7f).normalize(), LIGHT1 = new Vector3f(-0.2f, 1f, 0.7f).normalize();

    record Key(int part, int s, int x0, int z0) {}

    private record Built(MeshData data, ByteBufferBuilder mem, int quads) {}

    private static final class Tile {
        VertexBuffer vb;
        int quads;
        long used;
    }

    private static final Map<Key, Tile> ready = new HashMap<>();
    private static final Map<Key, CompletableFuture<Built>> building = new HashMap<>();
    private static long frame;
    public static int drawnTiles, drawnQuads;

    // ---- which tiles to draw ----

    private static double[] bounds(int part) {
        double m = 8;
        return switch (part) {
            case BODY -> new double[] {-HL - m, HL + m, -HW - m, HW + m};
            case HEAD -> new double[] {NECK_X0 - m, FRONT + m, -HEAD_C - m, HEAD_C + m};
            case TAIL -> new double[] {TAIL_X1 - m, TAIL_X0 + 70, -310, 310};
            default -> {
                double[] h = HIPS[part - LEG];
                double r = LEG_R + PAD + 44;
                yield new double[] {h[0] - r, h[0] + r, h[1] - r, h[1] + r};
            }
        };
    }

    private static int topSize(int part) {
        return switch (part) {
            case BODY -> 8192;
            case HEAD -> 2048;
            default -> 1024;
        };
    }

    private record Want(Key key, double d) {}

    private static void collect(int part, double cx, double cy, double cz, List<Key> out, List<Want> want) {
        double[] b = bounds(part);
        int n = topSize(part);
        for (int x0 = Math.floorDiv((int) b[0], n) * n; x0 < b[1]; x0 += n)
            for (int z0 = Math.floorDiv((int) b[2], n) * n; z0 < b[3]; z0 += n) {
                Key top = new Key(part, n / CELLS, x0, z0);
                // (the coarsest tiles are always wanted, so there is something to show while the fine ones come)
                if (!ready.containsKey(top)) want.add(new Want(top, -1));
                node(part, n, x0, z0, b, cx, cy, cz, out, want);
            }
    }

    private static boolean node(int part, int n, int x0, int z0, double[] b, double cx, double cy, double cz, List<Key> out, List<Want> want) {
        int s = n / CELLS;
        Key k = new Key(part, s, x0, z0);
        double dx = Math.max(Math.max(x0 - cx, cx - (x0 + n)), 0), dz = Math.max(Math.max(z0 - cz, cz - (z0 + n)), 0);
        double dy = Math.max(Math.max(G - cy, cy - (G + 800)), 0);
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (s > 1 && d < n * DETAIL) {
            List<Key> sub = new ArrayList<>();
            boolean all = true;
            int h = n / 2;
            for (int i = 0; i < 2; i++)
                for (int j = 0; j < 2; j++) {
                    int sx = x0 + i * h, sz = z0 + j * h;
                    if (sx > b[1] || sx + h < b[0] || sz > b[3] || sz + h < b[2]) continue;
                    all &= node(part, h, sx, sz, b, cx, cy, cz, sub, want);
                }
            if (all || !ready.containsKey(k)) {
                out.addAll(sub);
                return all;
            }
            out.add(k);
            return true;
        }
        if (ready.containsKey(k)) {
            out.add(k);
            return true;
        }
        want.add(new Want(k, d));
        return false;
    }

    // ---- drawing ----

    public static void draw(Pose pose, double cycle, int mood, double camX, double camY, double camZ, double near, double squash) {
        Minecraft mc = Minecraft.getInstance();
        frame++;
        finish();
        boolean hiding = mood == Gait.HIDING && pose.drop > 30;
        List<Key> keys = new ArrayList<>();
        List<double[]> offsets = new ArrayList<>();
        List<Want> want = new ArrayList<>();
        // the camera in his frame
        double lx = pose.localX(camX, camZ), lz = pose.localZ(camX, camZ), ly = pose.localY(camY);
        for (int part = 0; part < PARTS; part++) {
            double ox = 0, oy = 0;
            if (part != BODY && hiding) continue;
            if (part >= LEG && (mood == Gait.CALM || mood == Gait.SCARED)) {
                double ph = Gait.phase(cycle, part - LEG);
                ox = Gait.footDx(ph);
                oy = Gait.lift(ph) + pose.drop;
            }
            int before = keys.size();
            collect(part, lx - ox, ly - oy, lz, keys, want);
            for (int i = before; i < keys.size(); i++) offsets.add(new double[] {ox, oy});
        }
        request(want);

        ShaderInstance shader = TurtleClient.bodyShader;
        if (shader == null) return;
        RenderType rt = RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS);
        rt.setupRenderState();
        RenderSystem.setShader(() -> shader);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix());
        Matrix4f proj = new Matrix4f(RenderSystem.getProjectionMatrix());
        shader.setDefaultUniforms(VertexFormat.Mode.QUADS, view, proj, mc.getWindow());
        Matrix3f rot = new Matrix3f().rotateY((float) pose.yaw);
        set(shader.LIGHT0_DIRECTION, rot.transform(new Vector3f(LIGHT0)));
        set(shader.LIGHT1_DIRECTION, rot.transform(new Vector3f(LIGHT1)));
        set(shader, "Near", (float) near);
        set(shader, "Squash", (float) squash);
        shader.apply();
        Matrix4f model = new Matrix4f(), mv = new Matrix4f();
        drawnTiles = drawnQuads = 0;
        for (int i = 0; i < keys.size(); i++) {
            Key k = keys.get(i);
            Tile t = ready.get(k);
            t.used = frame;
            if (t.vb == null) continue;
            double[] off = offsets.get(i);
            model.identity()
                    .translate((float) (pose.x - camX), (float) (pose.baseY - pose.drop - camY), (float) (pose.z - camZ))
                    .rotateY((float) -pose.yaw)
                    .translate((float) (k.x0 + off[0]), (float) off[1], (float) k.z0);
            mv.set(view).mul(model);
            shader.MODEL_VIEW_MATRIX.set(mv);
            shader.MODEL_VIEW_MATRIX.upload();
            t.vb.bind();
            t.vb.draw();
            drawnTiles++;
            drawnQuads += t.quads;
        }
        VertexBuffer.unbind();
        shader.clear();
        rt.clearRenderState();
        forgetOld();
    }

    private static void set(ShaderInstance sh, String name, float v) {
        var u = sh.getUniform(name);
        if (u != null) u.set(v);
    }

    private static void set(com.mojang.blaze3d.shaders.Uniform u, Vector3f v) {
        if (u != null) u.set(v);
    }

    private static void request(List<Want> want) {
        want.sort(Comparator.comparingDouble(Want::d));
        for (Want w : want) {
            if (building.size() >= MAX_BUILDS) break;
            if (building.containsKey(w.key) || ready.containsKey(w.key)) continue;
            Key k = w.key;
            building.put(k, CompletableFuture.supplyAsync(() -> build(k), Util.backgroundExecutor()));
        }
    }

    private static void finish() {
        Iterator<Map.Entry<Key, CompletableFuture<Built>>> it = building.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, CompletableFuture<Built>> e = it.next();
            if (!e.getValue().isDone()) continue;
            it.remove();
            Built b;
            try {
                b = e.getValue().join();
            } catch (Exception ex) {
                continue;
            }
            Tile t = new Tile();
            t.used = frame;
            t.quads = b.quads;
            if (b.data != null) {
                t.vb = new VertexBuffer(VertexBuffer.Usage.STATIC);
                t.vb.bind();
                t.vb.upload(b.data);
                VertexBuffer.unbind();
            }
            b.mem.close();
            ready.put(e.getKey(), t);
        }
    }

    private static void forgetOld() {
        if (ready.size() <= MAX_TILES) return;
        List<Map.Entry<Key, Tile>> all = new ArrayList<>(ready.entrySet());
        all.sort(Comparator.comparingLong(e -> e.getValue().used));
        for (int i = 0; i < all.size() - MAX_TILES; i++) {
            if (all.get(i).getValue().used == frame) break;
            Tile t = ready.remove(all.get(i).getKey());
            if (t.vb != null) t.vb.close();
        }
    }

    /** textures changed: build everything again */
    public static void forget() {
        for (Tile t : ready.values()) if (t.vb != null) t.vb.close();
        ready.clear();
    }

    // ---- building a tile ----

    private static final int BODYK = 0, LEAVES = 1, TRUNK = 2;

    /** the solid runs in one column, as cube numbers (cube k covers k*s to k*s+s-1): from, to, kind */
    private static int[] runs(int part, Column c, int s, double x, double z) {
        List<int[]> r = new ArrayList<>(2);
        switch (part) {
            case BODY -> {
                if (!c.shell) break;
                int lo = (int) Math.floor(c.bottom), hi = (int) Math.floor(c.top);
                r.add(new int[] {Math.floorDiv(lo, s), Math.floorDiv(hi + (s <= 2 ? c.ruinH : 0), s), BODYK});
                if (c.r > 0.87 && c.r < 0.995) trees(c, s, x, z, hi, r);
            }
            case HEAD -> {
                if (!Double.isNaN(c.neckLo) && x > NECK_X0)
                    r.add(new int[] {Math.floorDiv((int) Math.floor(c.neckLo), s), Math.floorDiv((int) Math.floor(c.neckHi), s), BODYK});
                if (!Double.isNaN(c.headLo))
                    r.add(new int[] {Math.floorDiv((int) Math.floor(c.headLo), s), Math.floorDiv((int) Math.floor(c.headHi), s), BODYK});
            }
            case TAIL -> {
                if (!Double.isNaN(c.tailLo))
                    r.add(new int[] {Math.floorDiv((int) Math.floor(c.tailLo), s), Math.floorDiv((int) Math.floor(c.tailHi), s), BODYK});
            }
            default -> {
                if (c.leg != part - LEG) break;
                double d = c.legDist;
                int top;
                if (d <= LEG_R_TOP) top = (int) c.legTop;
                else if (d <= LEG_R) top = (int) (G + (LEG_R - d) / (LEG_R - LEG_R_TOP) * (c.legTop - G));
                else if (d <= LEG_R + PAD) top = G + 3;
                else break;
                r.add(new int[] {Math.floorDiv(G, s), Math.floorDiv(top, s), BODYK});
            }
        }
        if (r.isEmpty()) return null;
        int[] out = new int[r.size() * 3];
        for (int i = 0; i < r.size(); i++) System.arraycopy(r.get(i), 0, out, i * 3, 3);
        return out;
    }

    // the trees of the rim forest: from far off a canopy, close up trunks and crowns
    private static void trees(Column c, int s, double x, double z, int top, List<int[]> r) {
        if (s >= 4) {
            if (rnd(Math.floorDiv((int) x, s), Math.floorDiv((int) z, s), 77) < 0.75)
                r.add(new int[] {Math.floorDiv(top, s) + 1, Math.floorDiv(top + 5 + Math.max(s - 1, 8), s), LEAVES});
            return;
        }
        int gx = Math.floorDiv((int) x, 9), gz = Math.floorDiv((int) z, 9);
        double best = 1e9, px = 0, pz = 0;
        for (int i = -1; i <= 1; i++)
            for (int j = -1; j <= 1; j++) {
                if (rnd(gx + i, gz + j, 78) > 0.7) continue;
                double tx = (gx + i) * 9 + 1 + 7 * rnd(gx + i, gz + j, 79), tz = (gz + j) * 9 + 1 + 7 * rnd(gx + i, gz + j, 80);
                double d = Math.sqrt(sq(x - tx) + sq(z - tz));
                if (d < best) {
                    best = d;
                    px = tx;
                    pz = tz;
                }
            }
        if (best < 3.3) r.add(new int[] {Math.floorDiv(top + 6, s), Math.floorDiv(top + 11, s), LEAVES});
        if (Math.abs(x - px) < s * 0.5 + 0.01 && Math.abs(z - pz) < s * 0.5 + 0.01)
            r.add(new int[] {Math.floorDiv(top + 1, s), Math.floorDiv(top + 5, s), TRUNK});
    }

    private static boolean covered(int[] runs, int k) {
        if (runs == null) return false;
        for (int i = 0; i < runs.length; i += 3) if (k >= runs[i] && k <= runs[i + 1]) return true;
        return false;
    }

    private static Built build(Key key) {
        Minecraft mc = Minecraft.getInstance();
        int s = key.s, n = CELLS + 2;
        Column[] cols = new Column[n * n];
        int[][] runs = new int[n * n][];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                double x = key.x0 + (i - 1) * s + s * 0.5, z = key.z0 + (j - 1) * s + s * 0.5;
                Column c = TurtleShape.column(x, z);
                cols[i * n + j] = c;
                int[] r = runs(key.part, c, s, x, z);
                // round the edge of the tile the neighbours are taken as a little lower, so the walls hang down past
                // any step to a coarser tile next door
                if (r != null && (i == 0 || j == 0 || i == n - 1 || j == n - 1))
                    for (int q = 0; q < r.length; q += 3) {
                        r[q] += 2;
                        r[q + 1] -= 2;
                    }
                runs[i * n + j] = r;
            }

        ByteBufferBuilder mem = new ByteBufferBuilder(1 << 16);
        BufferBuilder out = new BufferBuilder(mem, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
        PoseStack ps = new PoseStack();
        PoseStack.Pose pose = ps.last();
        RandomSource rnd = RandomSource.create(42);
        var colours = mc.getBlockColors();
        var shaper = mc.getBlockRenderer();
        TextureAtlasSprite water = mc.getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(ResourceLocation.withDefaultNamespace("block/water_still"));
        int quads = 0;
        // north (-z), south (+z), west (-x), east (+x)
        int[] di = {0, 0, -1, 1}, dj = {-1, 1, 0, 0}, sideDir = {2, 3, 4, 5};
        for (int i = 1; i < n - 1; i++)
            for (int j = 1; j < n - 1; j++) {
                int[] r = runs[i * n + j];
                if (r == null) continue;
                Column c = cols[i * n + j];
                int bx = (int) Math.floor(key.x0 + (i - 1) * s + s * 0.5), bz = (int) Math.floor(key.z0 + (j - 1) * s + s * 0.5);
                for (int q = 0; q < r.length; q += 3) {
                    int a = r[q], b = r[q + 1], kind = r[q + 2];
                    for (int k = a; k <= b; k++) {
                        int faces = 0;
                        if (k == b && !covered(r, k + 1)) faces |= 1 << 1;
                        if (k == a && !covered(r, k - 1)) faces |= 1;
                        for (int sd = 0; sd < 4; sd++)
                            if (!covered(runs[(i + di[sd]) * n + (j + dj[sd])], k)) faces |= 1 << sideDir[sd];
                        if (faces == 0) continue;
                        BlockState st = state(c, kind, bx, bz, k, s, a, b);
                        pose.pose().identity().translate((i - 1) * s, k * s - G, (j - 1) * s).scale(s);
                        if (st.is(Blocks.WATER)) {
                            if ((faces & 2) != 0) {
                                waterTop(out, pose, water);
                                quads++;
                            }
                            st = Blocks.CALCITE.defaultBlockState();
                            faces &= ~2;
                        }
                        BakedModel bm = shaper.getBlockModel(st);
                        int light = st.getLightEmission() > 0 ? GLOW : LIGHT;
                        for (int d = 0; d < 6; d++) {
                            if ((faces & 1 << d) == 0) continue;
                            for (BakedQuad bq : bm.getQuads(st, DIRS[d], rnd)) {
                                float cr = 1, cg = 1, cb = 1;
                                if (bq.isTinted()) {
                                    int col = colours.getColor(st, null, null, bq.getTintIndex());
                                    if (col != -1) {
                                        cr = (col >> 16 & 255) / 255f;
                                        cg = (col >> 8 & 255) / 255f;
                                        cb = (col & 255) / 255f;
                                    }
                                }
                                out.putBulkData(pose, bq, cr, cg, cb, 1f, light, OverlayTexture.NO_OVERLAY);
                                quads++;
                            }
                        }
                    }
                }
            }
        MeshData md = out.build();
        return new Built(md, mem, quads);
    }

    private static BlockState state(Column c, int kind, int x, int z, int k, int s, int a, int b) {
        // (far off, leaves' little holes become big ones you see the sky through, so the canopy is drawn solid)
        if (kind == LEAVES) return s == 1 ? Blocks.SPRUCE_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true)
                : Blocks.MOSS_BLOCK.defaultBlockState();
        if (kind == TRUNK) return Blocks.SPRUCE_LOG.defaultBlockState();
        int lo = k * s, hi = k * s + s - 1;
        // the block this cube stands for: the top one for a top cube, the bottom one for a bottom cube
        if (k != a && k != b) {
            BlockState st = BodyBlocks.at(c, x, lo + s / 2, z);
            if (shows(st)) return st;
        }
        boolean down = k != a || k == b;
        for (int t = 0; t < s; t++) {
            BlockState st = BodyBlocks.at(c, x, down ? hi - t : lo + t, z);
            if (shows(st)) return st;
        }
        return Blocks.TUFF.defaultBlockState();
    }

    private static boolean shows(BlockState st) {
        return !st.isAir() && !st.is(Blocks.GLOW_LICHEN) && !st.is(Blocks.VINE) && !st.is(Blocks.CAVE_VINES)
                && !st.is(Blocks.CAVE_VINES_PLANT) && !st.is(Blocks.HANGING_ROOTS) && !st.is(Blocks.POINTED_DRIPSTONE);
    }

    private static void waterTop(BufferBuilder out, PoseStack.Pose pose, TextureAtlasSprite sp) {
        float y = 0.9f;
        int r = 0x3f, g = 0x76, b = 0xe4;
        float[][] v = {{0, y, 0}, {0, y, 1}, {1, y, 1}, {1, y, 0}};
        float[][] uv = {{sp.getU0(), sp.getV0()}, {sp.getU0(), sp.getV1()}, {sp.getU1(), sp.getV1()}, {sp.getU1(), sp.getV0()}};
        for (int i = 0; i < 4; i++)
            out.addVertex(pose, v[i][0], v[i][1], v[i][2])
                    .setColor(r, g, b, 255)
                    .setUv(uv[i][0], uv[i][1])
                    .setOverlay(OverlayTexture.NO_OVERLAY)
                    .setLight(LIGHT)
                    .setNormal(pose, 0, 1, 0);
    }

    public static String describe() {
        return ready.size() + " tiles ready, " + building.size() + " building, drawn " + drawnTiles + " tiles " + drawnQuads + " faces";
    }
}
