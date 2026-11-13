package annina.sparkstrength.client.mixin.wathe;

import annina.sparkstrength.util.UpstreamTextKeys;
import dev.doctor4t.wathe.client.gui.RoleAnnouncementTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * Gives the NoellesRoles roles SparkStrength reworks (see {@link UpstreamTextKeys#retunesGoal}) a welcome goal line
 * from SparkStrength's own keys, so the reworked text shows whatever the mod set (redefining NoellesRoles' key left it
 * to namespace order). Replaces the goal function itself rather than redirecting {@code RoundTextRenderer}, whose
 * goal call SparkTraits already redirects; its fallbacks still call this function.
 * 让 SparkStrength 重做的 NoellesRoles 身份（见 {@link UpstreamTextKeys#retunesGoal}）的开局目标句改用 SparkStrength
 * 自己的键，无论装了哪些模组都显示重做后的文字（重定义 NoellesRoles 的键时取决于命名空间顺序）。这里替换目标函数本身，
 * 而不是重定向 {@code RoundTextRenderer}：那里的目标调用已被 SparkTraits 重定向，它的回退分支仍会调用这个函数。
 */
@Mixin(value = RoleAnnouncementTexts.RoleAnnouncementText.class, remap = false)
public abstract class RoleAnnouncementGoalTextMixin {
    @Shadow
    @Final
    @Mutable
    public Function<Integer, Text> goalText;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void sparkstrength$retunedGoalText(Identifier identifier, int colour, CallbackInfo ci) {
        if (UpstreamTextKeys.retunesGoal(identifier.getNamespace(), identifier.getPath())) {
            this.goalText = UpstreamTextKeys.goalText(identifier.getPath(), colour);
        }
    }
}
