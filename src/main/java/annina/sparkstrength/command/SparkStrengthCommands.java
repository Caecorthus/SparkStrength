package annina.sparkstrength.command;

import annina.sparkstrength.component.tablet.TabletWorldComponent;
import annina.sparkstrength.role.detective.DetectiveCaseRules;
import annina.sparkstrength.role.detective.DetectiveCaseService;
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
        // Brigadier literals are case-sensitive, so the camel-case namespace is registered as a separate alias.
        // Brigadier 字面量区分大小写，因此驼峰写法的命名空间需单独注册为别名。
        registerDetectiveCaseLimitCommand(dispatcher, "sparkstrength:detectiveCaseLimit");
        registerDetectiveCaseLimitCommand(dispatcher, "sparkStrength:detectiveCaseLimit");
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

    private static void registerDetectiveCaseLimitCommand(CommandDispatcher<ServerCommandSource> dispatcher, String literal) {
        dispatcher.register(CommandManager.literal(literal)
                .requires(source -> source.hasPermissionLevel(DEFAULT_COMMAND_LEVEL))
                .executes(context -> queryDetectiveCaseLimit(context.getSource()))
                .then(CommandManager.argument("num", IntegerArgumentType.integer(
                                DetectiveCaseRules.MIN_SUSPECT_LIMIT,
                                DetectiveCaseRules.MAX_SUSPECT_LIMIT
                        ))
                        .executes(context -> setDetectiveCaseLimit(
                                context.getSource(),
                                IntegerArgumentType.getInteger(context, "num")
                        ))));
    }

    private static int queryDetectiveCaseLimit(ServerCommandSource source) {
        int limit = DetectiveCaseService.getSuspectLimit(source.getWorld());
        source.sendFeedback(
                () -> Text.translatable("commands.sparkstrength.detective_case_limit.query", limit),
                false
        );
        return limit;
    }

    private static int setDetectiveCaseLimit(ServerCommandSource source, int limit) {
        DetectiveCaseService.setSuspectLimit(source.getServer(), limit);
        source.sendFeedback(
                () -> Text.translatable("commands.sparkstrength.detective_case_limit.success", limit),
                true
        );
        return limit;
    }
}
