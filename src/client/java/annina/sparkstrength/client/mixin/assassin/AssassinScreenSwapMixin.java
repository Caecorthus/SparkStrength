package annina.sparkstrength.client.mixin.assassin;

import annina.sparkstrength.client.screen.assassin.AssassinGuessSources;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * NoellesRoles opens its own {@code AssassinScreen} from the ability key; SparkStrength's grouped guess screen takes its
 * place the moment it is about to be shown, so the key handler and its checks stay NoellesRoles' own.
 * NoellesRoles 由技能键打开自己的 AssassinScreen；在它即将显示的一刻换成 SparkStrength 的分组猜测界面，按键处理与其校验仍是
 * NoellesRoles 原逻辑。
 */
@Mixin(MinecraftClient.class)
public abstract class AssassinScreenSwapMixin {
    @ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
    private @Nullable Screen sparkstrength$openGroupedAssassinScreen(@Nullable Screen screen) {
        return AssassinGuessSources.replaceNativeScreen(screen);
    }
}
