package annina.sparkstrength.item;

import annina.sparkstrength.role.undercover.UndercoverRules;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;

import java.util.List;

/**
 * The stage-2 Undercover's tactical tomahawk (after the Kiba Arms KATT). Attack speed 1: a fully charged left click
 * kills (UndercoverTomahawkService); holding right-click on a door the holder cannot open chops it down
 * (DoorBreachService). Kill and breach share one item cooldown. It never drops and nobody else can hold it.
 * 二阶段卧底的战术斧（参考 Kiba Arms KATT）。攻速 1：蓄满的左键直接击杀（UndercoverTomahawkService）；对着打不开的门
 * 按住右键会把门拆掉（DoorBreachService）。击杀与拆门共用一个物品冷却。它不会掉落，别人也拿不到。
 */
public final class TacticalTomahawkItem extends Item {
    public TacticalTomahawkItem(Settings settings) {
        super(settings.attributeModifiers(attributes()));
    }

    /** Only the swing speed matters: kills never go through vanilla damage. / 只有挥击速度有意义：击杀从不走原版伤害。 */
    private static AttributeModifiersComponent attributes() {
        return AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID,
                        UndercoverRules.TOMAHAWK_ATTACK_SPEED - 4.0, EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)
                .build();
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        for (int line = 1; line <= 3; line++) {
            tooltip.add(Text.translatable("item.sparkstrength.tactical_tomahawk.tooltip.line" + line)
                    .styled(style -> style.withColor(0x808080).withItalic(false)));
        }
    }
}
