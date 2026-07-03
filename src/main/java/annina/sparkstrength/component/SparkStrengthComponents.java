package annina.sparkstrength.component;

import annina.sparkstrength.component.role.RoleEnhancementPlayerComponent;
import annina.sparkstrength.component.role.RoleEnhancementWorldComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowTargetComponent;
import annina.sparkstrength.component.noisemaker.NoisemakerGlowUserComponent;
import net.minecraft.entity.player.PlayerEntity;
import org.jetbrains.annotations.NotNull;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.entity.RespawnCopyStrategy;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

/**
 * SparkStrength 的 CCA 组件注册入口。
 *
 * <p>两个组件都使用 NEVER_COPY：新一轮、死亡重生或玩家重置时不继承旧状态，
 * 防止上一局的点亮冷却或发光倒计时残留到下一局。</p>
 */
public class SparkStrengthComponents implements EntityComponentInitializer, WorldComponentInitializer {
    @Override
    public void registerEntityComponentFactories(@NotNull EntityComponentFactoryRegistry registry) {
        registry.beginRegistration(PlayerEntity.class, NoisemakerGlowUserComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(NoisemakerGlowUserComponent::new);
        registry.beginRegistration(PlayerEntity.class, NoisemakerGlowTargetComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(NoisemakerGlowTargetComponent::new);
        registry.beginRegistration(PlayerEntity.class, RoleEnhancementPlayerComponent.KEY)
                .respawnStrategy(RespawnCopyStrategy.NEVER_COPY)
                .end(RoleEnhancementPlayerComponent::new);
    }

    @Override
    public void registerWorldComponentFactories(@NotNull WorldComponentFactoryRegistry registry) {
        registry.register(RoleEnhancementWorldComponent.KEY, RoleEnhancementWorldComponent::new);
    }
}
