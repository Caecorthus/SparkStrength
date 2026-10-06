package annina.sparkstrength.mixin.noellesroles;

import org.agmas.noellesroles.scavenger.HiddenBodiesWorldComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

/**
 * Exact Adapter for NoellesRoles' private Scavenger set. The set is keyed by body owner and only reset at round end, so
 * a player revived by the T-Virus would otherwise leave every later corpse hidden too.
 * NoellesRoles 清道夫私有集合的精确 Adapter。该集合按尸体主人记录且只在局末重置，被 T病毒复活的玩家否则之后的尸体也会
 * 被隐藏。
 */
@Mixin(value = HiddenBodiesWorldComponent.class, remap = false)
public interface HiddenBodiesWorldComponentAccessor {
    @Accessor(value = "hiddenBodies", remap = false)
    Set<UUID> sparkstrength$getHiddenBodies();
}
