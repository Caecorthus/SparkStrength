package annina.sparkstrength.role;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.component.role.RoleEnhancementPlayerComponent;
import annina.sparkstrength.component.role.RoleEnhancementWorldComponent;
import annina.sparkstrength.network.criminologist.OpenCriminologistScreenS2CPacket;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.api.event.BuildShopEntries;
import dev.doctor4t.wathe.api.event.CanSeeMoney;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.cca.PlayerShopComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.util.ShopEntry;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Server-side hooks that enhance selected NoellesRoles roles without changing their own classes.
 * 服务端挂钩：在不改 NoellesRoles 角色类的前提下追加机制。
 */
public final class NoellesRoleEnhancementService {
    private static boolean registered;

    private NoellesRoleEnhancementService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        BuildShopEntries.EVENT.register(NoellesRoleEnhancementService::buildShopEntries);
        CanSeeMoney.EVENT.register(NoellesRoleEnhancementService::canSeeMoney);
        UseEntityCallback.EVENT.register(NoellesRoleEnhancementService::useEntity);
    }

    public static void assignForRole(ServerPlayerEntity player, Role role) {
        RoleEnhancementPlayerComponent component = RoleEnhancementPlayerComponent.KEY.get(player);
        if (NoellesRoleEnhancementRules.shouldInitializeGoodMoney(role)) {
            PlayerShopComponent.KEY.get(player).setBalance(NoellesRoleEnhancementRules.INITIAL_GOOD_ROLE_MONEY);
        }
        // Attendants start with a flashlight, but the item itself stays role-independent.
        // 乘务员开局获得手电筒，但手电筒本身不绑定乘务员身份。
        if (shouldGiveAttendantFlashlight(role, hasFlashlight(player))) {
            player.giveItemStack(new ItemStack(SparkStrengthItems.flashlight()));
        }
        if (NoellesRoleIds.isDetective(role)) {
            component.initializeCriminologist();
        } else {
            component.clearCriminologist();
        }
    }

    public static void onTaskComplete(ServerPlayerEntity player) {
        Role role = GameWorldComponent.KEY.get(player.getServerWorld()).getRole(player);
        if (NoellesRoleEnhancementRules.earnsTaskMoney(role)) {
            PlayerShopComponent.KEY.get(player).addToBalance(NoellesRoleEnhancementRules.TASK_MONEY_REWARD);
        }
    }

    public static void afterKill(
            ServerPlayerEntity victim,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason
    ) {
        if (killer != null) {
            RoleEnhancementWorldComponent.KEY.get(victim.getServerWorld())
                    .recordCriminologistKill(victim.getUuid(), killer.getUuid());
        }
        for (ServerPlayerEntity player : victim.getServerWorld().getPlayers()) {
            RoleEnhancementPlayerComponent component = RoleEnhancementPlayerComponent.KEY.get(player);
            UUID targetUuid = component.getCriminologistTrackingTargetUuid();
            if (victim.getUuid().equals(targetUuid)) {
                component.startCriminologistCooldown();
            }
        }
    }

    public static void handleCriminologistSelection(
            ServerPlayerEntity player,
            UUID victimUuid,
            UUID suspectUuid
    ) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
        Role role = gameComponent.getRole(player);
        RoleEnhancementPlayerComponent component = RoleEnhancementPlayerComponent.KEY.get(player);
        if (!NoellesRoleIds.isDetective(role) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return;
        }
        if (component.getCriminologistCooldownTicks() > 0 || component.hasCriminologistTarget()) {
            return;
        }
        if (!victimUuid.equals(component.getCriminologistPendingVictimUuid())) {
            player.sendMessage(Text.translatable("message.sparkstrength.criminologist.no_pending"), true);
            return;
        }
        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        if (shop.getBalance() < NoellesRoleEnhancementRules.CRIMINOLOGIST_COST) {
            player.sendMessage(Text.translatable(
                    "message.sparkstrength.criminologist.not_enough_money",
                    NoellesRoleEnhancementRules.CRIMINOLOGIST_COST
            ), true);
            return;
        }
        shop.setBalance(shop.getBalance() - NoellesRoleEnhancementRules.CRIMINOLOGIST_COST);

        Optional<UUID> actualKiller = RoleEnhancementWorldComponent.KEY.get(player.getServerWorld())
                .getCriminologistKiller(victimUuid);
        if (actualKiller.isEmpty() || !actualKiller.get().equals(suspectUuid)) {
            component.startCriminologistCooldown();
            player.sendMessage(Text.translatable("message.sparkstrength.criminologist.wrong"), true);
            return;
        }

        ServerPlayerEntity killer = player.getServer().getPlayerManager().getPlayer(suspectUuid);
        if (killer == null || !GameFunctions.isPlayerPlayingAndAlive(killer) || gameComponent.isPlayerDead(suspectUuid)) {
            component.startCriminologistCooldown();
            player.sendMessage(Text.translatable("message.sparkstrength.criminologist.killer_dead"), true);
            return;
        }

        component.startCriminologistTracking(suspectUuid);
        player.sendMessage(Text.translatable("message.sparkstrength.criminologist.correct", killer.getName()), true);
    }

    private static void buildShopEntries(PlayerEntity player, BuildShopEntries.ShopContext context) {
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        if (!NoellesRoleEnhancementRules.canBuyCapsules(role)) {
            return;
        }
        context.addEntry(new ShopEntry.Builder(
                NoellesRoleEnhancementRules.CAPSULE_ENTRY_ID,
                capsuleDisplayStack(),
                NoellesRoleEnhancementRules.CAPSULE_PRICE,
                ShopEntry.Type.TOOL
        ).build());
    }

    private static CanSeeMoney.Result canSeeMoney(PlayerEntity player) {
        if (player == null || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return null;
        }
        Role role = GameWorldComponent.KEY.get(player.getWorld()).getRole(player);
        return moneyVisibilityResult(role);
    }

    static CanSeeMoney.Result moneyVisibilityResult(@Nullable Role role) {
        return NoellesRoleEnhancementRules.isGoodMoneyRole(role) ? CanSeeMoney.Result.ALLOW : null;
    }

    private static ActionResult useEntity(
            PlayerEntity player,
            World world,
            net.minecraft.util.Hand hand,
            net.minecraft.entity.Entity entity,
            @Nullable net.minecraft.util.hit.EntityHitResult hitResult
    ) {
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.PASS;
        }
        if (!(entity instanceof PlayerBodyEntity body)) {
            return ActionResult.PASS;
        }

        return tryOpenCriminologistScreen(serverPlayer, body.getPlayerUuid())
                ? ActionResult.SUCCESS
                : ActionResult.PASS;
    }

    private static boolean tryOpenCriminologistScreen(ServerPlayerEntity player, UUID victimUuid) {
        GameWorldComponent gameComponent = GameWorldComponent.KEY.get(player.getServerWorld());
        Role role = gameComponent.getRole(player);
        if (!NoellesRoleIds.isDetective(role) || !GameFunctions.isPlayerPlayingAndAlive(player)) {
            return false;
        }

        RoleEnhancementPlayerComponent component = RoleEnhancementPlayerComponent.KEY.get(player);
        if (component.getCriminologistCooldownTicks() > 0) {
            player.sendMessage(Text.translatable(
                    "message.sparkstrength.criminologist.cooldown",
                    seconds(component.getCriminologistCooldownTicks())
            ), true);
            return true;
        }
        if (component.hasCriminologistTarget()) {
            player.sendMessage(Text.translatable("message.sparkstrength.criminologist.already_tracking"), true);
            return true;
        }

        PlayerShopComponent shop = PlayerShopComponent.KEY.get(player);
        if (shop.getBalance() < NoellesRoleEnhancementRules.CRIMINOLOGIST_COST) {
            player.sendMessage(Text.translatable(
                    "message.sparkstrength.criminologist.not_enough_money",
                    NoellesRoleEnhancementRules.CRIMINOLOGIST_COST
            ), true);
            return true;
        }

        component.setCriminologistPendingVictim(victimUuid);
        ServerPlayNetworking.send(player, new OpenCriminologistScreenS2CPacket(victimUuid));
        return true;
    }

    private static ItemStack capsuleDisplayStack() {
        ItemStack stack = SparkStrengthItems.capsule().getDefaultStack();
        stack.set(DataComponentTypes.ITEM_NAME, Text.translatable("shop.sparkstrength.capsule"));
        stack.set(DataComponentTypes.LORE, new LoreComponent(List.of(
                Text.translatable("shop.sparkstrength.capsule.description")
                        .styled(style -> style.withColor(0x808080).withItalic(false))
        )));
        return stack;
    }

    private static int seconds(int ticks) {
        return (int) Math.ceil(ticks / 20.0);
    }

    static boolean shouldGiveAttendantFlashlight(Role role, boolean alreadyHasFlashlight) {
        return NoellesRoleEnhancementRules.startsWithFlashlight(role) && !alreadyHasFlashlight;
    }

    private static boolean hasFlashlight(ServerPlayerEntity player) {
        // Make assignment idempotent so repeated role hooks do not duplicate the starter item.
        // 保证重复身份分配不会刷出多个开局手电筒。
        for (int slot = 0; slot < player.getInventory().size(); slot++) {
            if (player.getInventory().getStack(slot).isOf(SparkStrengthItems.flashlight())) {
                return true;
            }
        }
        return false;
    }
}
