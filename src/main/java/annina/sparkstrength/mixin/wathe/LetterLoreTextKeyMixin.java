package annina.sparkstrength.mixin.wathe;

import annina.sparkstrength.util.UpstreamTextKeys;
import dev.doctor4t.wathe.api.Role;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Points the round letter's retuned lines (the reworked Bodyguard's postscript) at SparkStrength's keys once Wathe
 * has built the lore. Wathe still picks each line by checking that the upstream key exists, which NoellesRoles
 * guarantees; only the shown text moves to the SparkStrength key. Works on the finished lore because the lines are
 * built inside a lambda whose number differs between the two Wathe 1.5.6 builds.
 * Wathe 建好信件 lore 后，把需要改写的行（重做后保镖的附言）指向 SparkStrength 的键。Wathe 仍按上游键是否存在来决定加
 * 哪一行（NoellesRoles 保证存在），只有显示的文字换成 SparkStrength 的键。在成品 lore 上改，因为这些行是在一个 lambda 里
 * 构造的，两个 Wathe 1.5.6 构建的 lambda 编号不同。
 */
@Mixin(GameFunctions.class)
public abstract class LetterLoreTextKeyMixin {
    @Inject(method = "applyLetterLore", at = @At("TAIL"))
    private static void sparkstrength$retunedLetterLines(
            ItemStack letter,
            ServerPlayerEntity player,
            Role role,
            String roleName,
            String factionName,
            int letterColor,
            int roleColor,
            CallbackInfo ci
    ) {
        LoreComponent lore = letter.get(DataComponentTypes.LORE);
        if (lore == null) {
            return;
        }
        List<Text> lines = UpstreamTextKeys.retargetLines(lore.lines());
        if (lines != null) {
            letter.set(DataComponentTypes.LORE, new LoreComponent(lines));
        }
    }
}
