package annina.sparkstrength.client.input;

import annina.sparkstrength.client.role.bomber.DronePilotClient;
import annina.sparkstrength.client.role.engineer.EngineerStunClient;
import annina.sparkstrength.client.role.partyanimal.PartygoerTaotieClient;
import annina.sparkstrength.client.role.perfumer.PerfumerClientEffects;
import annina.sparkstrength.client.sabotage.BlackoutSightClient;
import annina.sparkstrength.role.partyanimal.PartygoerTaotieRules;
import net.minecraft.client.option.KeyBinding;

import java.util.function.BooleanSupplier;

/**
 * The client key gate: every SparkStrength rule that changes what a {@link KeyBinding} reports, in one fixed order. The
 * {@code mixin/input} hooks only forward here. The order is the one the separate hooks it replaces ran in (same-point
 * injectors of one mixin config run in config order), so results and side effects are unchanged:
 * <ol>
 *   <li>{@code onKeyPressed} HEAD: an Engineer stun drops the press, so nothing is queued.</li>
 *   <li>{@code isPressed} / {@code wasPressed} HEAD: an Engineer stun reads every key as released and
 *   {@code wasPressed} drains the queue, so nothing fires when the stun ends. The call returns at once: nothing below,
 *   and no RETURN hook of another mod, sees it.</li>
 *   <li>{@code isPressed} RETURN ({@link #held}): drone pilot, then Cooling Oil, then blackout release Wathe's instinct
 *   key.</li>
 *   <li>{@code wasPressed} RETURN, three hooks: drone pilot releases the instinct key ({@link #clickedPastDrone}), then
 *   the Taotie's Partygoer claim ({@link #claimsClick}: sends {@code partygoer_swallow} and cancels, so the call
 *   returns false at once and no later RETURN hook, ours or another mod's or the carry lock, runs), then Cooling Oil
 *   releases the instinct key ({@link #clickedPastCoolingOil}). The claim must stay a cancelling hook between the two:
 *   folding it into one return-value hook would let the later hooks run on a claimed press.</li>
 *   <li>{@code StickyKeyBinding#setPressed} HEAD ({@link #ignoresStickyPress}): a toggle sneak/sprint press during an
 *   Engineer stun is ignored, so the latch keeps its state from before the stun.</li>
 * </ol>
 * The RETURN rules only turn true into false and the original methods still run, so a buffered click is consumed, not
 * replayed later. The Partygoer carry lock is not here: it stays in {@code PartygoerCarriedKeyMixin} at priority 5000,
 * after every other mod's RETURN hook. Key bindings are polled from the start: this class holds no state, and each
 * feature class is first touched at the same moment its own hook used to touch it.
 * 客户端按键闸门：所有改变 {@link KeyBinding} 结果的 SparkStrength 规则，按固定顺序集中在这里；{@code mixin/input} 中的钩子
 * 只负责转发。顺序与被取代的各个钩子原先的执行顺序一致（同一 mixin 配置中同一注入点的注入器按配置顺序执行），因此结果与
 * 副作用都不变：
 * 1. onKeyPressed 的 HEAD：工程师定身时丢弃按下，不入队。
 * 2. isPressed / wasPressed 的 HEAD：工程师定身时所有按键读作未按下，wasPressed 还会清空队列，定身结束时不会补发。调用立即
 * 返回：下面的规则以及其他模组的 RETURN 钩子都看不到这次调用。
 * 3. isPressed 的 RETURN（held）：无人机驾驶、风油精、熄灯依次松开 Wathe 本能键。
 * 4. wasPressed 的 RETURN，三个钩子：无人机驾驶松开本能键（clickedPastDrone），然后是饕餮对派对客的认领（claimsClick：发送
 * partygoer_swallow 并取消，调用立即返回 false，之后的 RETURN 钩子——我们的、其他模组的、扛人锁——都不会执行），最后风油精
 * 松开本能键（clickedPastCoolingOil）。认领必须保持为夹在两者之间的可取消钩子：若并入单个返回值钩子，被认领的按下也会让
 * 后续钩子执行。
 * 5. StickyKeyBinding#setPressed 的 HEAD（ignoresStickyPress）：工程师定身期间切换式潜行/疾跑的按下被忽略，锁定保持定身前的
 * 状态。
 * RETURN 规则只会把 true 改成 false，原方法照常执行，缓存的点击会被消耗而不会事后补发。派对客扛人锁不在这里：它留在优先级
 * 5000 的 PartygoerCarriedKeyMixin 中，排在其他模组所有 RETURN 钩子之后。按键从启动起就会被查询：本类不持有状态，各功能类
 * 首次被触及的时机与它们原先各自的钩子相同。
 */
