package annina.sparkstrength.client.mixin.spiritualist;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Writes {@code MinecraftClient.cameraEntity} without {@code setCameraEntity}: vanilla's setter also calls
 * {@code GameRenderer.onCameraEntitySet}, which drops the post-processing shader, and NoellesRoles only loads its spirit
 * grayscale when its projection starts, so a possession swap through the setter would leave the spirit view in colour.
 * 不经 setCameraEntity 直接写入 MinecraftClient.cameraEntity：原版的 setter 还会调用 GameRenderer.onCameraEntitySet 丢弃后处理
 * 着色器，而 NoellesRoles 只在出窍开始时加载灵界灰度，经 setter 切换附身镜头会让灵魂画面恢复彩色。
 */
@Mixin(MinecraftClient.class)
public interface SpiritPossessionCameraAccessor {
    @Accessor("cameraEntity")
    void sparkstrength$setCameraEntityField(Entity entity);
}
