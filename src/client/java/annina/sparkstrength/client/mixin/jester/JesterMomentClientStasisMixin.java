package annina.sparkstrength.client.mixin.jester;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.client.jester.JesterMomentClient;
import org.agmas.noellesroles.jester.JesterPlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/**
 * Starts NoellesRoles' Jester Moment view when the Jester's transformation (stasis) starts, not 3 s later when the
 * moment itself does. Pinned NoellesRoles 1.7.6: {@code clientTick} caches the first Jester in psycho mode, and every
 * moment overlay (everyone wears the Jester's psycho skin and bat, names scrambled, voices buzzed, the Jester hidden
 * from instinct, guns highlighted for innocents) reads that cache. When it found none, a Jester in stasis counts too;
 * the server syncs the stasis flag to everyone ({@code JesterMomentTransitionMixin}).
 * 让 NoellesRoles 的小丑时刻视角在小丑转变（禁锢）开始时就生效，而不是 3 秒后时刻正式开始时。锁定 NoellesRoles 1.7.6：
 * {@code clientTick} 缓存第一个处于疯魔的小丑，所有时刻覆盖效果（人人穿小丑疯魔皮肤拿球棒、名字乱码、全员变声、
 * 本能看不到小丑、好人高亮枪械）都读这个缓存。它没找到时，处于禁锢的小丑也算；服务端会把禁锢状态同步给所有人。
 */
@Mixin(value = JesterMomentClient.class, remap = false)
public abstract class JesterMomentClientStasisMixin {
    @Shadow
    private static volatile UUID activeJesterUuid;

    @Inject(method = "clientTick", at = @At("RETURN"))
    private static void sparkstrength$activateDuringStasis(MinecraftClient client, CallbackInfo ci) {
        if (activeJesterUuid != null || client.world == null) {
            return;
        }
        GameWorldComponent game = GameWorldComponent.KEY.get(client.world);
        for (AbstractClientPlayerEntity player : client.world.getPlayers()) {
            if (game.isRole(player, Noellesroles.JESTER) && JesterPlayerComponent.KEY.get(player).inStasis) {
                activeJesterUuid = player.getUuid();
                return;
            }
        }
    }
}
