package annina.sparkstrength.mixin.wathe;

import dev.doctor4t.wathe.cca.GameWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.HashSet;
import java.util.UUID;

/**
 * Exact Adapter for Wathe's private {@code GameWorldComponent.deadPlayers}: Wathe has no "un-dead" method, and the
 * Pathogen's T-Virus revive must take a player back out of the dead set (same approach as SparkTraits' Last Stand).
 * Wathe 私有 {@code GameWorldComponent.deadPlayers} 的精确 Adapter：Wathe 没有“复活”方法，病原体的 T病毒复活需要把玩家
 * 移出死亡名单（与 SparkTraits 背水一战的做法相同）。
 *
 * <p>Writers: the T-Virus revive and the admin {@code setPlayerAlive}/{@code setPlayerDead} debug commands.
 * 写入方：T病毒复活，以及管理员调试命令 setPlayerAlive / setPlayerDead。</p>
 */
@Mixin(value = GameWorldComponent.class, remap = false)
public interface GameWorldComponentDeadPlayersAccessor {
    @Accessor(value = "deadPlayers", remap = false)
    HashSet<UUID> sparkstrength$getDeadPlayers();
}
