package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.util.Identifier;

/**
 * Replay lines for the Jester's transformation and fake death.
 * 小丑转变与假死的回放文本。
 */
public final class JesterReplayEvents {
    public static final Identifier JESTER_POSITIONS_SHUFFLED = SparkStrength.id("jester_positions_shuffled");
    public static final Identifier JESTER_FAKE_DEATH_KILL_BLOCKED = SparkStrength.id("jester_fake_death_kill_blocked");

    private JesterReplayEvents() {
    }

    static void register() {
        // Actor = the Jester whose transformation shuffled everyone. / actor 为开始转变、打乱所有人位置的小丑。
        ReplayRegistry.registerGlobalEventFormatter(JESTER_POSITIONS_SHUFFLED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.jester_positions_shuffled"
                ));
        // Actor = the Jester whose fake corpse shrugged off a kill. / actor 为假尸体挡下一次击杀的小丑。
        ReplayRegistry.registerGlobalEventFormatter(JESTER_FAKE_DEATH_KILL_BLOCKED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.jester_fake_death_kill_blocked"
                ));
    }
}
