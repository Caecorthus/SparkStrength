package annina.sparkstrength.client.mixin.m67;

import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(KeyBinding.class)
public interface M67KeyBindingAccessor {
    @Accessor("boundKey")
    InputUtil.Key sparkstrength$m67BoundKey();
}
