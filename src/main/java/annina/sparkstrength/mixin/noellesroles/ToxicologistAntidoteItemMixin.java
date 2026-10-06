package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.component.pathogen.VirusCarrierComponent;
import annina.sparkstrength.mixin.wathe.PlayerPoisonComponentAccessor;
import annina.sparkstrength.role.pathogen.PathogenRules;
import annina.sparkstrength.role.pathogen.VirusService;
import annina.sparkstrength.role.toxicologist.ToxicologistAntidoteService;
import annina.sparkstrength.role.coroner.CoronerService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.doctor4t.wathe.cca.PlayerPoisonComponent;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.NbtComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.agmas.noellesroles.item.AntidoteItem;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

/**
 * 接入 NoellesRoles 解毒剂的成功治疗结果。
 *
 * <p>NoellesRoles 的解毒剂没有事件钩子，成功路径是在 finishUsing 里直接 reset 毒组件并设置冷却。
 * 这里在方法开头记录“目标治疗前确实中毒”，再在方法尾部确认毒已被清掉，避免治疗失败、
 * 距离过远、目标不存在或非中毒目标也触发毒理学家奖励和冷却抵扣。</p>
 *
 * <p>Pathogen buff (Q3): a virus carrier counts as treatable wherever NoellesRoles reads {@code poisonTicks} (start, hold,
 * finish, on both sides; the carrier flag is synced to Toxicologists), and the cure clears the virus and its infection
 * together with the poison. Curing a carrier pays like curing poison, once per use.
 * 病原体增强（Q3）：在 NoellesRoles 读取 {@code poisonTicks} 的每一处（开始、持续、完成，双端；带毒标记已同步给毒理学家），
 * 带毒者都视为可治疗；治疗会连同毒一起清除病毒及其感染。治疗带毒者与解毒一样获得奖励，每次使用只结算一次。</p>
 */
@Mixin(AntidoteItem.class)
public abstract class ToxicologistAntidoteItemMixin {
    @Unique
    private static final ThreadLocal<Boolean> SPARKSTRENGTH_CURED_TREATABLE_TARGET =
            ThreadLocal.withInitial(() -> false);
    @Unique
    private static final ThreadLocal<UUID> SPARKSTRENGTH_CURE_TARGET =
            new ThreadLocal<>();

    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$coronerOnlyUsesAntidoteWhileToxicologist(
            World world,
            net.minecraft.entity.player.PlayerEntity user,
            Hand hand,
            CallbackInfoReturnable<TypedActionResult<ItemStack>> cir
    ) {
        if (!CoronerService.canUseToxicologistDisguiseItem(user)) {
            cir.setReturnValue(TypedActionResult.pass(user.getStackInHand(hand)));
        }
    }

    @Inject(method = "useOnEntity", at = @At("HEAD"), cancellable = true)
    private void sparkstrength$coronerOnlyUsesAntidoteOnEntityWhileToxicologist(
            ItemStack stack,
            net.minecraft.entity.player.PlayerEntity user,
            LivingEntity entity,
            Hand hand,
            CallbackInfoReturnable<ActionResult> cir
    ) {
        if (!CoronerService.canUseToxicologistDisguiseItem(user)) {
            cir.setReturnValue(ActionResult.PASS);
        }
    }

    @Inject(method = "finishUsing", at = @At("HEAD"))
    private void sparkstrength$capturePoisonedTargetBeforeCure(
            ItemStack stack,
            World world,
            LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        SPARKSTRENGTH_CURED_TREATABLE_TARGET.set(false);
        SPARKSTRENGTH_CURE_TARGET.remove();
        if (world.isClient || !(user instanceof ServerPlayerEntity)) {
            return;
        }

        UUID targetUuid = sparkstrength$getTargetUuid(stack);
        if (targetUuid == null || !(world.getPlayerByUuid(targetUuid) instanceof ServerPlayerEntity target)) {
            return;
        }

        PlayerPoisonComponent poisonComponent = PlayerPoisonComponent.KEY.get(target);
        boolean treatable = PathogenRules.antidoteCanTreat(
                poisonComponent.poisonTicks > 0, VirusCarrierComponent.isCarrier(target));
        SPARKSTRENGTH_CURED_TREATABLE_TARGET.set(treatable);
        if (treatable) {
            SPARKSTRENGTH_CURE_TARGET.set(targetUuid);
        }
    }

