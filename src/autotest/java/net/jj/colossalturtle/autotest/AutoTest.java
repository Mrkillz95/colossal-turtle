package net.jj.colossalturtle.autotest;

import static net.jj.colossalturtle.body.TurtleShape.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.jj.colossalturtle.ColossalTurtle;
import net.jj.colossalturtle.client.TurtleClient;
import net.jj.colossalturtle.body.BodyBlocks;
import net.jj.colossalturtle.body.CaveGrid;
import net.jj.colossalturtle.walk.Pose;
import net.jj.colossalturtle.walk.TurtleState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * Development only (-Dcolossal_turtle.autotest=<script>): makes a creative world, runs the script's lines, takes
 * screenshots, quits. Lines: wait N, cmd ..., shot name, look yaw pitch, near lx ly lz yaw pitch (stand at a spot in
 * his frame, in the world), check, status, quit.
 */
public final class AutoTest implements ClientModInitializer {
    private static List<String> script;
    private static int line, wait, joined;
    private static boolean asked;

    @Override
    public void onInitializeClient() {
        String f = System.getProperty("colossal_turtle.autotest");
        if (f == null) return;
        try {
            script = Files.readAllLines(Path.of(f));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ClientTickEvents.END_CLIENT_TICK.register(AutoTest::tick);
    }

    private static void log(String s) {
        System.out.println("AUTOTEST " + s);
    }

    private static void tick(Minecraft mc) {
        mc.options.pauseOnLostFocus = false;
        mc.getTutorial().setStep(TutorialSteps.NONE);
        if (mc.level == null) {
            if (mc.screen instanceof AccessibilityOnboardingScreen) {
                mc.setScreen(new TitleScreen());
                return;
            }
            if (!asked && mc.screen instanceof TitleScreen) {
                asked = true;
                GameRules rules = new GameRules();
                rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
                LevelSettings ls = new LevelSettings("turtletest", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                        WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("turtletest" + System.currentTimeMillis() % 100000, ls,
                        new WorldOptions(20261012L, false, false), WorldPresets::createNormalWorldDimensions, mc.screen);
            }
            return;
        }
        if (mc.player == null || ++joined < 60) return;
        mc.player.getAbilities().flying = true;
        if (wait > 0) {
            wait--;
            return;
        }
        while (line < script.size()) {
            String s = script.get(line++).trim();
            if (s.isEmpty() || s.startsWith("#")) continue;
            if (!run(mc, s)) return;
        }
    }

    private static boolean run(Minecraft mc, String s) {
        String[] a = s.split(" +");
        switch (a[0]) {
            case "wait" -> {
                wait = Integer.parseInt(a[1]);
                return false;
            }
            case "cmd" -> mc.player.connection.sendCommand(s.substring(4));
            case "look" -> {
                mc.player.setYRot(Float.parseFloat(a[1]));
                mc.player.setXRot(Float.parseFloat(a[2]));
            }
            case "near" -> {
                Pose p = TurtleClient.pose(0);
                double lx = Double.parseDouble(a[1]), ly = Double.parseDouble(a[2]), lz = Double.parseDouble(a[3]);
                float yaw = p.worldYRot(Float.parseFloat(a[4]));
                log("near " + p.x + " " + p.z);
                mc.player.connection.sendCommand(String.format(java.util.Locale.ROOT, "execute in minecraft:overworld run tp @s %.1f %.1f %.1f %.1f %s",
                        p.worldX(lx, lz), p.worldY(ly), p.worldZ(lx, lz), yaw, a[5]));
            }
            case "shot" -> {
                String name = a[1] + ".png";
                Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), c -> log("shot " + name));
                wait = 5;
                return false;
            }
            case "status" -> {
                Pose p = TurtleClient.pose(0);
                log(String.format("status dim=%s player=%.0f %.0f %.0f turtle=%.0f %.0f yaw=%.2f baseY=%.0f drop=%.0f mood=%d fps=%d known=%s body: %s",
                        mc.level.dimension().location(), mc.player.getX(), mc.player.getY(), mc.player.getZ(), p.x, p.z,
                        p.yaw, p.baseY, p.drop, TurtleClient.mood, mc.getFps(), TurtleClient.known, net.jj.colossalturtle.client.BlockBody.describe()));
            }
            case "check" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> check(server));
                wait = 200;
                return false;
            }
            case "goprint" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var pr = TurtleState.get(server.overworld()).prints.first();
                    if (pr == null) {
                        log("no footprints yet");
                        return;
                    }
                    log("last footprint " + pr);
                    var ow = server.overworld();
                    var ch = ow.getChunkSource().getChunkNow(pr.x() >> 4, pr.z() >> 4);
                    log("print chunk loaded=" + (ch != null) + " done=" + (ch == null ? null : ch.getAttached(ColossalTurtle.PRINTS_DONE))
                            + " ground=" + (ch == null ? -1 : ch.getHeight(Heightmap.Types.OCEAN_FLOOR, pr.x() & 15, pr.z() & 15))
                            + " pending=" + TurtleState.get(ow).prints.pending());
                    var sp = server.getPlayerList().getPlayers().get(0);
                    sp.teleportTo(server.overworld(), pr.x() - 60, pr.water() + 110, pr.z(), -90, 60);
                });
                wait = 20;
                return false;
            }
            case "printcheck" -> {
                MinecraftServer server = mc.getSingleplayerServer();
                server.execute(() -> {
                    var pr = TurtleState.get(server.overworld()).prints.first();
                    var ow = server.overworld();
                    var ch = ow.getChunkSource().getChunkNow(pr.x() >> 4, pr.z() >> 4);
                    log("print chunk loaded=" + (ch != null) + " done=" + (ch == null ? null : ch.getAttached(ColossalTurtle.PRINTS_DONE))
                            + " ground=" + (ch == null ? -1 : ch.getHeight(Heightmap.Types.OCEAN_FLOOR, pr.x() & 15, pr.z() & 15))
                            + " top=" + (ch == null ? "" : at(ow, pr.x(), ch.getHeight(Heightmap.Types.WORLD_SURFACE, pr.x() & 15, pr.z() & 15) - 1, pr.z()))
                            + " pending=" + TurtleState.get(ow).prints.pending());
                });
                wait = 10;
                return false;
            }
            case "quit" -> {
                log("done");
                mc.stop();
                return false;
            }
            default -> log("unknown line " + s);
        }
        return true;
    }

    private static String at(ServerLevel l, int x, int y, int z) {
        return l.getBlockState(new BlockPos(x, y, z)).getBlock().getName().getString();
    }

    private static void check(MinecraftServer server) {
        ServerLevel shell = server.getLevel(ColossalTurtle.SHELL);
        if (shell == null) {
            log("FAIL no shell world");
            return;
        }
        long t0 = System.nanoTime();
        int top = shell.getHeight(Heightmap.Types.WORLD_SURFACE, 0, 0);
        log("first chunk " + (System.nanoTime() - t0) / 1000000 + " ms, top at 0,0 = " + top);
        StringBuilder col = new StringBuilder();
        for (int y = top; y > G; y -= 20) col.append(y).append('=').append(at(shell, 0, y, 0)).append(' ');
        log("column 0,0: " + col);
        log((top > G + 600 ? "ok" : "FAIL") + " shell top " + top);
        double[] hip = HIPS[0];
        log("leg middle " + at(shell, (int) hip[0], G + 20, (int) hip[1]) + ", edge " + at(shell, (int) (hip[0] + LEG_R - 1), G + 20, (int) hip[1])
                + ", pad " + at(shell, (int) (hip[0] + LEG_R + 10), G + 1, (int) hip[1]));
        log("underhang at 0," + (G + 40) + ": " + at(shell, 0, G + 40, 0) + ", ceiling " + at(shell, 0, (int) BOT_C + G - 1, 0) + " / " + at(shell, 0, (int) BOT_C + G, 0));
        log("stomach " + at(shell, 0, (int) STOMACH_Y + 40, 0) + ", pool " + at(shell, 0, (int) STOMACH_WATER - 2, 0));
        int ex = (int) EYE_X, ez = (int) EYE_Z;
        int eyeTop = shell.getHeight(Heightmap.Types.WORLD_SURFACE, ex, ez);
        log("eye surface " + eyeTop + " " + at(shell, ex, eyeTop - 1, ez) + ", head top " + shell.getHeight(Heightmap.Types.WORLD_SURFACE, (int) HEAD_X, 0));
        int rz = (int) (HW * 0.95);
        int rim = shell.getHeight(Heightmap.Types.WORLD_SURFACE, 0, rz);
        log("rim top " + rim + " " + at(shell, 0, rim - 1, rz));
        // the block-filling alone, for one chunk in the thick middle of him
        long tf = System.nanoTime();
        int filled = 0;
        Column col1 = new Column();
        CaveGrid.CURRENT.set(new CaveGrid(160, 160, (int) (G + BOT_C), (int) (G + RIM + DOME)));
        for (int x = 160; x < 176; x++)
            for (int z = 160; z < 176; z++) {
                column(x, z, col1);
                for (int y = (int) col1.lowest - 40; y <= col1.highest + 2; y++) if (!BodyBlocks.at(col1, x, y, z).isAir()) filled++;
            }
        CaveGrid.CURRENT.remove();
        log("fill one chunk " + (System.nanoTime() - tf) / 1000000 + " ms, " + filled + " blocks");
        long t1 = System.nanoTime();
        for (int i = 0; i < 16; i++) shell.getChunk(10 + i, 40);
        log("16 chunks " + (System.nanoTime() - t1) / 1000000 + " ms");
        TurtleState st = TurtleState.get(server.overworld());
        log("turtle at " + (int) st.pose.x + " " + (int) st.pose.z + " baseY " + (int) st.pose.baseY + " prints " + st.prints.count());
    }
}
