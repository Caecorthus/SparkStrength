package annina.sparkstrength.client.role.taotie;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.client.renderer.TaotieHeadEntityRenderer;
import annina.sparkstrength.compat.SparkWitchCompat;
import annina.sparkstrength.component.taotie.TaotieHeadPlayerComponent;
import annina.sparkstrength.network.taotie.TaotieHeadFireC2SPacket;
import annina.sparkstrength.role.taotie.TaotieHeadDaze;
import dev.doctor4t.wathe.cca.GameWorldComponent;
import dev.doctor4t.wathe.game.GameFunctions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.agmas.noellesroles.Noellesroles;
import org.agmas.noellesroles.taotie.SwallowedPlayerComponent;
import org.agmas.noellesroles.taotie.TaotiePlayerComponent;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Client key hook for the Taotie head launch.
 * 饕餮“发射头颅”的客户端按键挂钩。
 *
 * <p>The key is the shared "second skill key": SparkWitch's {@code key.sparkwitch.secondary_skill}, found at runtime by
 * translation key so SparkStrength never compiles against SparkWitch. Only when SparkWitch is absent do we register our
 * own fallback binding. SparkWitch's controller drains {@code wasPressed()}, so this hook reads the rising edge of
 * {@code isPressed()} and never consumes the press queue. The client gate only avoids pointless packets; the server
 * re-validates every condition.
 * 按键复用“第二技能键”：运行时按翻译键查找 SparkWitch 的 key.sparkwitch.secondary_skill，SparkStrength 不在编译期依赖
 * SparkWitch；仅当 SparkWitch 未安装时才注册自己的后备键。SparkWitch 会清空 wasPressed() 队列，所以这里只对
 * isPressed() 做上升沿检测，绝不消耗按键队列。客户端条件只用于减少无效发包，服务端会重新校验全部条件。</p>
 */
public final class TaotieHeadClientHooks {
    private static final String SHARED_KEY = "key.sparkwitch.secondary_skill";
    private static final String FALLBACK_KEY = "key.sparkstrength.secondary_skill";
    private static final String CATEGORY = "category.wathe.keybinds";
    private static final Text DEFAULT_KEY_TEXT = Text.literal("N");

    private static boolean registered;
    private static @Nullable KeyBinding fallbackBinding;
    private static @Nullable KeyBinding sharedBinding;
    private static boolean keyWasDown;

    private TaotieHeadClientHooks() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;
        if (!SparkWitchCompat.isLoaded()) {
            fallbackBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                    FALLBACK_KEY,
                    InputUtil.Type.KEYSYM,
                    GLFW.GLFW_KEY_N,
                    CATEGORY
            ));
        }
        ClientTickEvents.END_CLIENT_TICK.register(TaotieHeadClientHooks::tick);
        EntityRendererRegistry.register(SparkStrengthEntities.taotieHead(), TaotieHeadEntityRenderer::new);
    }

    /** Localized name of the bound second skill key for HUD text. / HUD 文本使用的第二技能键本地化名称。 */
    public static Text secondarySkillKeyText() {
        KeyBinding binding = secondarySkillKey();
        return binding != null ? binding.getBoundKeyLocalizedText() : DEFAULT_KEY_TEXT;
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        KeyBinding binding = secondarySkillKey();
        if (player == null || binding == null) {
            keyWasDown = false;
            return;
        }

        boolean keyDown = binding.isPressed();
        if (keyDown && !keyWasDown && client.currentScreen == null && canRequestFire(player)) {
            ClientPlayNetworking.send(new TaotieHeadFireC2SPacket());
        }
        keyWasDown = keyDown;
    }

    private static boolean canRequestFire(ClientPlayerEntity player) {
        if (!GameFunctions.isPlayerPlayingAndAlive(player)
                || !GameWorldComponent.KEY.get(player.getWorld()).isRole(player, Noellesroles.TAOTIE)
                || SwallowedPlayerComponent.isPlayerSwallowed(player)) {
            return false;
        }
        return TaotiePlayerComponent.KEY.get(player).getSwallowedCount() > 0
                && TaotieHeadPlayerComponent.KEY.get(player).getCooldownTicks() <= 0
                && !TaotieHeadDaze.isDazed(player);
    }

    /**
     * SparkWitch registers its binding during client init, so it is already in {@code options.allKeys} by the first
     * tick; the lookup is retried while it misses and cached once found.
     * SparkWitch 在客户端初始化时注册该键，首个 tick 时已在 options.allKeys 中；未找到时下次重试，找到后缓存。
     */
    private static @Nullable KeyBinding secondarySkillKey() {
        if (fallbackBinding != null) {
            return fallbackBinding;
        }
        if (sharedBinding == null) {
            sharedBinding = findBinding(SHARED_KEY);
        }
        return sharedBinding;
    }

    private static @Nullable KeyBinding findBinding(String translationKey) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options == null) {
            return null;
        }
        for (KeyBinding binding : client.options.allKeys) {
            if (translationKey.equals(binding.getTranslationKey())) {
                return binding;
            }
        }
        return null;
    }
}
