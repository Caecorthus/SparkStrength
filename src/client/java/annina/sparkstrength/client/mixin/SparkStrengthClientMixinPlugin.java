package annina.sparkstrength.client.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Client mixin config plugin: skips mixins that cannot coexist with a renderer mod installed alongside.
 * 客户端 mixin 配置插件：跳过无法与同时安装的渲染模组共存的 mixin。
 *
 * <p>Sodium {@code @Overwrite}s {@code WorldRenderer.setupTerrain}. Mixin rejects any injector into an overwritten
 * method while applying, and {@code require = 0} does not prevent that, so with Sodium installed the game crashed on
 * launch. {@code bomber.DronePilotTerrainGridMixin} and {@code spiritualist.SpiritPossessionTerrainGridMixin} are
 * therefore not applied when Sodium is loaded; Sodium's renderer already centres terrain on the camera, so drone piloting
 * and Wraith possession still draw far terrain.
 * Sodium 以 @Overwrite 替换 WorldRenderer.setupTerrain。Mixin 在应用阶段拒绝向被覆盖的方法注入，require = 0 也无法避免，
 * 因此安装 Sodium 时游戏启动即崩溃。故 Sodium 存在时不应用 bomber.DronePilotTerrainGridMixin 与
 * spiritualist.SpiritPossessionTerrainGridMixin；Sodium 的渲染器本就以镜头为中心渲染地形，无人机驾驶与附身冤魂时远处地形仍会绘制。</p>
 */
public final class SparkStrengthClientMixinPlugin implements IMixinConfigPlugin {
    /** {@code WorldRenderer.setupTerrain} injectors (camera-centred render grid). / setupTerrain 注入（以镜头为中心的渲染网格）。 */
    private static final Set<String> SODIUM_SKIPPED_MIXINS = Set.of(
            "annina.sparkstrength.client.mixin.bomber.DronePilotTerrainGridMixin",
            "annina.sparkstrength.client.mixin.spiritualist.SpiritPossessionTerrainGridMixin");

    private boolean sodiumLoaded;

    @Override
    public void onLoad(String mixinPackage) {
        sodiumLoaded = FabricLoader.getInstance().isModLoaded("sodium");
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !(sodiumLoaded && SODIUM_SKIPPED_MIXINS.contains(mixinClassName));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
