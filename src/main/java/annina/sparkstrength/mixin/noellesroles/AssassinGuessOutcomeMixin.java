package annina.sparkstrength.mixin.noellesroles;

import annina.sparkstrength.role.assassin.AssassinGuessService;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.packet.AssassinGuessRoleC2SPacket;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The Assassin buff inside NoellesRoles' guess handler: a forgiven miss skips the misfire gunshot at the Assassin (second
 * {@code playSound}) and the misfire kill (second {@code killPlayer}), and the cooldown {@code useGuess()} just set is
 * shortened after a correct guess. All three bind the exact lambda the Undercover immunity uses, with
 * {@code require = 0}; build.gradle pins the selectors against the libs jar so a renumbered NoellesRoles build fails
 * the build instead of silently dropping the buff. The lambda name cannot be remapped, hence {@code remap = false};
 * only the {@code playSound} target names Minecraft types and turns remapping back on for itself.
 * NoellesRoles 猜测处理函数里的刺客加强：被宽恕的猜错跳过刺客脚下的反噬枪声（第二个 playSound）与反噬击杀（第二个 killPlayer）；
 * 猜对后把 useGuess() 刚设好的冷却缩短。三处都以 require = 0 绑定卧底免疫所用的同一个 lambda；build.gradle 会对照 libs jar
 * 锁定选择器，NoellesRoles 编号变化时构建失败，而不是悄悄失去加强。lambda 名无法重映射，故 remap = false；只有 playSound
 * 的目标含 Minecraft 类型，单独为它重新开启重映射。
 */
@Mixin(value = Noellesroles.class, remap = false)
public abstract class AssassinGuessOutcomeMixin {
    private static final String GUESS_HANDLER = "lambda$registerPackets$6("
            + "Lorg/agmas/noellesroles/packet/AssassinGuessRoleC2SPacket;"
            + "Lnet/fabricmc/fabric/api/networking/v1/ServerPlayNetworking$Context;)V";

    @WrapOperation(
            method = GUESS_HANDLER,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;playSound(Lnet/minecraft/entity/player/PlayerEntity;DDD"
                            + "Lnet/minecraft/sound/SoundEvent;Lnet/minecraft/sound/SoundCategory;FF)V",
                    ordinal = 1,
                    remap = true
            ),
            require = 0,
            remap = false
    )
    private static void sparkstrength$forgivenMissIsSilent(
            World world,
            @Nullable PlayerEntity except,
            double x,
            double y,
            double z,
            SoundEvent sound,
            SoundCategory category,
            float volume,
            float pitch,
            Operation<Void> original,
            @Local(ordinal = 0) ServerPlayerEntity assassin
    ) {
        // Runs just before the misfire kill below, which is what spends the free miss. / 紧接在下面的反噬击杀之前；免死机会由后者消耗。
        if (!AssassinGuessService.missWillBeForgiven(assassin)) {
            original.call(world, except, x, y, z, sound, category, volume, pitch);
        }
    }

    @WrapOperation(
            method = GUESS_HANDLER,
            at = @At(
                    value = "INVOKE",
                    target = "Ldev/doctor4t/wathe/game/GameFunctions;killPlayer",
                    ordinal = 1
            ),
            require = 0,
            remap = false
    )
    private static void sparkstrength$firstMissIsForgiven(
            ServerPlayerEntity assassin,
            boolean spawnBody,
            @Nullable ServerPlayerEntity killer,
            Identifier deathReason,
            Operation<Void> original,
            @Local(ordinal = 1) ServerPlayerEntity target
    ) {
        if (!AssassinGuessService.forgiveMiss(assassin, target)) {
            original.call(assassin, spawnBody, killer, deathReason);
        }
    }

    @Inject(
            method = GUESS_HANDLER,
            at = @At(
                    value = "INVOKE",
                    target = "Lorg/agmas/noellesroles/assassin/AssassinPlayerComponent;useGuess()V",
                    shift = At.Shift.AFTER
            ),
            require = 0,
            remap = false
    )
    private static void sparkstrength$correctGuessCoolsDownFast(
            AssassinGuessRoleC2SPacket payload,
            ServerPlayNetworking.Context context,
            CallbackInfo ci,
            @Local boolean guessedCorrectly
    ) {
        AssassinGuessService.afterGuess(context.player(), guessedCorrectly);
    }
}
