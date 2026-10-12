package net.jj.colossalturtle.walk;

import static net.jj.colossalturtle.body.TurtleShape.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.jj.colossalturtle.ColossalTurtle;
import net.jj.colossalturtle.body.BodyBlocks;
import net.jj.colossalturtle.body.TurtleShape;
import net.jj.colossalturtle.net.GroundPayload;
import net.jj.colossalturtle.net.QuakePayload;
import net.jj.colossalturtle.net.SyncPayload;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.phys.Vec3;

/**
 * Runs him each tick: walks him, presses his footprints in, and moves people between the world and his back. Riding
 * him means being in the shell world, where he stands still and the world goes by underneath; step off and you're
 * put back in the real world at the matching spot.
 */
public final class TurtleWorld {
    private TurtleWorld() {}

    private static final Map<UUID, Long> lastMove = new HashMap<>();
    private static GroundPayload ground;

    public static void tick(MinecraftServer server) {
        ServerLevel ow = server.overworld();
        ServerLevel shell = server.getLevel(ColossalTurtle.SHELL);
        if (shell == null) return;
        TurtleState st = TurtleState.get(ow);
        int planted = st.tick(ow);
        long now = ow.getGameTime();

        for (int leg = 0; leg < 4; leg++) if ((planted & 1 << leg) != 0) plant(ow, shell, st, leg);
        st.prints.work(ow);

        if (now % 2 == 0) {
            for (ServerPlayer p : new ArrayList<>(ow.players())) board(p, ow, shell, st, now);
            for (ServerPlayer p : new ArrayList<>(shell.players())) leave(p, ow, shell, st, now);
        }
        riders(shell, st, now);

        if (now % 10 == 0) {
            SyncPayload sync = new SyncPayload(st.pose.x, st.pose.z, st.pose.yaw, st.pose.baseY, st.pose.drop, st.cycle, st.mood);
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                if (p.level() == ow || p.level() == shell) ServerPlayNetworking.send(p, sync);
        }
        if (now % 100 == 0 && !shell.players().isEmpty()) {
            ground = groundMap(ow, st);
            for (ServerPlayer p : shell.players()) ServerPlayNetworking.send(p, ground);
        }
    }

    // ---- a foot comes down ----

