package net.jj.colossalturtle.client;

import static net.jj.colossalturtle.body.TurtleShape.clamp;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.jj.colossalturtle.ColossalTurtle;
import net.jj.colossalturtle.walk.Pose;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Down in the world: him, made of his blocks (BlockBody). Up on his back: the land going by below. Past the edge of
 * the loaded world things are pulled in towards you along your line of sight, so they look just as big but still fit
 * inside the view.
 */
public final class TurtleRenderer {
    private TurtleRenderer() {}

    private static double cx, cy, cz, near, squash;
    private static float fogR, fogG, fogB, light;
    private static BufferBuilder buf;

    public static void draw(WorldRenderContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (!TurtleClient.known || mc.level == null) return;
        boolean world = mc.level.dimension() == Level.OVERWORLD, onHim = mc.level.dimension() == ColossalTurtle.SHELL;
        if (!world && !onHim) return;
        float partial = ctx.tickCounter().getGameTimeDeltaPartialTick(false);
        Pose pose = TurtleClient.pose(partial);
        Vec3 cam = ctx.camera().getPosition();
        cx = cam.x;
        cy = cam.y;
        cz = cam.z;
        near = Math.max(64, mc.options.getEffectiveRenderDistance() * 16 * 0.95);
        double far = mc.gameRenderer.getDepthFar() * 0.9;
        squash = Math.max(0.0005, (far - near) / (60000 - near));

        if (world) {
            BlockBody.draw(pose, TurtleClient.cycle(partial), TurtleClient.mood, cx, cy, cz, near, squash);
            return;
        }
        float[] fog = RenderSystem.getShaderFogColor();
        fogR = fog[0];
        fogG = fog[1];
        fogB = fog[2];
        light = Math.max(0.25f, mc.level.getSkyDarken(partial));
        buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        GroundRenderer.draw(pose);
        MeshData mesh = buf.build();
        buf = null;
        if (mesh == null) return;
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferUploader.drawWithShader(mesh);
        RenderSystem.enableCull();
    }

    /** a point in the coordinates of the world being drawn */
    static void vertex(double wx, double wy, double wz, int rgb) {
        double rx = wx - cx, ry = wy - cy, rz = wz - cz;
        double d = Math.sqrt(rx * rx + ry * ry + rz * rz);
        if (d > near) {
            double k = (near + (d - near) * squash) / d;
            rx *= k;
            ry *= k;
            rz *= k;
        }
        float h = (float) clamp((d - 400) / 14000, 0, 0.45);
        float r = ((rgb >> 16 & 255) / 255f * light) * (1 - h) + fogR * h;
        float g = ((rgb >> 8 & 255) / 255f * light) * (1 - h) + fogG * h;
        float b = ((rgb & 255) / 255f * light) * (1 - h) + fogB * h;
        buf.addVertex((float) rx, (float) ry, (float) rz).setColor(r, g, b, 1f);
    }

    static int mul(int rgb, double k) {
        int r = (int) Math.min(255, (rgb >> 16 & 255) * k), g = (int) Math.min(255, (rgb >> 8 & 255) * k), b = (int) Math.min(255, (rgb & 255) * k);
        return r << 16 | g << 8 | b;
    }
}
