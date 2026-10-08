package annina.sparkstrength.mixin.minecraft;

import net.minecraft.server.network.ServerItemCooldownManager;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Owner of a server cooldown manager, so a hook on a bare {@code remove(Item)} call knows whose cooldown changed.
 * 服务端冷却管理器所属的玩家，使挂在单纯 {@code remove(Item)} 调用上的钩子知道是谁的冷却发生了变化。
 */
@Mixin(ServerItemCooldownManager.class)
public interface ServerItemCooldownManagerAccessor {
    @Accessor("player")
    ServerPlayerEntity sparkstrength$getPlayer();
}
