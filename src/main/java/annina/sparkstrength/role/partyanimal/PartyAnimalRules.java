package annina.sparkstrength.role.partyanimal;

import annina.sparkstrength.SparkStrength;
import dev.doctor4t.wathe.api.Role;
import net.minecraft.util.Identifier;
import org.agmas.noellesroles.Noellesroles;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for the Party Animal's second skill, the Partygoer (派对客) summon. Owner rules, 2026-10-21:
 * 派对狂二技能「派对客」召唤的纯规则。主人 2026-10-21 定下的规则：
 * <ul>
 *   <li>50 coins, unlocked by 2 tasks done as the Party Animal, 60 s cooldown once the Partygoer is gone, one at a
 *   time. / 50 金币；当派对狂期间做完 2 个任务解锁；派对客离场后冷却 60 秒；同时只能有一只。</li>
 *   <li>Hunts at Speed II, carries at Speed IV, rages at Speed V (no particles). / 追人速度 II，扛人速度 IV，狂怒速度 V（无粒子）。</li>
 *   <li>Eats for 5 s plus 5 s per shield layer. / 吃人 5 秒，每层盾加 5 秒。</li>
 *   <li>Prey: alive, playing, helium- or balloon-affected, not the summoner, not the killer faction, and an Undercover
 *   only from stage 2. / 猎物：存活、在局内、受氦气或气球影响，不是召唤者，不是杀手阵营；卧底从二阶段开始才算。</li>
 *   <li>Rage triggers at most once per Partygoer. / 每只派对客最多狂怒一次。</li>
 *   <li>Killed by anything that can kill a player, in one hit: the killer gets 25 coins and 20% sanity.
 *   / 任何能杀死玩家的东西一击即可打死它：击杀者获得 25 金币与 20% 理智。</li>
 * </ul>
 */
public final class PartyAnimalRules {
    public static final Identifier PARTY_ANIMAL_ID = Identifier.of("noellesroles", "party_animal");

    public static final int SKILL_PRICE = 50;
    public static final int UNLOCK_TASKS = 2;
    public static final int COOLDOWN_TICKS = 60 * 20;

    /** Player walking speed in blocks per tick (0.1 movement attribute = 4.317 b/s). / 玩家步行速度（格/刻）。 */
    public static final double WALK_BLOCKS_PER_TICK = 4.317 / 20.0;
    public static final int HUNT_SPEED_LEVEL = 2;
    public static final int CARRY_SPEED_LEVEL = 4;
    public static final int RAGE_SPEED_LEVEL = 5;

    public static final int EAT_TICKS_PER_LAYER = 5 * 20;
    /** Eating sound and particles every this many ticks, like a player eating. / 进食声与粒子间隔，与玩家进食相同。 */
    public static final int EAT_SOUND_INTERVAL = 4;
    public static final int HUNT_TIMEOUT_TICKS = 60 * 20;
    public static final int RAGE_STUN_TICKS = 2 * 20;
    public static final int RAGE_CHASE_TIMEOUT_TICKS = 30 * 20;

    /** Grab when the prey's box comes this close (horizontal, between centres). / 猎物水平中心距离不超过此值时抓住。 */
    public static final double GRAB_DISTANCE = 1.1;
    public static final double GRAB_VERTICAL = 1.5;

    public static final int KILL_REWARD_COINS = 25;
    public static final float KILL_REWARD_SANITY = 0.2F;

    /** The Taotie that swallows a Partygoer: Slowness IV, then dies after 5 s. / 吞下派对客的饕餮：缓慢 IV，5 秒后死亡。 */
    public static final int TAOTIE_DEATH_TICKS = 5 * 20;
    public static final int TAOTIE_SLOWNESS_AMPLIFIER = 3;

    /** Sounds reach about 24 blocks (vanilla range = 16 x volume). / 声音约 24 格可闻（原版范围 = 16 x 音量）。 */
    public static final float SOUND_VOLUME = 1.5F;

    public static final Identifier DEATH_REASON_EATEN = SparkStrength.id("partygoer_eaten");
    public static final Identifier DEATH_REASON_KICKED = SparkStrength.id("partygoer_kicked");

    private PartyAnimalRules() {
    }

    public static boolean isPartyAnimal(@Nullable Role role) {
        return role != null && (role == Noellesroles.PARTY_ANIMAL || PARTY_ANIMAL_ID.equals(role.identifier()));
    }

    /** Speed level n adds 20% per level, like the vanilla Speed effect. / 速度等级 n 每级加 20%，与原版速度效果一致。 */
    public static double blocksPerTick(int speedLevel) {
        return WALK_BLOCKS_PER_TICK * (1.0 + 0.2 * Math.max(0, speedLevel));
    }

    /** 5 s for the meal plus 5 s per shield layer. / 5 秒本体加每层盾 5 秒。 */
    public static int eatTicks(int shieldLayers) {
        return EAT_TICKS_PER_LAYER * (Math.max(0, shieldLayers) + 1);
    }

    /** Tasks still missing before the skill unlocks. / 解锁还差的任务数。 */
    public static int remainingTasks(int completedTasks) {
        return Math.max(0, UNLOCK_TASKS - completedTasks);
    }

    public static boolean isUnlocked(int completedTasks) {
        return completedTasks >= UNLOCK_TASKS;
    }

    /**
     * Whether a player may be hunted. {@code affected} = helium buzz active or balloon-marked. The Undercover reads as a
     * killer until stage 2, so before that it is left alone like a real killer (hunting it would out it).
     * 玩家能否被猎捕。affected = 氦气生效或被气球标记。卧底在二阶段前看起来是杀手，因此像真杀手一样不抓（否则会暴露身份）。
     */
    public static boolean isValidPrey(boolean aliveAndPlaying, boolean swallowed, boolean isSummoner,
                                      boolean effectiveKiller, boolean undercover, boolean undercoverStage2,
                                      boolean affected, boolean alreadyCarried) {
        if (!aliveAndPlaying || swallowed || isSummoner || alreadyCarried || !affected) {
            return false;
        }
        if (undercover) {
            return undercoverStage2;
        }
        return !effectiveKiller;
    }

    /**
     * Rage needs the prey in hand (carried or being eaten), rage still unused, and a living killer other than the
     * prey; otherwise the Partygoer gives up and vanishes.
     * 狂怒需要猎物在手中（被扛着或正在被吃）、狂怒未用过，且击杀者是还活着的、不是猎物本人的玩家；否则派对客放弃并消失。
     */
    public static boolean triggersRage(boolean preyInHand, boolean rageUsed, boolean killerKnown, boolean killerAlive,
                                       boolean killerIsPrey) {
        return preyInHand && !rageUsed && killerKnown && killerAlive && !killerIsPrey;
    }

    /** HUD/sync cadence: once per second, plus the last tick. / HUD 同步节奏：每秒一次，以及最后一刻。 */
    public static boolean shouldSyncCooldown(int ticks) {
        return ticks % 20 == 0 || ticks == 1;
    }
}
