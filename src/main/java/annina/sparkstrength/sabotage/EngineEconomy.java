package annina.sparkstrength.sabotage;

import annina.sparkstrength.compat.SparkTraitsCompat;
import annina.sparkstrength.mixin.wathe.ShopEntryAccessor;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameConstants;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.Optional;

/**
 * The killer economy while the engine is broken, from the wreck until the repair (emergency power included): Wathe's
 * killer passive income pays three times as often and shop gold prices are 20% off. Eligible are exactly the players
 * Wathe's own killer passive income pays ({@code canUseKillerFeatures}, real role), minus Conscience killers; the
 * Impostor, Undercover, witch faction, neutrals and innocents are outside {@code canUseKillerFeatures} already.
 * 动力损毁期间的杀手经济，从损毁一直到修好（应急动力期间也算）：Wathe 的杀手被动收入发放频率变为三倍，商店金币价打 8 折。
 * 享有加成的正好是 Wathe 原生杀手被动收入发放的对象（canUseKillerFeatures，真实身份），排除善良杀手；内鬼、卧底、魔女阵营、
 * 中立和好人本来就不在 canUseKillerFeatures 之内。
 *
 * <p>The shop discount lives on {@code ShopEntry.price()} and only applies in two scopes, so every reader in them (price
 * tags, info cards, Charisma on top, team funds, bookkeeping) sees the same price: on the server inside
 * {@code PlayerShopComponent.tryBuy} for an eligible buyer, and on the client thread while the server says the local
 * player is eligible. The integrated server runs on another thread of the same JVM, so it never sees the client flag;
 * if a purchase races the repair, the server price wins.
 * 商店折扣挂在 ShopEntry.price() 上，只在两个作用域里生效，因此其中每个读取方（价签、信息卡、叠在上面的魅力、团队资金、记账）
 * 看到的都是同一个价格：服务端在 PlayerShopComponent.tryBuy 内且买家有资格时；客户端在客户端线程上、且服务端说本地玩家有资格时。
 * 集成服务端运行在同一 JVM 的另一个线程上，所以永远看不到客户端标记；购买和修复同时发生时，以服务端价格为准。</p>
 */
public final class EngineEconomy {
    /** Whether the purchase running on this thread is discounted; unset outside tryBuy. / 本线程上正在进行的购买是否打折；tryBuy 之外未设置。 */
    private static final ThreadLocal<Boolean> PURCHASE_DISCOUNT = new ThreadLocal<>();
    /** The client's render thread, registered by the client init; null on a dedicated server. / 客户端渲染线程，由客户端初始化登记；专用服务端上为 null。 */
    private static volatile @Nullable Thread clientThread;
    /** The local player's bonus from the last status packet. / 最近一次状态包里本地玩家的加成。 */
    private static volatile boolean clientBonus;
    /** Wrappers of wrappers are followed this deep at most. / 包装的包装最多追这么多层。 */
    private static final int MAX_WRAPPING_DEPTH = 4;
    /** Per entry class, the field holding the entry it wraps (see EngineRules#wrappedEntryField). / 每个条目类里存放被包装条目的字段。 */
    private static final ClassValue<Optional<Field>> WRAPPED_ENTRY = new ClassValue<>() {
        @Override
        protected Optional<Field> computeValue(Class<?> type) {
            Field field = EngineRules.wrappedEntryField(type, ShopEntry.class);
            if (field != null) {
                try {
                    field.setAccessible(true);
                } catch (RuntimeException ignored) {
                    // Still a wrapper; only the display tint cannot look inside. / 仍是包装条目，只是显示染色无法看到内部。
                }
            }
            return Optional.ofNullable(field);
        }
    };

    private EngineEconomy() {
    }

    /** The bonus runs in this world now: an active round, the engine wrecked or on emergency power. / 本世界当前处于加成期：进行中的对局，动力已损毁或处于应急动力。 */
    public static boolean isBoosted(ServerWorld world) {
        EnginePhase phase = EngineSabotageService.phase(world);
        return phase != null && phase.boostsKillerEconomy()
                && GameWorldComponent.KEY.get(world).getGameStatus() == GameWorldComponent.GameStatus.ACTIVE;
    }

    /** Server: this player gets the bonus right now. / 服务端：此玩家当前享有加成。 */
    public static boolean isEligible(ServerPlayerEntity player) {
        if (player == null) {
            return false;
        }
        ServerWorld world = player.getServerWorld();
        return isBoosted(world) && GameWorldComponent.KEY.get(world).canUseKillerFeatures(player)
                && !SparkTraitsCompat.hasConscience(player);
    }