    private static void plant(ServerLevel ow, ServerLevel shell, TurtleState st, int leg) {
        double[] hip = HIPS[leg];
        double lx = hip[0] + Gait.footDx(Gait.SWING), lz = hip[1];
        int fx = (int) Math.floor(st.pose.worldX(lx, lz)), fz = (int) Math.floor(st.pose.worldZ(lx, lz));
        int water = st.groundAt(ow, fx, fz) - 3;
        st.prints.add(ow, fx, fz, (int) LEG_R, water, (float) st.pose.yaw);

        float hard = st.mood == Gait.SCARED ? 1f : 0.55f;
        for (ServerPlayer p : ow.players()) {
            double d = Math.sqrt(p.distanceToSqr(fx, p.getY(), fz));
            if (d < 3000) quake(p, (float) (hard * (1 - d / 3000)));
            if (d < 1500)
                ow.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.AMBIENT,
                        (float) (2 * (1 - d / 1500)) + 0.2f, 0.3f);
        }
        for (ServerPlayer p : shell.players()) {
            quake(p, hard * 0.45f);
            // standing on that leg when it lands
            double dx = p.getX() - hip[0], dz = p.getZ() - hip[1], d = Math.sqrt(dx * dx + dz * dz);
            if (d < LEG_R + PAD + 6 && p.getY() < G + 40) {
                p.hurt(p.damageSources().fall(), 6);
                Vec3 out = new Vec3(dx / Math.max(1, d), 0.6, dz / Math.max(1, d)).scale(0.8);
                p.setDeltaMovement(p.getDeltaMovement().add(out));
                p.hurtMarked = true;
            }
        }
    }

    private static void quake(ServerPlayer p, float s) {
        if (s > 0.02f) ServerPlayNetworking.send(p, new QuakePayload(Math.min(1f, s)));
    }

    // ---- getting on and off ----

    private static boolean resting(ServerPlayer p, long now) {
        Long t = lastMove.get(p.getUUID());
        return t != null && now - t < 40;
    }

    private static void board(ServerPlayer p, ServerLevel ow, ServerLevel shell, TurtleState st, long now) {
        if (resting(p, now)) return;
        Pose pose = st.pose;
        double lx = pose.localX(p.getX(), p.getZ()), lz = pose.localZ(p.getX(), p.getZ()), ly = pose.localY(p.getY());
        if (!near(lx, lz, 500)) return;

        // the legs, where they really are just now
        if (pose.drop < 40)
            for (int i = 0; i < 4; i++) {
                double ph = Gait.phase(st.cycle, i);
                if (Gait.swinging(ph)) continue;
                double fx = HIPS[i][0] + Gait.footDx(ph), fz = HIPS[i][1];
                double dx = lx - fx, dz = lz - fz;
                if (dx * dx + dz * dz < sq(LEG_R + PAD + 2) && ly >= G - 2) {
                    move(p, shell, HIPS[i][0] + dx, ly, HIPS[i][1] + dz, pose.localYRot(p.getYRot()), toLocal(pose, p.getDeltaMovement()), now);
                    return;
                }
            }

        Column c = TurtleShape.column(lx, lz);
        double low = Double.POSITIVE_INFINITY;
        if (c.shell) low = c.bottom;
        if (!Double.isNaN(c.neckLo)) low = Math.min(low, c.neckLo);
        if (!Double.isNaN(c.headLo)) low = Math.min(low, c.headLo);
        if (!Double.isNaN(c.tailLo)) low = Math.min(low, c.tailLo);
        if (low == Double.POSITIVE_INFINITY || ly < low - 1) return;
        move(p, shell, lx, ly, lz, pose.localYRot(p.getYRot()), toLocal(pose, p.getDeltaMovement()), now);
    }

    private static void leave(ServerPlayer p, ServerLevel ow, ServerLevel shell, TurtleState st, long now) {
        if (resting(p, now)) return;
        double lx = p.getX(), lz = p.getZ(), ly = p.getY();
        boolean fell = ly < G - 3;
        if (!fell && near(lx, lz, 40)) return;
        Pose pose = st.pose;
        double wx = pose.worldX(lx, lz), wz = pose.worldZ(lx, lz);
        for (int i = 0; i < 4; i++) {
            double dx = lx - HIPS[i][0], dz = lz - HIPS[i][1];
            if (dx * dx + dz * dz < sq(LEG_R + PAD + 120)) {
                double fx = HIPS[i][0] + Gait.footDx(Gait.phase(st.cycle, i));
                wx = pose.worldX(fx + dx, HIPS[i][1] + dz);
                wz = pose.worldZ(fx + dx, HIPS[i][1] + dz);
                break;
            }
        }
        double wy = pose.worldY(ly);
        ow.getChunk((int) Math.floor(wx) >> 4, (int) Math.floor(wz) >> 4);
        int ground = ow.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(wx), (int) Math.floor(wz));
        if (wy < ground) wy = ground;
        Vec3 v = p.getDeltaMovement();
        double c = Math.cos(pose.yaw), s = Math.sin(pose.yaw);
        Vec3 wv = new Vec3(v.x * c - v.z * s, v.y, v.x * s + v.z * c);
        float fall = p.fallDistance;
        p.teleportTo(ow, wx, wy, wz, pose.worldYRot(p.getYRot()), p.getXRot());
        p.fallDistance = fall;
        push(p, wv);
        lastMove.put(p.getUUID(), now);
    }

    private static Vec3 toLocal(Pose pose, Vec3 v) {
        double c = Math.cos(pose.yaw), s = Math.sin(pose.yaw);
        return new Vec3(v.x * c + v.z * s, v.y, -v.x * s + v.z * c);
    }

    public static void move(ServerPlayer p, ServerLevel shell, double x, double y, double z, float yRot, Vec3 v, long now) {
        Column c = TurtleShape.column(x, z);
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int by = (int) Math.floor(y);
        int top = shell.getMaxBuildHeight() - 3;
        if (by > top) by = top;
        // inside the body: come up to the first open space
        int tries = 0;
        while (!(free(c, bx, by, bz) && free(c, bx, by + 1, bz)) && tries++ < 800) by++;
        if (tries > 800) return;
        float fall = p.fallDistance;
        p.teleportTo(shell, x, by == (int) Math.floor(y) ? y : by, z, yRot, p.getXRot());
        p.fallDistance = fall;
        push(p, v);
        lastMove.put(p.getUUID(), now);
    }

    private static boolean free(Column c, int x, int y, int z) {
        BlockState s = BodyBlocks.at(c, x, y, z);
        return s.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
    }

    private static void push(ServerPlayer p, Vec3 v) {
        p.setDeltaMovement(v);
        p.connection.send(new ClientboundSetEntityMotionPacket(p));
    }

    // ---- life on his back ----

    private static void riders(ServerLevel shell, TurtleState st, long now) {
        if (shell.players().isEmpty()) return;
        boolean awake = st.mood == Gait.CALM || st.mood == Gait.SCARED;
        double breath = (now % 240) / 240.0;
        for (ServerPlayer p : shell.players()) {
            double x = p.getX(), y = p.getY(), z = p.getZ();
            // a leg on the move shakes anyone hanging off it
            if (now % 10 == 0 && awake)
                for (int i = 0; i < 4; i++) {
                    if (!Gait.swinging(Gait.phase(st.cycle, i))) continue;
                    double dx = x - HIPS[i][0], dz = z - HIPS[i][1];
                    if (dx * dx + dz * dz < sq(LEG_R + PAD + 6) && y < G + BOT_E + 20) quake(p, 0.3f);
                }
            // his nostrils breathe out and in
            if (x > NOSE_X - 40 && Math.abs(Math.abs(z) - NOSE_Z) < NOSE_R + 1 && Math.abs(y - NOSE_Y) < NOSE_R + 1
                    && st.mood != Gait.HIDING) {
                double pushX = breath < 0.5 ? 0.14 : -0.09;
                if (!awake) pushX *= 0.4;
                p.setDeltaMovement(p.getDeltaMovement().add(pushX * 0.25, 0, 0));
                p.hurtMarked = true;
            }
        }
    }

    // ---- the land below, for the riders ----

    private static GroundPayload groundMap(ServerLevel ow, TurtleState st) {
        int n = 64, cell = 192;
        int x0 = Math.floorDiv((int) st.pose.x - n * cell / 2, cell) * cell;
        int z0 = Math.floorDiv((int) st.pose.z - n * cell / 2, cell) * cell;
        BiomeSource src = ow.getChunkSource().getGenerator().getBiomeSource();
        RandomState rs = ow.getChunkSource().randomState();
        Registry<Biome> reg = ow.registryAccess().registryOrThrow(Registries.BIOME);
        int[] ids = new int[n * n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++) {
                int x = x0 + i * cell + cell / 2, z = z0 + j * cell + cell / 2;
                Holder<Biome> b = src.getNoiseBiome(x >> 2, 63 >> 2, z >> 2, rs.sampler());
                ids[i * n + j] = reg.getId(b.value());
            }
        return new GroundPayload(x0, z0, cell, n, ids);
    }

    public static void onExplosion(ServerLevel level) {
        if (level.dimension() != ColossalTurtle.SHELL) return;
        ServerLevel ow = level.getServer().overworld();
        TurtleState st = TurtleState.get(ow);
        if (st.mood != Gait.HIDING) st.setMood(Gait.HIDING, 3600 + level.getRandom().nextInt(2400), ow.getGameTime());
    }

    public static void forget(ServerPlayer p) {
        lastMove.remove(p.getUUID());
    }
}
