package annina.sparkstrength.role.assassin;

import annina.sparkstrength.component.assassin.AssassinMisfireComponent;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.assassin.AssassinPlayerComponent;

/**
 * Server half of the Assassin buff, called from {@code AssassinGuessOutcomeMixin} inside NoellesRoles' guess handler.
 * 刺客加强的服务端部分，由 AssassinGuessOutcomeMixin 在 NoellesRoles 的猜测处理函数内调用。
 */
public final class AssassinGuessService {
    private AssassinGuessService() {
    }

    /** Whether a wrong guess right now would be forgiven; spends nothing. / 此刻猜错是否会被宽恕；不消耗任何东西。 */
    public static boolean missWillBeForgiven(ServerPlayerEntity assassin) {
        return AssassinMisfireComponent.KEY.get(assassin).hasFreeMiss();
    }

    /**
     * A wrong guess is about to kill the Assassin. Returns true when it is forgiven instead: the free miss is spent,
     * the action bar (NoellesRoles just wrote its "you pay for it" line there) says the next one is lethal, and only
     * the Assassin hears a dry click (owner 2026-11-12: nobody else hears a forgiven miss).
     * 猜错即将反噬刺客。返回 true 表示改为宽恕：用掉免死机会，动作栏（NoellesRoles 刚写了“付出代价”的提示）改为提示下一次致命，
     * 并且只有刺客本人听到一声哑火（负责人 2026-11-12：被宽恕的猜错其他人听不到）。
     */
    public static boolean forgiveMiss(ServerPlayerEntity assassin, ServerPlayerEntity target) {
        if (!AssassinMisfireComponent.KEY.get(assassin).forgiveMiss()) {
            return false;
        }
        assassin.playSoundToPlayer(SoundEvents.BLOCK_DISPENSER_FAIL, SoundCategory.PLAYERS, 1.0F, 1.2F);
        assassin.sendMessage(
                Text.translatable("message.sparkstrength.assassin.miss_forgiven", target.getName())
                        .formatted(Formatting.GOLD, Formatting.BOLD),
                true
        );
        return true;
    }

    /** Runs right after NoellesRoles' {@code useGuess()}. / 紧跟在 NoellesRoles 的 useGuess() 之后执行。 */
    public static void afterGuess(ServerPlayerEntity assassin, boolean guessedCorrectly) {
        AssassinPlayerComponent guesses = AssassinPlayerComponent.KEY.get(assassin);
        int current = guesses.getCooldownTicks();
        int adjusted = AssassinGuessRules.cooldownAfterGuess(guessedCorrectly, current);
        if (adjusted != current) {
            guesses.setCooldown(adjusted);
        }
    }

    public static void reset(ServerPlayerEntity player) {
        AssassinMisfireComponent.KEY.get(player).reset();
    }
}