    /**
     * The value of Wathe's {@code PASSIVE_MONEY_TICKER} call for {@code player} in the killer branch of
     * {@code tickServerGameLoop}. On the two extra ticks of each period where Wathe pays nothing, an eligible player gets
     * what the ticker pays on its own payout tick, through exactly the same credit path (balance cap, killer team funds
     * exclusion). Wathe's own payout and every other tick keep the original.
     * tickServerGameLoop 杀手分支里 Wathe 对 player 调用 PASSIVE_MONEY_TICKER 的结果。在每个周期 Wathe 不发钱的两个额外 tick 上，
     * 有资格的玩家得到计时器在它自己发放 tick 上给的金额，走的入账路径完全相同（余额上限、不计入杀手团队资金）。Wathe 自己的
     * 发放和其他所有 tick 都保留原值。
     */
    public static Object passiveIncome(Object original, ServerWorld world, ServerPlayerEntity player) {
        long now = world.getTime();
        if (!EngineRules.boostedIncomeTick(now) || !(original instanceof Integer amount) || amount != 0
                || !isEligible(player)) {
            return original;
        }
        return GameConstants.PASSIVE_MONEY_TICKER.apply(EngineRules.alignedPayoutTick(now));
    }

    /**
     * Runs one {@code tryBuy} with the buyer's discount decided once, so every price read of this purchase agrees.
     * Restores the previous scope afterwards, so a nested purchase cannot leak into the outer one.
     * 执行一次 tryBuy，买家的折扣只判定一次，让这次购买里的每次价格读取都一致。结束后恢复之前的作用域，嵌套的购买不会影响外层。
     */
    public static void purchase(ServerPlayerEntity buyer, Runnable purchase) {
        Boolean previous = PURCHASE_DISCOUNT.get();
        PURCHASE_DISCOUNT.set(isEligible(buyer));
        try {
            purchase.run();
        } finally {
            if (previous == null) {
                PURCHASE_DISCOUNT.remove();
            } else {
                PURCHASE_DISCOUNT.set(previous);
            }
        }
    }

    /**
     * {@code ShopEntry.price()} of {@code entry} as read on this thread right now. A wrapper entry (SparkTraits' Charisma)
     * stores a price it took from the wrapped entry's {@code price()}, which already carried the discount, so it is not
     * discounted again: "20% off first, then Charisma" on both sides.
     * 当前在本线程上读取到的 entry 的 ShopEntry.price()。包装条目（SparkTraits 的魅力）保存的价格取自被包装条目的 price()，
     * 那时已经带上了折扣，所以不再打折：两端都是「先 8 折、再魅力」。
     */
    public static int price(ShopEntry entry, int listed) {
        if (!discountApplies() || wrapsAnotherEntry(entry)) {
            return listed;
        }
        return EngineRules.discountedPrice(listed);
    }

    /**
     * Client display: the price shown for {@code entry} is discounted, i.e. the discount changes the listed price of the
     * entry it ultimately wraps. False for prices it leaves alone (0 for stamps and mana, under 5).
     * 客户端显示：entry 显示的价格打了折，即折扣改变了它最终包装的那个条目的上架价格。折扣不改变的价格（邮票和魔力的 0、低于 5）
     * 返回 false。
     */
    public static boolean shownDiscounted(ShopEntry entry) {
        if (!discountApplies()) {
            return false;
        }
        ShopEntry listed = entry;
        for (int depth = 0; wrapsAnotherEntry(listed); depth++) {
            Optional<Field> field = WRAPPED_ENTRY.get(listed.getClass());
            if (depth >= MAX_WRAPPING_DEPTH || field.isEmpty()) {
                return false;
            }
            try {
                if (!(field.get().get(listed) instanceof ShopEntry inner)) {
                    return false;
                }
                listed = inner;
            } catch (ReflectiveOperationException | RuntimeException unreadable) {
                return false;
            }
        }
        int price = ((ShopEntryAccessor) listed).sparkstrength$listedPrice();
        return EngineRules.discountedPrice(price) != price;
    }

    private static boolean wrapsAnotherEntry(ShopEntry entry) {
        return entry.getClass() != ShopEntry.class && WRAPPED_ENTRY.get(entry.getClass()).isPresent();
    }

    /**
     * The client thread follows the server's flag for the local player; any other thread (the server, the integrated
     * server) only inside a discounted purchase.
     * 客户端线程跟随服务端给本地玩家的标记；其他线程（服务端、集成服务端）只在打折的购买之内。
     */
    private static boolean discountApplies() {
        Thread client = clientThread;
        if (client != null && Thread.currentThread() == client) {
            return clientBonus;
        }
        return Boolean.TRUE.equals(PURCHASE_DISCOUNT.get());
    }

    /** Client init, on the render thread. / 客户端初始化时在渲染线程上调用。 */
    public static void registerClientThread(Thread thread) {
        clientThread = thread;
    }

    /** Client: the server's verdict for the local player, from the status packet. / 客户端：状态包里服务端对本地玩家的判定。 */
    public static void setClientBonus(boolean bonus) {
        clientBonus = bonus;
    }

    public static boolean clientBonus() {
        return clientBonus;
    }
}
