package annina.sparkstrength.mixin.bodyguard;

import annina.sparkstrength.role.bodyguard.BodyguardProtectionService;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Democracy Shield and vest at HEAD of the one killPlayer overload every Wathe kill funnels into. Here the force
 * flag is known, and the gear is spent before any KillPlayer.BEFORE listener (NoellesRoles' Iron Man / whiskey) or
 * Wathe's psycho armour. SparkWitch's AXMC may pierce the vest; the kill then goes on past HEAD instead of being
 * cancelled (BodyguardProtectionService).
 *
 * <p>Priority 1100: HEAD injectors run in apply order (priority, then config order), so this one runs after every
 * default-priority immunity guard at the same HEAD: the Jester's fake-death immunity, the stage-2 Undercover's
 * assassination immunity, SparkWitch's Saint protection and SparkFactionAPI's affect veto. Gear is only spent on a
 * kill that no immunity stops (owner 2026-10-22).</p>
 * 民主盾牌与防弹衣，挂在所有 Wathe 击杀汇总的 killPlayer 重载的 HEAD。此处能看到 force 标记，并且装备先于任何
 * KillPlayer.BEFORE 监听（NoellesRoles 铁人/威士忌）与 Wathe 疯魔护甲消耗。SparkWitch 的 AXMC 可以击穿防弹衣，此时击杀
 * 不在 HEAD 取消而是继续（BodyguardProtectionService）。
 *
 * <p>优先级 1100：同一 HEAD 的注入按应用顺序执行（先优先级，再配置顺序），因此本钩子排在所有默认优先级的免死判断之后：
 * 小丑假死免死、二阶段卧底免刺杀、SparkWitch 的圣徒保护与 SparkFactionAPI 的影响否决。只有没有任何免死挡下的击杀才会
 * 消耗装备（所有者 2026-10-22）。</p>
 */
@Mixin(value = GameFunctions.class, priority = 1100)
public abstract class BodyguardGearKillGuardMixin {
    @Inject(
            method = "killPlayer(Lnet/minecraft/server/network/ServerPlayerEntity;ZLnet/minecraft/server/network/ServerPlayerEntity;Lnet/minecraft/util/Identifier;Z)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private static void sparkstrength$bodyguardGearBlocksKill(
            ServerPlayerEntity victim,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            boolean force,
            CallbackInfo ci
    ) {
        if (BodyguardProtectionService.tryBlockKill(victim, killer, deathReason, force)) {
            ci.cancel();
        }
    }
}
