package net.jj.colossalturtle.net;

import net.jj.colossalturtle.ColossalTurtle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A rough map of the land under him (which biome is where), for the riders on his back, who can't see the real
 * world from up there. Cells are `cell` blocks wide, n by n, starting at x0, z0.
 */
public record GroundPayload(int x0, int z0, int cell, int n, int[] biomes) implements CustomPacketPayload {
    public static final Type<GroundPayload> TYPE = new Type<>(ColossalTurtle.id("ground"));
    public static final StreamCodec<FriendlyByteBuf, GroundPayload> CODEC = StreamCodec.ofMember(GroundPayload::write, GroundPayload::read);

    private void write(FriendlyByteBuf b) {
        b.writeInt(x0);
        b.writeInt(z0);
        b.writeVarInt(cell);
        b.writeVarInt(n);
        for (int id : biomes) b.writeVarInt(id);
    }

    private static GroundPayload read(FriendlyByteBuf b) {
        int x0 = b.readInt(), z0 = b.readInt(), cell = b.readVarInt(), n = b.readVarInt();
        int[] ids = new int[n * n];
        for (int i = 0; i < ids.length; i++) ids[i] = b.readVarInt();
        return new GroundPayload(x0, z0, cell, n, ids);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
