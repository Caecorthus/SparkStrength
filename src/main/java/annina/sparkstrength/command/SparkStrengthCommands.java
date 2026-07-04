package annina.sparkstrength.command;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.tablet.TabletRules;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

/**
 * Registers SparkStrength administrator commands.
 * 注册 SparkStrength 管理命令。
 */
public final class SparkStrengthCommands {
    private static final int DEFAULT_COMMAND_LEVEL = 2;

    private SparkStrengthCommands() {
    }

    public static void register(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(CommandManager.literal("sparkstrength:emergencyMeetingChances")
                .requires(source -> source.hasPermissionLevel(DEFAULT_COMMAND_LEVEL))
                .then(CommandManager.argument("num", IntegerArgumentType.integer(0))
                        .executes(context -> setEmergencyMeetingChances(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "num")
                        ))));
        // Keep the requested misspelled command as an alias beside the canonical name.
        // 保留需求中写出的拼写作为别名，同时提供正确拼写。
        registerVoteTimeCommand(dispatcher, "sparkstrength:voteTime");
        registerVoteTimeCommand(dispatcher, "sparkstength:voteTime");
    }

    private static int setEmergencyMeetingChances(ServerCommandSource source, int chances) {
        for (ServerWorld world : source.getServer().getWorlds()) {
            TabletWorldComponent.KEY.get(world).setEmergencyMeetingChances(chances);
        }
        source.sendFeedback(
                () -> Text.translatable("commands.sparkstrength.emergency_meeting_chances.success", chances),
                true
        );
        return chances;
    }

    private static void registerVoteTimeCommand(CommandDispatcher<ServerCommandSource> dispatcher, String literal) {
        dispatcher.register(CommandManager.literal(literal)
                .requires(source -> source.hasPermissionLevel(DEFAULT_COMMAND_LEVEL))
                .then(CommandManager.argument("sec", IntegerArgumentType.integer(1, Integer.MAX_VALUE / 20))
                        .executes(context -> setVoteTime(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "sec")
                        ))));
    }

    private static int setVoteTime(ServerCommandSource source, int seconds) {
        int ticks = TabletRules.ticksFromSeconds(seconds);
        for (ServerWorld world : source.getServer().getWorlds()) {
            TabletWorldComponent.KEY.get(world).setMeetingDurationTicks(ticks);
        }
        source.sendFeedback(
                () -> Text.translatable("commands.sparkstrength.vote_time.success", seconds),
                true
        );
        return seconds;
    }
}