    @Inject(method = "finishUsing", at = @At("RETURN"))
    private void sparkstrength$rewardToxicologistAfterCure(
            ItemStack stack,
            World world,
            LivingEntity user,
            CallbackInfoReturnable<ItemStack> cir
    ) {
        try {
            if (world.isClient || !(user instanceof ServerPlayerEntity serverUser)) {
                return;
            }
            if (!SPARKSTRENGTH_CURED_TREATABLE_TARGET.get()) {
                return;
            }
            UUID targetUuid = SPARKSTRENGTH_CURE_TARGET.get();
            if (targetUuid == null || !(world.getPlayerByUuid(targetUuid) instanceof ServerPlayerEntity target)) {
                return;
            }

            PlayerPoisonComponent poisonComponent = PlayerPoisonComponent.KEY.get(target);
            if (poisonComponent.poisonTicks > 0 || VirusCarrierComponent.isCarrier(target)) {
                return;
            }

            ToxicologistAntidoteService.afterAntidoteCure(serverUser);
        } finally {
            SPARKSTRENGTH_CURED_TREATABLE_TARGET.remove();
            SPARKSTRENGTH_CURE_TARGET.remove();
        }
    }

    /**
     * Every {@code poisonTicks} read in {@code useOnEntity}, {@code use}, {@code usageTick} and {@code finishUsing}
     * (four reads, one each): a virus carrier reads as treatable.
     * {@code useOnEntity}、{@code use}、{@code usageTick} 与 {@code finishUsing} 中每一次 {@code poisonTicks} 读取
     * （共四次，每个方法一次）：带毒者视为可治疗。
     */
    @WrapOperation(
            method = {"useOnEntity", "use", "usageTick", "finishUsing"},
            at = @At(
                    value = "FIELD",
                    target = "Ldev/doctor4t/wathe/cca/PlayerPoisonComponent;poisonTicks:I",
                    opcode = Opcodes.GETFIELD,
                    remap = false
            ),
            require = 4,
            allow = 4
    )
    private int sparkstrength$virusCarrierIsTreatable(PlayerPoisonComponent component, Operation<Integer> original) {
        int poisonTicks = original.call(component);
        if (poisonTicks > 0) {
            return poisonTicks;
        }
        PlayerEntity owner = ((PlayerPoisonComponentAccessor) component).sparkstrength$getPlayer();
        return VirusCarrierComponent.isCarrier(owner) ? 1 : poisonTicks;
    }

    /** The cure itself: the poison reset also clears the virus and its infection. / 治疗本身：清毒时一并清除病毒与感染。 */
    @WrapOperation(
            method = "finishUsing",
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/cca/PlayerPoisonComponent;reset()V",
                    remap = false
            ),
            require = 1,
            allow = 1
    )
    private void sparkstrength$cureVirusWithPoison(
            PlayerPoisonComponent component,
            Operation<Void> original,
            @Local(argsOnly = true) LivingEntity user
    ) {
        original.call(component);
        PlayerEntity target = ((PlayerPoisonComponentAccessor) component).sparkstrength$getPlayer();
        if (target != null && !target.getWorld().isClient()) {
            VirusService.cureCarrier(user instanceof PlayerEntity curer ? curer : null, target);
        }
    }

    @Unique
    private static UUID sparkstrength$getTargetUuid(ItemStack stack) {
        NbtComponent customData = stack.get(DataComponentTypes.CUSTOM_DATA);
        if (customData == null) {
            return null;
        }

        NbtCompound nbt = customData.copyNbt();
        return nbt.containsUuid("target") ? nbt.getUuid("target") : null;
    }
}
