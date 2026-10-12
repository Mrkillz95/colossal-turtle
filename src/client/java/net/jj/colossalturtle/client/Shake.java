package net.jj.colossalturtle.client;

import net.jj.colossalturtle.ColossalTurtle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

/** The ground shaking when his foot comes down. */
public final class Shake {
    private Shake() {}

    private static float trauma;
    private static int age;

    public static void quake(float s) {
        trauma = Math.max(trauma, s);
        Minecraft mc = Minecraft.getInstance();
        // up on his back you hear it through his body; down in the world the server sends the sound
        if (mc.level != null && mc.level.dimension() == ColossalTurtle.SHELL)
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.GENERIC_EXPLODE.value(), 0.3f, 0.25f + 0.5f * s));
    }

    public static void tick() {
        trauma = Math.max(0, trauma - 0.025f);
        age++;
    }

    /** side, up, back */
    public static float[] offset(float partial) {
        if (trauma <= 0) return null;
        float t = (age + partial) * 1.7f, k = trauma * trauma * 0.6f;
        return new float[] {
            (float) Math.sin(t * 1.3) * k, (float) Math.sin(t * 2.1 + 1) * k, (float) Math.sin(t * 0.9 + 2) * k * 0.5f
        };
    }
}
