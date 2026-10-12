package net.jj.colossalturtle.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.CoreShaderRegistrationCallback;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.jj.colossalturtle.ColossalTurtle;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.jj.colossalturtle.net.GroundPayload;
import net.jj.colossalturtle.net.QuakePayload;
import net.jj.colossalturtle.net.SyncPayload;
import net.jj.colossalturtle.walk.Gait;
import net.jj.colossalturtle.walk.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/** The client keeps its own copy of him walking, nudged now and then by what the server says. */
public class TurtleClient implements ClientModInitializer {
    private static final Pose now = new Pose(), before = new Pose(), told = new Pose();
    private static final Pose drawn = new Pose();
    private static double cycle, cycleBefore, toldCycle;
    public static int mood;
    public static boolean known;
    public static GroundPayload ground;
    public static ShaderInstance bodyShader;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(SyncPayload.TYPE, (p, ctx) -> sync(p));
        ClientPlayNetworking.registerGlobalReceiver(QuakePayload.TYPE, (p, ctx) -> Shake.quake(p.strength()));
        ClientPlayNetworking.registerGlobalReceiver(GroundPayload.TYPE, (p, ctx) -> {
            ground = p;
            GroundRenderer.forget();
        });
        ClientTickEvents.END_CLIENT_TICK.register(TurtleClient::tick);
        WorldRenderEvents.AFTER_ENTITIES.register(TurtleRenderer::draw);
        CoreShaderRegistrationCallback.EVENT.register(ctx -> ctx.register(
                ColossalTurtle.id("turtle_body"), DefaultVertexFormat.NEW_ENTITY, s -> bodyShader = s));
        ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new SimpleSynchronousResourceReloadListener() {
            @Override
            public ResourceLocation getFabricId() {
                return ColossalTurtle.id("block_body");
            }

            @Override
            public void onResourceManagerReload(ResourceManager manager) {
                BlockBody.forget();
            }
        });
    }

    private static void sync(SyncPayload p) {
        told.x = p.x();
        told.z = p.z();
        told.yaw = p.yaw();
        told.baseY = p.baseY();
        told.drop = p.drop();
        toldCycle = p.cycle();
        mood = p.mood();
        double jump = Math.abs(told.x - now.x) + Math.abs(told.z - now.z);
        if (!known || jump > 400) {
            now.set(told);
            before.set(told);
            cycle = cycleBefore = toldCycle;
            known = true;
        }
    }

    private static void tick(Minecraft mc) {
        if (!known) return;
        if (mc.level == null) {
            known = false;
            return;
        }
        if (mc.isPaused()) return;
        before.set(now);
        cycleBefore = cycle;
        double speed = Gait.speed(mood), step = Gait.STRIDE / Gait.CYCLE * speed;
        for (Pose p : new Pose[] {now, told}) {
            p.x += Math.cos(p.yaw) * step;
            p.z += Math.sin(p.yaw) * step;
        }
        cycle += speed;
        toldCycle += speed;
        now.x += (told.x - now.x) * 0.1;
        now.z += (told.z - now.z) * 0.1;
        now.yaw += Mth.wrapDegrees(Math.toDegrees(told.yaw - now.yaw)) * Math.PI / 180 * 0.1;
        now.baseY += (told.baseY - now.baseY) * 0.05;
        now.drop += (told.drop - now.drop) * 0.2;
        cycle += (toldCycle - cycle) * 0.1;
        Shake.tick();
    }

    public static Pose pose(float partial) {
        drawn.x = Mth.lerp(partial, before.x, now.x);
        drawn.z = Mth.lerp(partial, before.z, now.z);
        drawn.yaw = Mth.lerp(partial, before.yaw, now.yaw);
        drawn.baseY = Mth.lerp(partial, before.baseY, now.baseY);
        drawn.drop = Mth.lerp(partial, before.drop, now.drop);
        return drawn;
    }

    public static double cycle(float partial) {
        return Mth.lerp(partial, cycleBefore, cycle);
    }
}
