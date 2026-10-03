package annina.sparkstrength.role.toxicologist;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.compat.SparkTraitsBluePoisonCompat;
import annina.sparkstrength.item.CapsuleItem;
import dev.doctor4t.wathe.block.FoodPlatterBlock;
import dev.doctor4t.wathe.block.TrimmedBedBlock;
import dev.doctor4t.wathe.cca.MapEnhancementsWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import dev.doctor4t.wathe.index.WatheDataComponentTypes;
import dev.doctor4t.wathe.record.GameRecordManager;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.screen.slot.Slot;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.ClickType;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.UUID;

/**
 * Blue Vitriol: converts native Wathe poison on plates, beds, held food/drink and filled capsules into SparkTraits blue
 * poison owned by the Toxicologist. Every conversion is server-authoritative and silent to other players.
 * 蓝矾：把餐盘、床、食物/饮品与已装填胶囊上的 Wathe 原生毒转化为归属毒理学家的 SparkTraits 蓝毒。
 * 所有转化都由服务端权威结算，且不会被其他玩家听到。
 */
public final class ToxicologistBlueVitriolService {
    public static final String ACTION_PLATE = "plate";
    public static final String ACTION_BED = "bed";
    public static final String ACTION_FOOD = "food";
    public static final String ACTION_CAPSULE = "capsule";
    public static final String CONVERTED_KEY = "message.sparkstrength.blue_vitriol.converted";
    public static final String NOTHING_KEY = "message.sparkstrength.blue_vitriol.nothing";
    public static final String UNAVAILABLE_KEY = "message.sparkstrength.blue_poison.unavailable";
    private static boolean registered;

