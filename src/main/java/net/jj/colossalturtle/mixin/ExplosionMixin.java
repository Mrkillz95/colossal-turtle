package net.jj.colossalturtle.mixin;

import net.jj.colossalturtle.walk.TurtleWorld;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Blowing things up on his back frightens him into his shell. */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow
    @Final
    private Level level;

    @Inject(method = "explode", at = @At("HEAD"))
    private void colossalTurtle$frighten(CallbackInfo ci) {
        if (level instanceof ServerLevel s) TurtleWorld.onExplosion(s);
    }
}
