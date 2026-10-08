package annina.sparkstrength.client.role.pathogen;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.role.pathogen.PathogenRules;
import annina.sparkstrength.role.pathogen.PathogenVisibility;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.entity.PlayerBodyEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.client.util.SkinTextures;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

/**
 * 2026-10-07: what a living converted Pathogen (revived by a T-Virus) sees. Every other non-Pathogen, player or
 * corpse, wears a gray Steve: the game's own default Steve skin, turned gray on this client when first needed, so no
 * vanilla texture ships in the jar. Wired into {@code MorphlingAppearanceClientHelper}, the one place that already
 * resolves the body, arm, cape, elytra and corpse skins, so all of them agree. Names and outlines are untouched.
 * 2026-10-07：存活的转化病原体（被 T病毒复活者）所见。除病原体以外的其他人（玩家与尸体）都显示为灰色史蒂夫：使用游戏自带的
 * 默认史蒂夫皮肤，在客户端首次需要时转为灰色，因此 jar 中不包含任何原版贴图。接入已统一解析身体、手臂、披风、鞘翅与尸体
 * 皮肤的 {@code MorphlingAppearanceClientHelper}，使它们保持一致。名字与描边不受影响。
 */
public final class PathogenGrayCrowdClient {
    private static final Identifier GRAY_STEVE_TEXTURE = SparkStrength.id("dynamic/pathogen_gray_steve");
    private static final int FALLBACK_GRAY = 0xFF808080;
    private static @Nullable SkinTextures graySteve;

    private PathogenGrayCrowdClient() {
    }

    /** Render thread: the gray Steve for this player, or {@code null} to keep its usual look. / 渲染线程。 */
    public static @Nullable SkinTextures skinFor(PlayerEntity target) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        if (viewer == null || !PathogenRules.seesAsGrayCrowd(
                PathogenVisibility.isLivingConvertedPathogen(viewer),
                viewer.getUuid().equals(target.getUuid()),
                PathogenVisibility.isPathogen(target))) {
            return null;
        }
        return graySteve();
    }

    /** Render thread: the gray Steve for a corpse whose owner was no Pathogen, else {@code null}. / 渲染线程。 */
    public static @Nullable SkinTextures skinForBody(PlayerBodyEntity body) {
        ClientPlayerEntity viewer = MinecraftClient.getInstance().player;
        UUID owner = body.getPlayerUuid();
        if (viewer == null || !PathogenRules.seesAsGrayCrowd(
                PathogenVisibility.isLivingConvertedPathogen(viewer),
                viewer.getUuid().equals(owner),
                owner != null && PathogenRules.isPathogen(GameWorldComponent.KEY.get(viewer.getWorld()).getRole(owner)))) {
            return null;
        }
        return graySteve();
    }

    private static SkinTextures graySteve() {
        SkinTextures cached = graySteve;
        if (cached == null) {
            MinecraftClient client = MinecraftClient.getInstance();
            client.getTextureManager().registerTexture(GRAY_STEVE_TEXTURE, new NativeImageBackedTexture(grayImage(client)));
            // Classic arms, no cape or elytra texture. / 经典手臂，无披风与鞘翅贴图。
            cached = new SkinTextures(GRAY_STEVE_TEXTURE, null, null, null, SkinTextures.Model.WIDE, false);
            graySteve = cached;
        }
        return cached;
    }

    private static NativeImage grayImage(MinecraftClient client) {
        try (InputStream in = client.getResourceManager().open(DefaultSkinHelper.getTexture())) {
            NativeImage image = NativeImage.read(in);
            for (int y = 0; y < image.getHeight(); y++) {
                for (int x = 0; x < image.getWidth(); x++) {
                    image.setColor(x, y, PathogenRules.grayPixel(image.getColor(x, y)));
                }
            }
            return image;
        } catch (IOException | RuntimeException exception) {
            // A resource pack without Steve: a plain gray figure still reads as "someone". / 资源包缺少史蒂夫时退回纯灰人形。
            SparkStrength.LOGGER.warn("Pathogen gray crowd: could not read the default Steve skin", exception);
            NativeImage image = new NativeImage(64, 64, true);
            image.fillRect(0, 0, 64, 64, FALLBACK_GRAY);
            return image;
        }
    }
}