public final class ClientKeyGate {
    private ClientKeyGate() {
    }

    /** {@code onKeyPressed} HEAD: true drops the press. / 为 true 时丢弃这次按下。 */
    public static boolean dropsPress() {
        return EngineerStunClient.isLocalPlayerStunned();
    }

    /**
     * {@code isPressed} / {@code wasPressed} HEAD: true reads the key as released (and drains its queue).
     * 为 true 时按键读作未按下（并清空其队列）。
     */
    public static boolean releasesEveryKey() {
        return EngineerStunClient.isLocalPlayerStunned();
    }

    /** {@code isPressed} RETURN. / isPressed 的 RETURN。 */
    public static boolean held(KeyBinding key, boolean pressed) {
        return pressed
                && !DronePilotClient.blocksInstinctKey(key)
                && !PerfumerClientEffects.blocksInstinctKey(key)
                && !BlackoutSightClient.releasesKey(key);
    }

    /** {@code wasPressed} RETURN, first hook. / wasPressed 的 RETURN，第一个钩子。 */
    public static boolean clickedPastDrone(KeyBinding key, boolean pressed) {
        return pressed && !DronePilotClient.blocksInstinctKey(key);
    }

    /**
     * {@code wasPressed} RETURN, second hook: true when the press is claimed (and {@code partygoer_swallow} was sent,
     * if the claim swallows); the hook then cancels with false. The Taotie claim coexists with SparkWitch's
     * {@code SeekerTaotieAbilityKeyMixin} on the same key in either order: each claims only its own entity, and
     * whichever claims first leaves the other a false result. A Control Expert stun cancels at HEAD like the Engineer
     * stun, so nothing is claimed then.
     * wasPressed 的 RETURN，第二个钩子：按下被认领时为 true（若认领要吞噬，已发送 partygoer_swallow），钩子随即以 false 取消。
     * 饕餮认领与同一按键上 SparkWitch 的 SeekerTaotieAbilityKeyMixin 以任意顺序共存：各自只认领自己的实体，先认领的一方让另一方
     * 读到 false。控场专家眩晕与工程师定身一样在 HEAD 处取消，此时不会认领。
     */
    public static boolean claimsClick(KeyBinding key, boolean pressed) {
        int claim = PartygoerTaotieClient.claimPress(key, pressed);
        if (claim == PartygoerTaotieRules.NO_CLAIM) {
            return false;
        }
        PartygoerTaotieClient.send(claim);
        return true;
    }

    /** {@code wasPressed} RETURN, third hook. / wasPressed 的 RETURN，第三个钩子。 */
    public static boolean clickedPastCoolingOil(KeyBinding key, boolean pressed) {
        return pressed && !PerfumerClientEffects.blocksInstinctKey(key);
    }

    /** {@code StickyKeyBinding#setPressed} HEAD: true ignores the call. / 为 true 时忽略这次调用。 */
    public static boolean ignoresStickyPress(boolean pressed, BooleanSupplier toggled) {
        return pressed && toggled.getAsBoolean() && EngineerStunClient.isLocalPlayerStunned();
    }
}
