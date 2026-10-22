package annina.sparkstrength.client.mixin.serialkiller;

import annina.sparkstrength.SparkStrengthItems;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.doctor4t.wathe.cca.PlayerPsychoComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Client hotbar lock, number keys: the {@code selectedSlot} write in {@code handleInputEvents} goes back to the serial
 * pistol at once, without waiting for the next tick's restore in {@link SerialKillerClientSlotLockMixin}. Wrapped, not
 * redirected, so it composes with the other wrappers of that write (SparkStrength M67's cancel, Wathe's psycho bat
 * lock). MixinExtras nests wrappers in apply order, the first one innermost; priority 900 applies this one before
 * theirs (default 1000), so it stays innermost, right on the field write, where the old {@code @Redirect} sat, and they
 * still see the slot the player asked for.
 * 客户端快捷栏锁定（数字键）：{@code handleInputEvents} 中写入 {@code selectedSlot} 的位置立即回到连环手枪，不必等下一 tick
 * 的恢复。用包装而不是重定向，因此能与同一写入的其他包装（SparkStrength M67 取消、Wathe 疯魔球棒锁）组合。MixinExtras
 * 按应用顺序嵌套包装，先应用的在最内层；优先级 900 使它先于它们（默认 1000）应用，因此仍在最内层、紧贴字段写入，也就是
 * 原先 {@code @Redirect} 的位置，它们看到的仍是玩家请求的槽位。
 */
@Mixin(value = MinecraftClient.class, priority = 900)
public abstract class SerialKillerClientSlotWriteMixin {
    @WrapOperation(method = "handleInputEvents", at = @At(value = "FIELD",
            target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", opcode = Opcodes.PUTFIELD))
    private void sparkstrength$lockSlot(PlayerInventory inventory, int value, Operation<Void> original) {
        if (inventory.player != null
                && PlayerPsychoComponent.KEY.get(inventory.player).getPsychoTicks() > 0) {
            int pistolSlot = findSerialPistolSlot(inventory);
            if (pistolSlot >= 0 && value != pistolSlot) {
                // 数字键写槽也必须立即回到主手连环手枪，不依赖下一 tick 恢复。
                original.call(inventory, pistolSlot);
                return;
            }
        }
        original.call(inventory, value);
    }

    private static int findSerialPistolSlot(PlayerInventory inventory) {
        for (int slot = 0; slot < 9; slot++) {
            if (inventory.getStack(slot).isOf(SparkStrengthItems.serialPistol())) return slot;
        }
        return -1;
    }
}
