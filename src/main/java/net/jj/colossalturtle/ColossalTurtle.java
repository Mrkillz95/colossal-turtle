package net.jj.colossalturtle;

import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.jj.colossalturtle.net.GroundPayload;
import net.jj.colossalturtle.net.QuakePayload;
import net.jj.colossalturtle.net.SyncPayload;
import net.jj.colossalturtle.walk.TurtleState;
import net.jj.colossalturtle.walk.TurtleWorld;
import net.jj.colossalturtle.world.TurtleBiomeSource;
import net.jj.colossalturtle.world.TurtleBodyGenerator;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class ColossalTurtle implements ModInitializer {
    public static final String MOD = "colossal_turtle";
    public static final ResourceKey<Level> SHELL = ResourceKey.create(Registries.DIMENSION, id("shell"));

    /** the last footprint a chunk has had pressed into it */
    public static final AttachmentType<Integer> PRINTS_DONE = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT)
            .buildAndRegister(id("prints_done"));

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD, path);
    }

    @Override
    public void onInitialize() {
        Registry.register(BuiltInRegistries.CHUNK_GENERATOR, id("turtle_body"), TurtleBodyGenerator.CODEC);
        Registry.register(BuiltInRegistries.BIOME_SOURCE, id("turtle_body"), TurtleBiomeSource.CODEC);

        PayloadTypeRegistry.playS2C().register(SyncPayload.TYPE, SyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(QuakePayload.TYPE, QuakePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(GroundPayload.TYPE, GroundPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(TurtleWorld::tick);
        ServerChunkEvents.CHUNK_LOAD.register((level, chunk) -> {
            if (level.dimension() == Level.OVERWORLD) TurtleState.get(level).prints.onChunkLoad(chunk);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((h, s) -> TurtleWorld.forget(h.getPlayer()));
        CommandRegistrationCallback.EVENT.register((d, reg, env) -> TurtleCommand.register(d));
    }
}
