package annina.sparkstrength.client.mixin.assassin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Builds a screen's widgets without showing it: the public {@code init(client, width, height)} also narrates the
 * screen, which a throwaway probe initialised once a second must not do.
 * 在不显示界面的情况下生成其控件：公开的 init(client, width, height) 还会触发旁白朗读，每秒初始化一次的一次性探测界面不能这样做。
 */
@Mixin(Screen.class)
public interface ScreenInitInvoker {
    @Invoker("init")
    void sparkstrength$init();

    @Accessor("client")
    void sparkstrength$setClient(MinecraftClient client);
}
