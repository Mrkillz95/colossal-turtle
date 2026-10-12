package net.jj.colossalturtle.net;

import net.jj.colossalturtle.ColossalTurtle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** A foot came down: shake the view this hard (0 to 1). */
public record QuakePayload(float strength) implements CustomPacketPayload {
    public static final Type<QuakePayload> TYPE = new Type<>(ColossalTurtle.id("quake"));
    public static final StreamCodec<FriendlyByteBuf, QuakePayload> CODEC =
            StreamCodec.ofMember((p, b) -> b.writeFloat(p.strength), b -> new QuakePayload(b.readFloat()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
