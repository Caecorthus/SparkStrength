package annina.sparkstrength.component.phantom;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.OwnerCooldownComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 幽灵背包按钮的“使用者冷却”。
 *
 * <p>NoellesRoles 原幽灵按键隐身使用通用能力冷却；这里单独保存背包按钮冷却，
 * 保证两个技能互不影响，同时只同步给幽灵本人用于背包头像上的倒计时显示。</p>
 */
public class PhantomBackpackUserComponent extends OwnerCooldownComponent {
    public static final ComponentKey<PhantomBackpackUserComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("phantom_backpack_user"),
            PhantomBackpackUserComponent.class
    );

    public PhantomBackpackUserComponent(PlayerEntity player) {
        super(player);
    }

    @Override
    protected void sync() {
        KEY.sync(player);
    }
}
