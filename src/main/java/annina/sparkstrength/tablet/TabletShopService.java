package annina.sparkstrength.tablet;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkFactionCompat;
import annina.sparkstrength.compat.SparkTraitsCompat;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.ShopPurchase;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.util.ShopEntry;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Adds the SparkStrength tablet to eligible roles' shop.
 * 给符合条件的角色追加 SparkStrength 平板商店项。
 */
public final class TabletShopService {
    private static final String ALREADY_OWNED_KEY = "message.sparkstrength.tablet.already_owned";
    private static final String UNDERCOVER_GRANTED_KEY = "message.sparkstrength.tablet.undercover_granted";
    private static boolean registered;

    private TabletShopService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        SparkFactionCompat.registerCorruptCopPoliceRole();
        // No BuildShopEntries listener: SparkWitch witch/killer shops and NoellesRoles/SparkWitch civilian shops
        // call clearEntries() and cross-mod listener order is not guaranteed. Purchases resolve by list index,
        // so the tablet is appended last after every listener (M67ShopEntriesMixin -> appendToFinalEntries).
        // 不注册 BuildShopEntries 监听：SparkWitch 魔女/杀手商店与 NoellesRoles/SparkWitch 平民商店会 clearEntries()，
        // 且跨模组监听顺序无保证。购买按列表下标解析，因此平板在所有监听器之后追加到末尾。
        //
        // Deny-only (never allow, so later listeners still run). Wathe prints the deny reason on the action bar;
        // a false onBuy would instead be overwritten by Wathe's generic purchase_failed message in the same tick.
        // 只拒绝不放行（不会跳过后续监听器）。Wathe 会把拒绝原因显示在动作栏；若仅靠 onBuy 返回 false，
        // 提示会在同一 tick 被 Wathe 的通用 purchase_failed 覆盖。
        ShopPurchase.BEFORE.register(TabletShopService::denyDuplicateTablet);
    }

    /**
     * Grants the Undercover its free killer-network tablet for roles assigned mid-round (RoleAssigned while ACTIVE);
     * round-start roles are granted by {@link #grantStarterTablets} once they are final.
     * 为局中分配的身份（ACTIVE 状态下的 RoleAssigned）发放卧底的免费杀手网络平板；开局身份在最终确定后由
     * grantStarterTablets 发放。
     */
    public static void assignForRole(ServerPlayerEntity player, Role role) {
        // SparkTraits Conscience compensation may convert an Undercover after RoleAssigned during initialization;
        // grant only for the final role.
        // SparkTraits 良心补偿可能在初始化期间的 RoleAssigned 之后改写卧底身份；只按最终身份发放。
        if (GameWorldComponent.KEY.get(player.getWorld()).getGameStatus() != GameWorldComponent.GameStatus.ACTIVE) {
            return;
        }
        grantStarterTablet(player, role);
    }

    /**
     * Round-start grant from {@code GameEvents.ON_FINISH_INITIALIZE}: runs after every RoleAssigned of Wathe's
     * initializeGame (including trait compensation) and before the round becomes ACTIVE.
     * 开局发放（ON_FINISH_INITIALIZE）：在 Wathe initializeGame 的所有 RoleAssigned（含天赋补偿）之后、
     * 对局变为 ACTIVE 之前执行。
     */
    public static void grantStarterTablets(ServerWorld world, GameWorldComponent game) {
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (game.hasAnyRole(player)) {
                grantStarterTablet(player, game.getRole(player));
            }
        }
    }

    private static void grantStarterTablet(ServerPlayerEntity player, Role role) {
        if (TabletShopRules.shouldGrantStarterTablet(role, hasTabletAnywhere(player))) {
            player.giveItemStack(new ItemStack(SparkStrengthItems.tablet()));
            player.sendMessage(Text.translatable(UNDERCOVER_GRANTED_KEY), false);
        }
    }

    public static boolean isTabletEconomyEligible(PlayerEntity player) {
        if (player == null) {
            return false;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        // Impostor is an independent optional trait override, including for Veteran; no income is granted here.
        // 叛徒是独立的可选天赋覆盖（包括老兵）；此处不授予任何收入。
        return TabletShopRules.canBuyTabletRole(role) || SparkTraitsCompat.hasImpostor(player);
    }

    /**
     * Appends the tablet to Wathe's final shop list; must give the same answer on client and server because
     * Wathe rebuilds the list server-side and buys by index. Called from {@code M67ShopEntriesMixin}.
     * 将平板追加到 Wathe 最终商店列表末尾；客户端与服务端必须得出相同结果，因为 Wathe 会在服务端重建列表并按下标购买。
     *
     * <p>Deliberately not {@code GameFunctions.isPlayerPlayingAndAlive}: Wathe's {@code initializeGame} runs
     * {@code initializeShopsForPlayers} (which caches stock from this list) before the status becomes ACTIVE,
     * so a running-state gate would drop the round-start {@code stock(1)}. Only the synced dead set is checked.
     * 刻意不使用 isPlayerPlayingAndAlive：Wathe 在状态变为 ACTIVE 之前就调用 initializeShopsForPlayers 缓存库存，
     * 依赖运行状态会丢失开局的 stock(1)。这里只检查已同步的死亡集合。</p>
     */
    public static List<ShopEntry> appendToFinalEntries(PlayerEntity player, List<ShopEntry> entries) {
        if (player == null) {
            return entries;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(player.getWorld());
        // Undercover's tablet is granted at role assignment and it has no money, so it is never listed here.
        // 卧底的平板在身份分配时发放且其没有金钱，因此这里从不列出。
        if (game.isPlayerDead(player.getUuid()) || TabletShopRules.isUndercover(game.getRole(player))) {
            return entries;
        }
        // A non-empty identity set implies a real round role (TabletChannelRules.Facts#hasRole).
        // 身份频道集非空即意味着拥有有效的局内身份。
        EnumSet<TabletChannel> allowed = TabletChannelResolver.identityChannels(player);
        if (allowed.isEmpty()) {
            return entries;
        }
        // Killer/witch-only roles with an intentionally empty shop keep no shop (and no money display).
        // 仅杀手/魔女网络且商店被刻意清空的身份，保持无商店（也不显示金钱）。
        if (!allowed.contains(TabletChannel.POLICE) && entries.isEmpty()) {
            return entries;
        }
        for (ShopEntry entry : entries) {
            if (TabletShopRules.TABLET_ENTRY_ID.equals(entry.id()) || entry.stack().isOf(SparkStrengthItems.tablet())) {
                return entries;
            }
        }

        ShopEntry tablet = new ShopEntry.Builder(
                TabletShopRules.TABLET_ENTRY_ID,
                tabletDisplayStack(allowed),
                TabletChannelRules.price(allowed),
                ShopEntry.Type.TOOL
        ).actualStack(SparkStrengthItems.tablet().getDefaultStack())
                .stock(1)
                .onBuy(TabletShopService::buyTablet)
                .build();
        List<ShopEntry> result = new ArrayList<>(entries);
        result.add(SparkTraitsCompat.discountShopEntryForCharisma(player, tablet));
        return result;
    }

    /**
     * Server-side purchase handler (PlayerShopComponent.tryBuy). Returning false charges nothing. Round-start
     * stock only exists for roles held at initialization, so this ownership check also caps mid-round roles at one.
     * 服务端购买处理（PlayerShopComponent.tryBuy）；返回 false 不扣费。开局库存只覆盖初始化时的身份，
     * 因此该持有检查同时把局中获得的身份限制为一台。
     */
    private static boolean buyTablet(PlayerEntity player) {
        if (ownsTablet(player)) {
            // Normally already denied with a message by denyDuplicateTablet; this is the authoritative backstop.
            // 通常已由 denyDuplicateTablet 带提示拒绝；此处为权威兜底。
            return false;
        }
        // Wathe's default onBuy: first empty hotbar slot 0-8, otherwise fail.
        // 与 Wathe 默认 onBuy 一致：放入第一个空的快捷栏 0-8，没有则失败。
        if (!ShopEntry.insertStackInFreeSlot(player, SparkStrengthItems.tablet().getDefaultStack())) {
            return false;
        }
        if (player instanceof ServerPlayerEntity serverPlayer) {
            TabletStateService.syncTo(serverPlayer);
        }
        return true;
    }

    private static ShopPurchase.PurchaseResult denyDuplicateTablet(ServerPlayerEntity player, ShopEntry entry, int index) {
        return TabletShopRules.TABLET_ENTRY_ID.equals(entry.id()) && ownsTablet(player)
                ? ShopPurchase.PurchaseResult.deny(ALREADY_OWNED_KEY)
                : null;
    }

    private static boolean hasTabletAnywhere(ServerPlayerEntity player) {
        // RoleAssigned can fire more than once per round; scan every slot so the grant never duplicates.
        // RoleAssigned 每局可能触发多次；扫描所有槽位，避免重复发放。
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(SparkStrengthItems.tablet())) {
                return true;
            }
        }
        return false;
    }

    private static boolean ownsTablet(PlayerEntity player) {
        if (player.getInventory().contains(stack -> stack.isOf(SparkStrengthItems.tablet()))) {
            return true;
        }
        // The shop lives in the inventory screen, so a tablet may be held on the cursor while buying.
        // 商店位于物品栏界面，购买时平板可能正被鼠标拿起。
        return player.currentScreenHandler != null
                && player.currentScreenHandler.getCursorStack().isOf(SparkStrengthItems.tablet());
    }

    private static ItemStack tabletDisplayStack(Set<TabletChannel> allowed) {
        ItemStack stack = SparkStrengthItems.tablet().getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength.tablet"));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable(descriptionKey(allowed))
                        .styled(style -> style.withColor(0x808080).withItalic(false))
        )));
        return stack;
    }

    private static String descriptionKey(Set<TabletChannel> allowed) {
        String key = "shop.sparkstrength.tablet.description";
        if (allowed.contains(TabletChannel.POLICE)) {
            return allowed.contains(TabletChannel.KILLER) ? key + ".impostor" : key;
        }
        if (allowed.contains(TabletChannel.KILLER)) {
            return key + ".killer";
        }
        return allowed.contains(TabletChannel.WITCH) ? key + ".witch" : key;
    }
}
