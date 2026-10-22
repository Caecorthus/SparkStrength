package annina.sparkstrength.replay;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.record.replay.ReplayRegistry;
import net.minecraft.util.Identifier;

/**
 * Replay lines for the Party Animal's Partygoer and Party Balloon.
 * 派对狂的派对客与派对气球的回放文本。
 */
public final class PartyAnimalReplayEvents {
    /** Party Balloon given: actor = the Party Animal, target = the recipient. / 塞出派对气球：actor 为派对狂，target 为接收者。 */
    public static final Identifier PARTY_BALLOON_GIVEN = SparkStrength.id("party_balloon_given");
    /** Partygoer summoned: actor = the Party Animal. / 召唤派对客：actor 为派对狂。 */
    public static final Identifier PARTYGOER_SUMMONED = SparkStrength.id("partygoer_summoned");
    /** Partygoer grabbed someone: actor = its Party Animal, target = the prey. / 派对客抓住了人：actor 为其派对狂，target 为猎物。 */
    public static final Identifier PARTYGOER_GRABBED = SparkStrength.id("partygoer_grabbed");
    /** Partygoer raged: actor = its Party Animal, target = the prey's killer. / 派对客狂怒：actor 为其派对狂，target 为猎物的击杀者。 */
    public static final Identifier PARTYGOER_RAGED = SparkStrength.id("partygoer_raged");
    /** Partygoer killed: actor = the attacker (absent when offline), target = its Party Animal. / 派对客被打死：actor 为攻击者（离线时缺省），target 为其派对狂。 */
    public static final Identifier PARTYGOER_KILLED = SparkStrength.id("partygoer_killed");
    /** Partygoer swallowed: actor = the Taotie, target = its Party Animal. / 派对客被吞：actor 为饕餮，target 为其派对狂。 */
    public static final Identifier PARTYGOER_SWALLOWED = SparkStrength.id("partygoer_swallowed");

    private PartyAnimalReplayEvents() {
    }

    static void register() {
        ReplayRegistry.registerGlobalEventFormatter(PARTYGOER_SUMMONED,
                (event, match, world) -> ReplayText.onePlayerEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.partygoer_summoned"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PARTYGOER_GRABBED,
                (event, match, world) -> ReplayText.throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.partygoer_grabbed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PARTYGOER_RAGED,
                (event, match, world) -> ReplayText.throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.partygoer_raged"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PARTYGOER_KILLED,
                (event, match, world) -> ReplayText.throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.partygoer_killed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PARTYGOER_SWALLOWED,
                (event, match, world) -> ReplayText.throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.partygoer_swallowed"
                ));
        ReplayRegistry.registerGlobalEventFormatter(PARTY_BALLOON_GIVEN,
                (event, match, world) -> ReplayText.throwableHitEvent(
                        event.data(),
                        match,
                        "replay.global.sparkstrength.party_balloon_given"
                ));
    }
}
