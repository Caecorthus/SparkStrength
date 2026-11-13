package annina.sparkstrength.component.professor;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.component.OwnerCooldownComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;

/**
 * 教授远程投喂背包技能的使用者冷却。
 *
 * <p>冷却只同步给教授本人，用于背包头像和试剂按钮显示剩余秒数；
 * 服务端依旧会在收到网络包后重新检查冷却，客户端显示只负责交互体验。</p>
 */
public class ProfessorSerumUserComponent extends OwnerCooldownComponent {
    public static final ComponentKey<ProfessorSerumUserComponent> KEY = ComponentRegistry.getOrCreate(
            SparkStrength.id("professor_serum_user"),
            ProfessorSerumUserComponent.class
    );

    public ProfessorSerumUserComponent(PlayerEntity player) {
        super(player);
    }

    @Override
    protected void sync() {
        KEY.sync(player);
    }
}