    private ToxicologistBlueVitriolService() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        UseBlockCallback.EVENT.register(ToxicologistBlueVitriolService::onUseBlock);
    }

    /**
     * UseBlockCallback runs before Block.onUse on both sides, so a handled use never reaches Wathe's plate (hands out
     * food) or bed (tries to sleep). The client only predicts SUCCESS (swing + sends the use packet); the server decides.
     * UseBlockCallback 在双端都先于 Block.onUse 触发，因此被接管的交互不会进入 Wathe 餐盘（发放食物）或床（尝试睡觉）。
     * 客户端只预测 SUCCESS（挥手并发送交互包），结果由服务端决定。
     */
    private static ActionResult onUseBlock(PlayerEntity player, World world, Hand hand, BlockHitResult hitResult) {
        ItemStack vitriol = player.getStackInHand(hand);
        if (!vitriol.isOf(SparkStrengthItems.blueVitriol())) {
            return ActionResult.PASS;
        }
        BlockPos pos = hitResult.getBlockPos();
        Block block = world.getBlockState(pos).getBlock();
        ToxicologistBlueItemRules.BlockTarget target = ToxicologistBlueItemRules.blockTarget(
                block instanceof FoodPlatterBlock,
                block instanceof TrimmedBedBlock
        );
        if (target == ToxicologistBlueItemRules.BlockTarget.NONE) {
            return ActionResult.PASS;
        }
        // Wathe's own UseBlockCallback answers FAIL for blacklisted blocks; listener order is not guaranteed, so defer.
        // Wathe 自己的 UseBlockCallback 会对黑名单方块返回 FAIL；监听器顺序无保证，因此这里主动让行。
        boolean blacklisted = MapEnhancementsWorldComponent.KEY.get(world)
                .getInteractionBlacklistConfig()
                .isBlacklisted(block);
        if (!ToxicologistBlueItemRules.shouldHandleBlockUse(
                true,
                target,
                blacklisted,
                ToxicologistBlueRules.isToxicologistLike(player),
                GameFunctions.isPlayerPlayingAndAlive(player),
                player.isSpectator()
        )) {
            return ActionResult.PASS;
        }
        if (world.isClient() || !(player instanceof ServerPlayerEntity serverPlayer)) {
            return ActionResult.SUCCESS;
        }
        if (!SparkTraitsBluePoisonCompat.isBluePoisonApiAvailable()) {
            serverPlayer.sendMessage(Text.translatable(UNAVAILABLE_KEY), true);
            return ActionResult.FAIL;
        }

        UUID poisoner = serverPlayer.getUuid();
        boolean converted = target == ToxicologistBlueItemRules.BlockTarget.PLATE
                ? SparkTraitsBluePoisonCompat.convertPlatePoisonToBlue(world, pos, poisoner)
                : SparkTraitsBluePoisonCompat.convertBedPoisonToBlue(world, pos, poisoner);
        if (!converted) {
            serverPlayer.sendMessage(Text.translatable(NOTHING_KEY), true);
            return ActionResult.SUCCESS;
        }
        NbtCompound extra = new NbtCompound();
        GameRecordManager.putBlockPos(extra, "pos", pos);
        finishConversion(
                serverPlayer,
                vitriol,
                target == ToxicologistBlueItemRules.BlockTarget.PLATE ? ACTION_PLATE : ACTION_BED,
                extra
        );
        return ActionResult.SUCCESS;
    }

    /**
     * Vitriol on the cursor right-clicked onto a slot. Both sides return the same handled decision from synced stack
     * data, so the client never runs vanilla's swap; only the server mutates, and its slot/cursor sync corrects the client.
     * 光标上的蓝矾右键某个格子。双端用同步的物品数据得出相同的“是否接管”结论，客户端不会执行原版交换；
     * 只有服务端修改物品，再由其格子/光标同步纠正客户端。
     */
    public static boolean onStackClicked(ItemStack vitriol, Slot slot, ClickType clickType, PlayerEntity player) {
        if (clickType != ClickType.RIGHT) {
            return false;
        }
        ItemStack target = slot.getStack();
        ToxicologistBlueItemRules.StackTarget kind = stackTarget(target);
        if (kind == ToxicologistBlueItemRules.StackTarget.NONE || !canUseVitriol(player)) {
            return false;
        }
        if (!player.getWorld().isClient() && player instanceof ServerPlayerEntity serverPlayer) {
            convertStack(serverPlayer, vitriol, slot, target, kind);
        }
        return true;
    }

    private static void convertStack(
            ServerPlayerEntity player,
            ItemStack vitriol,
            Slot slot,
            ItemStack target,
            ToxicologistBlueItemRules.StackTarget kind
    ) {
        if (!SparkTraitsBluePoisonCompat.isBluePoisonApiAvailable()) {
            player.sendMessage(Text.translatable(UNAVAILABLE_KEY), true);
            return;
        }
        UUID poisoner = player.getUuid();
        NbtCompound extra = new NbtCompound();
        boolean converted;
        if (kind == ToxicologistBlueItemRules.StackTarget.FOOD) {
            // Wathe's replay item formatter prefers a serialized item_name over the recorded item id (the vitriol).
            // Wathe 回放的物品名格式化优先使用序列化的 item_name，而不是记录的物品 ID（即蓝矾本身）。
            extra.putString("item_name", Text.Serialization.toJsonString(target.getName(), player.getRegistryManager()));
            converted = SparkTraitsBluePoisonCompat.convertStackPoisonToBlue(target, poisoner);
        } else {
            converted = CapsuleItem.convertContentsPoisonToBlue(target, poisoner, player.getRegistryManager());
        }
        if (!converted) {
            player.sendMessage(Text.translatable(NOTHING_KEY), true);
            return;
        }
        slot.markDirty();
        finishConversion(
                player,
                vitriol,
                kind == ToxicologistBlueItemRules.StackTarget.FOOD ? ACTION_FOOD : ACTION_CAPSULE,
                extra
        );
    }

    private static ToxicologistBlueItemRules.StackTarget stackTarget(ItemStack target) {
        boolean filledCapsule = target.getItem() instanceof CapsuleItem && CapsuleItem.hasContents(target);
        return ToxicologistBlueItemRules.stackTarget(
                CapsuleItem.isFoodOrDrink(target),
                target.contains(WatheDataComponentTypes.POISONER),
                filledCapsule,
                filledCapsule && CapsuleItem.hasNativePoisonedContents(target)
        );
    }

    private static boolean canUseVitriol(PlayerEntity player) {
        return ToxicologistBlueRules.isToxicologistLike(player)
                && GameFunctions.isPlayerPlayingAndAlive(player)
                && !player.isSpectator();
    }

    private static void finishConversion(ServerPlayerEntity player, ItemStack vitriol, String action, NbtCompound extra) {
        vitriol.decrementUnlessCreative(1, player);
        // Private feedback only: the conversion must not be audible to anyone else.
        // 只给本人反馈：转化过程不能被其他玩家听到。
        player.playSoundToPlayer(SoundEvents.BLOCK_BREWING_STAND_BREW, SoundCategory.PLAYERS, 0.5F, 1.4F);
        player.sendMessage(Text.translatable(CONVERTED_KEY), true);
        extra.putString("action", action);
        GameRecordManager.recordItemUse(player, SparkStrengthItems.BLUE_VITRIOL_ID, null, extra);
    }
}
