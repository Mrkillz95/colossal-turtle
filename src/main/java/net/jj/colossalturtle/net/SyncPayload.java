package net.jj.colossalturtle.net;

import net.jj.colossalturtle.ColossalTurtle;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Where he is and what he's doing, sent to everyone twice a second. */
public record SyncPayload(double x, double z, double yaw, double baseY, double drop, double cycle, int mood)
        implements CustomPacketPayload {
    public static final Type<SyncPayload> TYPE = new Type<>(ColossalTurtle.id("sync"));
    public static final StreamCodec<FriendlyByteBuf, SyncPayload> CODEC = StreamCodec.ofMember(SyncPayload::write, SyncPayload::read);

    private void write(FriendlyByteBuf b) {
        b.writeDouble(x);
        b.writeDouble(z);
        b.writeDouble(yaw);
        b.writeDouble(baseY);
        b.writeDouble(drop);
        b.writeDouble(cycle);
        b.writeVarInt(mood);
    }

    private static SyncPayload read(FriendlyByteBuf b) {
        return new SyncPayload(b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(), b.readDouble(),
                b.readDouble(), b.readVarInt());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
