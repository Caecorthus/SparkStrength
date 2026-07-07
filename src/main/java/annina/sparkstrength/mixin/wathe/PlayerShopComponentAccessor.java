package annina.sparkstrength.mixin.wathe;

import dev.doctor4t.wathe.cca.PlayerShopComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

@Mixin(value = PlayerShopComponent.class, remap = false)
public interface PlayerShopComponentAccessor {
    @Accessor(value = "cooldowns", remap = false)
    Map<String, Integer> sparkstrength$getCooldowns();
}
