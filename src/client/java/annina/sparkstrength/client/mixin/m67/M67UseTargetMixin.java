package annina.sparkstrength.client.mixin.m67;

import annina.sparkstrength.client.item.M67Client;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Vanilla offers right-click to the crosshair target before the item, and any accepted interaction ends the click: an
 * unlocked door toggles, a seat seats you, and the grenade doesn't pull (a held key keeps toggling the door). A claimed
 * M67 skips those calls, so no block/entity packet is sent and doItemUse falls through to interactItem.
 * 原版右键先交给准星目标再交给物品，任何被接受的交互都会结束这次右键：未上锁的门会开关，座椅会让人坐下，手雷拉不了环（按住右键门会反复开关）。
 * M67 认领右键时跳过这些调用，不发送方块/实体交互包，doItemUse 继续走到 interactItem。
 */
@Mixin(MinecraftClient.class)
public abstract class M67UseTargetMixin {
    @WrapOperation(method = "doItemUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactBlock(Lnet/minecraft/client/network/ClientPlayerEntity;Lnet/minecraft/util/Hand;Lnet/minecraft/util/hit/BlockHitResult;)Lnet/minecraft/util/ActionResult;"))
    private ActionResult sparkstrength$m67SkipBlock(ClientPlayerInteractionManager manager, ClientPlayerEntity player,
                                                    Hand hand, BlockHitResult hit, Operation<ActionResult> original) {
        return M67Client.claimsUse(hand) ? ActionResult.PASS : original.call(manager, player, hand, hit);
    }

    @WrapOperation(method = "doItemUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactEntityAtLocation(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/hit/EntityHitResult;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"))
    private ActionResult sparkstrength$m67SkipEntityAt(ClientPlayerInteractionManager manager, PlayerEntity player,
                                                       Entity entity, EntityHitResult hit, Hand hand,
                                                       Operation<ActionResult> original) {
        return M67Client.claimsUse(hand) ? ActionResult.PASS : original.call(manager, player, entity, hit, hand);
    }

    @WrapOperation(method = "doItemUse", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;interactEntity(Lnet/minecraft/entity/player/PlayerEntity;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/Hand;)Lnet/minecraft/util/ActionResult;"))
    private ActionResult sparkstrength$m67SkipEntity(ClientPlayerInteractionManager manager, PlayerEntity player,
                                                     Entity entity, Hand hand, Operation<ActionResult> original) {
        return M67Client.claimsUse(hand) ? ActionResult.PASS : original.call(manager, player, entity, hand);
    }
}
