package net.jj.colossalturtle.walk;

import net.jj.colossalturtle.body.TurtleShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.saveddata.SavedData;

/** The one turtle in the world: where he is, how far through his step, his mood. Kept with the overworld's save. */
public class TurtleState extends SavedData {
    private static final Factory<TurtleState> FACTORY = new Factory<>(TurtleState::new, TurtleState::load, null);

    // he wanders off this far from spawn at most, then bends back round
    public static final double ROAM = 20000;
    private static final double TURN = 0.0002;

    public final Pose pose = new Pose();
    public final Footprints prints = new Footprints();
    public double cycle, targetYaw;
    public int mood = Gait.CALM;
    public long moodEnd, awake, nextWander;
    public boolean placed;

    public static TurtleState get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(FACTORY, "colossal_turtle");
    }

    /** one tick of his life; returns which legs came down this tick (bit per leg) */
    public int tick(ServerLevel ow) {
        RandomSource rnd = ow.getRandom();
        long now = ow.getGameTime();
        if (!placed) place(ow, rnd);

        // moods
        if (mood != Gait.CALM && moodEnd > 0 && now >= moodEnd) setMood(Gait.CALM, 0, now);
        if (mood == Gait.CALM && ow.isThundering()) setMood(Gait.SCARED, 0, now);
        if (mood == Gait.SCARED && moodEnd == 0 && !ow.isThundering()) moodEnd = now + 600;
        if (mood == Gait.CALM || mood == Gait.SCARED) awake++;
        // after a few days on his feet he lies down, at night, and sleeps for one to three days
        if (mood == Gait.CALM && awake > 3 * 24000 && ow.isNight() && rnd.nextInt(2000) == 0)
            setMood(Gait.TIRED, 24000 + rnd.nextInt(48000), now);

        double want = Gait.drop(mood);
        pose.drop += Math.max(-0.08, Math.min(0.08, want - pose.drop));

        int planted = 0;
        double speed = Gait.speed(mood);
        if (speed > 0) {
            double before = cycle;
            cycle += speed;
            double step = Gait.STRIDE / Gait.CYCLE * speed;
            pose.x += Math.cos(pose.yaw) * step;
            pose.z += Math.sin(pose.yaw) * step;
            if (now >= nextWander) {
                targetYaw = pose.yaw + (rnd.nextDouble() - 0.5) * 1.2;
                BlockPos sp = ow.getSharedSpawnPos();
                double fx = pose.x - sp.getX(), fz = pose.z - sp.getZ();
                if (fx * fx + fz * fz > ROAM * ROAM) targetYaw = Math.atan2(-fz, -fx) + (rnd.nextDouble() - 0.5) * 0.8;
                nextWander = now + 4000 + rnd.nextInt(8000);
            }
            double turn = wrap(targetYaw - pose.yaw);
            pose.yaw += Math.max(-TURN * speed, Math.min(TURN * speed, turn));
            for (int leg = 0; leg < 4; leg++) if (Gait.planted(before, cycle, leg)) planted |= 1 << leg;
        }
        if (now % 40 == 0) feel(ow, false);
        if (now % 100 == 0) setDirty();
        return planted;
    }

    public void setMood(int m, int ticks, long now) {
        mood = m;
        moodEnd = ticks > 0 ? now + ticks : 0;
        if (m == Gait.TIRED) awake = 0;
        setDirty();
    }

    private void place(ServerLevel ow, RandomSource rnd) {
        BlockPos sp = ow.getSharedSpawnPos();
        double a = rnd.nextDouble() * Math.PI * 2;
        pose.x = sp.getX() + Math.cos(a) * 4500;
        pose.z = sp.getZ() + Math.sin(a) * 4500;
        // walking round past spawn, not at it
        pose.yaw = targetYaw = a + Math.PI / 2;
        nextWander = ow.getGameTime() + 6000;
        placed = true;
        feel(ow, true);
        setDirty();
    }

    public void moveTo(ServerLevel ow, double x, double z, double yaw) {
        pose.x = x;
        pose.z = z;
        pose.yaw = targetYaw = yaw;
        feel(ow, true);
        setDirty();
    }

    /** feel for the ground under his feet, so he walks over hills and down into the sea */
    private void feel(ServerLevel ow, boolean snap) {
        ChunkGenerator gen = ow.getChunkSource().getGenerator();
        RandomState rs = ow.getChunkSource().randomState();
        double sum = 0;
        for (double[] h : TurtleShape.HIPS) {
            int x = (int) pose.worldX(h[0], h[1]), z = (int) pose.worldZ(h[0], h[1]);
            sum += gen.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ow, rs);
        }
        double target = sum / 4;
        pose.baseY = snap ? target : pose.baseY + (target - pose.baseY) * 0.25;
    }

    /** water level for a footprint dropped here */
    public int groundAt(ServerLevel ow, int x, int z) {
        return ow.getChunkSource().getGenerator()
                .getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ow, ow.getChunkSource().randomState());
    }

    public static double wrap(double a) {
        while (a > Math.PI) a -= Math.PI * 2;
        while (a < -Math.PI) a += Math.PI * 2;
        return a;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider lookup) {
        tag.putBoolean("placed", placed);
        tag.putDouble("x", pose.x);
        tag.putDouble("z", pose.z);
        tag.putDouble("yaw", pose.yaw);
        tag.putDouble("baseY", pose.baseY);
        tag.putDouble("drop", pose.drop);
        tag.putDouble("cycle", cycle);
        tag.putDouble("targetYaw", targetYaw);
        tag.putInt("mood", mood);
        tag.putLong("moodEnd", moodEnd);
        tag.putLong("awake", awake);
        tag.putLong("nextWander", nextWander);
        tag.putInt("nextPrint", prints.nextId());
        tag.put("prints", prints.save());
        return tag;
    }

    private static TurtleState load(CompoundTag tag, HolderLookup.Provider lookup) {
        TurtleState s = new TurtleState();
        s.placed = tag.getBoolean("placed");
        s.pose.x = tag.getDouble("x");
        s.pose.z = tag.getDouble("z");
        s.pose.yaw = tag.getDouble("yaw");
        s.pose.baseY = tag.getDouble("baseY");
        s.pose.drop = tag.getDouble("drop");
        s.cycle = tag.getDouble("cycle");
        s.targetYaw = tag.getDouble("targetYaw");
        s.mood = tag.getInt("mood");
        s.moodEnd = tag.getLong("moodEnd");
        s.awake = tag.getLong("awake");
        s.nextWander = tag.getLong("nextWander");
        s.prints.load(tag);
        return s;
    }
}
