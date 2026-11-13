package annina.sparkstrength.client.mixin.assassin;

import dev.doctor4t.wathe.api.Role;
import org.agmas.noellesroles.client.screen.AssassinScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

/**
 * Reads NoellesRoles' own guessable-role list, so whatever other mods mix into it (SparkWitch hides the Savior and keeps
 * the Saboteur) also shapes SparkStrength's guess screen.
 * 读取 NoellesRoles 自己的可猜身份列表，让其他模组对它的修改（SparkWitch 隐藏救世主、保留破坏者）同样作用于 SparkStrength 的猜测界面。
 */
@Mixin(value = AssassinScreen.class, remap = false)
public interface AssassinScreenInvoker {
    @Invoker(value = "getAllGuessableRoles", remap = false)
    List<Role> sparkstrength$guessableRoles();
}
