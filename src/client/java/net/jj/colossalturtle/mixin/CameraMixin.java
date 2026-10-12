package net.jj.colossalturtle.mixin;

import net.jj.colossalturtle.client.Shake;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** His footsteps shake the view. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    protected abstract void move(float forward, float up, float side);

    @Inject(method = "setup", at = @At("TAIL"))
    private void colossalTurtle$shake(BlockGetter level, Entity focus, boolean detached, boolean mirror, float partial, CallbackInfo ci) {
        float[] o = Shake.offset(partial);
        if (o != null) move(o[2], o[1], o[0]);
    }
}
