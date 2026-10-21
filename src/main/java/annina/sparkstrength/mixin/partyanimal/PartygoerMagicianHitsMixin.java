package annina.sparkstrength.mixin.partyanimal;

import annina.sparkstrength.role.partyanimal.PartygoerWeaponHits;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Optional SparkWitch seams in {@code MagicianPuppetHits}, where SparkWitch already hands its lethal weapons' NON-player
 * targets to one static entry each (for Magician puppets): the crimson wither skull's entity hit, the ceremonial sword's
 * charged strike and dash step, and the Potion Gunner shell blast. A Partygoer there dies like a player would
 * ({@code PartygoerWeaponHits}); everything else reaches SparkWitch untouched. Not a public API: {@code @Pseudo},
 * name-only selectors, {@code @Local} arguments (vanilla types only; the shell type is read from the shell item) and
 * {@code require = 0} make every hook a silent no-op when SparkWitch is absent or the method changes.
 * MagicianPuppetHits 中的可选 SparkWitch 接缝：SparkWitch 已把其致命武器的“非玩家”目标各交给一个静态入口（供魔术师皮套使用）：猩红凋零
 * 头颅的实体命中、仪礼剑的蓄力斩击与冲刺每一步、药炮手炮弹爆炸。派对客在这些入口处与玩家一样死亡（PartygoerWeaponHits）；其余情况
 * 原样交给 SparkWitch。这不是公开 API：@Pseudo、仅方法名选择器、@Local 参数（只用原版类型；炮弹类型从炮弹物品读取）与 require = 0
 * 使 SparkWitch 缺失或方法变更时静默失效。
 */
@Pseudo
@Mixin(targets = "dev.caecorthus.sparkwitch.roles.killer.magician.MagicianPuppetHits", remap = false)
public abstract class PartygoerMagicianHitsMixin {
    /**
     * Crimson wither skull hit a non-player: {@code onCrimsonSkullHit(Entity, Entity, EntityHitResult)}; true = the skull
     * is spent there. / 猩红凋零头颅命中非玩家；返回 true 表示头颅就此消耗。
     */
    @Inject(method = "onCrimsonSkullHit", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$crimsonSkullKillsPartygoer(CallbackInfoReturnable<Boolean> cir,
                                                                 @Local(argsOnly = true, index = 0) Entity skull,
                                                                 @Local(argsOnly = true, index = 1) Entity owner,
                                                                 @Local(argsOnly = true) EntityHitResult hit) {
        if (PartygoerWeaponHits.onCrimsonSkullHit(skull, owner, hit)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Ceremonial sword, fully charged left click on a non-player: {@code onCeremonialSwordStrike(ServerPlayerEntity,
     * Entity)}. / 仪礼剑对非玩家的蓄满左键。
     */
    @Inject(method = "onCeremonialSwordStrike", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$swordStrikeKillsPartygoer(CallbackInfoReturnable<Boolean> cir,
                                                                @Local(argsOnly = true, index = 0)
                                                                ServerPlayerEntity attacker,
                                                                @Local(argsOnly = true, index = 1) Entity target) {
        if (PartygoerWeaponHits.onCeremonialSwordStrike(attacker, target)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Ceremonial sword dash step after its player contact check: {@code onCeremonialSwordDash(ServerPlayerEntity, Box)};
     * true = the dash stops (a Partygoer contact). / 仪礼剑冲刺每一步（玩家接触检查之后）；返回 true 表示冲刺停下。
     */
    @Inject(method = "onCeremonialSwordDash", at = @At("HEAD"), cancellable = true, require = 0)
    private static void sparkstrength$swordDashKillsPartygoer(CallbackInfoReturnable<Boolean> cir,
                                                              @Local(argsOnly = true) ServerPlayerEntity attacker,
                                                              @Local(argsOnly = true) Box sweptBox) {
        if (PartygoerWeaponHits.onCeremonialSwordDash(attacker, sweptBox)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * Potion Gunner shell blast, before the shell's own targets: {@code onPotionShellBlast(ServerWorld, Vec3d,
     * PotionShellType, ServerPlayerEntity, ItemStack)}; only a TR shell kills. / 药炮手炮弹爆炸，只有 TR 炮弹致死。
     */
    @Inject(method = "onPotionShellBlast", at = @At("HEAD"), require = 0)
    private static void sparkstrength$trShellKillsPartygoers(CallbackInfo ci,
                                                             @Local(argsOnly = true) ServerWorld world,
                                                             @Local(argsOnly = true) Vec3d center,
                                                             @Local(argsOnly = true) ServerPlayerEntity gunner,
                                                             @Local(argsOnly = true) ItemStack shell) {
        PartygoerWeaponHits.onPotionShellBlast(world, center, gunner, shell);
    }
}
