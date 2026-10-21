package annina.sparkstrength.mixin.partyanimal;

import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Reads NoellesRoles' dynamic Taotie swallow cooldown ({@code calculatedSwallowCooldown}, set per round from the player
 * count), so swallowing a Partygoer costs exactly what a player swallow costs.
 * 读取 NoellesRoles 饕餮的动态吞噬冷却（calculatedSwallowCooldown，每局按人数设定），使吞派对客的代价与吞人完全相同。
 */
@Mixin(value = TaotiePlayerComponent.class, remap = false)
public interface PartygoerTaotieCooldownAccessor {
    @Accessor(value = "calculatedSwallowCooldown", remap = false)
    int sparkstrength$getCalculatedSwallowCooldown();
}
