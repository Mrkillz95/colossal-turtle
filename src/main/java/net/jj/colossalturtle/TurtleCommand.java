package net.jj.colossalturtle;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.jj.colossalturtle.body.TurtleShape;
import net.jj.colossalturtle.walk.Gait;
import net.jj.colossalturtle.walk.TurtleState;
import net.jj.colossalturtle.walk.TurtleWorld;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** /turtle: where is he. The rest is for ops: ride, bring, mood. */
public final class TurtleCommand {
    private TurtleCommand() {}

    private static final String[] MOODS = {"calm", "scared", "tired", "hiding"};

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("turtle")
                .executes(TurtleCommand::where)
                .then(Commands.literal("ride").requires(s -> s.hasPermission(2)).executes(TurtleCommand::ride))
                .then(Commands.literal("bring").requires(s -> s.hasPermission(2)).executes(TurtleCommand::bring))
                .then(Commands.literal("mood").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("mood", StringArgumentType.word())
                                .suggests((c, b) -> {
                                    for (String m : MOODS) b.suggest(m);
                                    return b.buildFuture();
                                })
                                .executes(TurtleCommand::mood))));
    }

    private static TurtleState state(CommandContext<CommandSourceStack> c) {
        return TurtleState.get(c.getSource().getServer().overworld());
    }

    private static int where(CommandContext<CommandSourceStack> c) {
        TurtleState st = state(c);
        String mood = MOODS[st.mood];
        String msg = String.format("The turtle is at %d, %d, walking %s. He's %s.",
                (int) st.pose.x, (int) st.pose.z, compass(st.pose.yaw), mood);
        if (c.getSource().getEntity() instanceof ServerPlayer p) {
            if (p.level().dimension() == ColossalTurtle.SHELL) msg = "You're on him. He's walking " + compass(st.pose.yaw) + ". He's " + mood + ".";
            else {
                double dx = st.pose.x - p.getX(), dz = st.pose.z - p.getZ();
                msg += String.format(" That's %d blocks %s of you.", (int) Math.sqrt(dx * dx + dz * dz), compass(Math.atan2(dz, dx)));
            }
        }
        String out = msg;
        c.getSource().sendSuccess(() -> Component.literal(out), false);
        return 1;
    }

    private static String compass(double a) {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        int i = (int) Math.round(a / (Math.PI / 4));
        return names[Math.floorMod(i, 8)];
    }

    private static int ride(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ServerLevel shell = c.getSource().getServer().getLevel(ColossalTurtle.SHELL);
        if (shell == null) return 0;
        TurtleShape.Column col = TurtleShape.column(-600, 300);
        TurtleWorld.move(p, shell, -600.5, col.top + 1, 300.5, 0, Vec3.ZERO, shell.getGameTime());
        return 1;
    }

    private static int bring(CommandContext<CommandSourceStack> c) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        TurtleState st = state(c);
        double look = Math.toRadians(p.getYRot() + 90);
        double x = p.getX() + Math.cos(look) * 6000, z = p.getZ() + Math.sin(look) * 6000;
        st.moveTo(c.getSource().getServer().overworld(), x, z, look + Math.PI / 2);
        c.getSource().sendSuccess(() -> Component.literal("He's 6,000 blocks in front of you, walking across your view."), true);
        return 1;
    }

    private static int mood(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "mood");
        int m = java.util.Arrays.asList(MOODS).indexOf(name);
        if (m < 0) {
            c.getSource().sendFailure(Component.literal("Moods: calm, scared, tired, hiding"));
            return 0;
        }
        TurtleState st = state(c);
        int ticks = m == Gait.TIRED ? 24000 : m == Gait.CALM ? 0 : 4800;
        st.setMood(m, ticks, c.getSource().getServer().overworld().getGameTime());
        c.getSource().sendSuccess(() -> Component.literal("He's " + name + " now."), true);
        return 1;
    }
}
