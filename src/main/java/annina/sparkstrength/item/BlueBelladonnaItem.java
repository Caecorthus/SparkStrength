package annina.sparkstrength.item;

import annina.sparkstrength.compat.SparkTraitsBluePoisonCompat;
import annina.sparkstrength.role.toxicologist.ToxicologistBlueRules;
import dev.doctor4t.wathe.game.GameFunctions;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.world.World;
import net.minecraft.world.event.GameEvent;

import java.util.List;
import java.util.UUID;

/**
 * Blue Belladonna: a blue fruit carrying the buyer's blue-poison marker. A Toxicologist-like eater enters the blue
 * state; anyone else gets exactly what blue-poisoned food does (SparkTraits decides sanity drain vs lethal poison).
 * 蓝颠茄：带有购买者蓝毒标记的蓝色果实。类毒理学家食用后进入蓝毒状态；其他人食用的结果与蓝毒食物完全一致
 * （由 SparkTraits 判定是扣理智还是致死蓝毒）。
 */
public final class BlueBelladonnaItem extends Item {
    /**
     * A vanilla FOOD component keeps the vanilla eat animation/sounds and lets capsules accept the fruit; it restores
     * nothing and is always edible (Wathe also forces canConsume to true). snack() is vanilla's 0.8 s eat time, the
     * same as {@link ToxicologistBlueRules#BELLADONNA_EAT_TICKS}.
     * 使用原版 FOOD 组件以保留原版进食动画与音效，并让胶囊可以装入；不恢复饥饿，且总是可食用（Wathe 也强制 canConsume 为 true）。
     * snack() 即原版 0.8 秒进食时间，与 BELLADONNA_EAT_TICKS 一致。
     */
    public static final FoodComponent FOOD = new FoodComponent.Builder()
            .nutrition(0)
            .saturationModifier(0.0F)
            .alwaysEdible()
            .snack()
            .build();

    public BlueBelladonnaItem(Settings settings) {
        super(settings);
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return ToxicologistBlueRules.BELLADONNA_EAT_TICKS;
    }

    /**
     * Deliberately does not call super: super runs PlayerEntity.eatFood, where Wathe completes the EAT mood task and
     * runs PoisonUtils.applyFoodPoison (whose SparkTraits hook would also strip the blue marker from the whole stack).
     * 刻意不调用 super：super 会进入 PlayerEntity.eatFood，Wathe 会在那里完成进食心情任务并执行 PoisonUtils.applyFoodPoison
     * （其上的 SparkTraits 钩子还会把整组果实的蓝毒标记一起清掉）。
     */
    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (!world.isClient() && user instanceof ServerPlayerEntity player) {
            applyEaten(player, stack);
            // Private burp: SparkTraits' Cautious trait mutes the public burp inside eatFood, which is bypassed here,
            // so broadcasting it would leak that trait. Chewing sounds still go through the vanilla (muted) path.
            // 打嗝声只给本人：SparkTraits 的谨慎天赋在 eatFood 内静音公开打嗝声，而这里绕过了 eatFood，
            // 公开播放会暴露该天赋。咀嚼声仍走原版（可被静音的）路径。
            player.playSoundToPlayer(
                    SoundEvents.ENTITY_PLAYER_BURP,
                    SoundCategory.PLAYERS,
                    0.5F,
                    world.getRandom().nextFloat() * 0.1F + 0.9F
            );
        }
        user.emitGameEvent(GameEvent.EAT);
        stack.decrementUnlessCreative(1, user);
        return stack;
    }

    /**
     * Server-side outcome of eating one fruit, shared by hand eating and capsule hits. Reads the marker without
     * removing it, so the rest of the stack stays poisoned.
     * 服务端结算吃下一颗果实的效果，手动食用与胶囊命中共用。只读取标记不移除，剩余果实仍保持带毒。
     */
    public static void applyEaten(ServerPlayerEntity eater, ItemStack fruit) {
        if (!GameFunctions.isPlayerPlayingAndAlive(eater)) {
            return;
        }
        if (ToxicologistBlueRules.isToxicologistLike(eater)) {
            SparkTraitsBluePoisonCompat.applyBlueSanityDrain(eater, ToxicologistBlueRules.BELLADONNA_BLUE_TICKS);
            return;
        }
        UUID poisoner = SparkTraitsBluePoisonCompat.getStackBluePoisoner(fruit);
        if (poisoner != null) {
            SparkTraitsBluePoisonCompat.applyBlueTrap(eater, poisoner);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 2; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.blue_belladonna.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
