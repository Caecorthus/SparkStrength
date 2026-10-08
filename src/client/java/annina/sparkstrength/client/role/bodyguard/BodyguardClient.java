package annina.sparkstrength.client.role.bodyguard;

import annina.sparkstrength.SparkStrengthItems;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.util.Identifier;

/**
 * Client entry point for the Bodyguard's Democracy Shield: the vanilla "blocking" model predicate, which vanilla only
 * registers for its own shield, switches the item to the raised model.
 * 保镖民主盾牌的客户端入口：原版只给自己的盾注册了 "blocking" 模型谓词，这里为民主盾牌注册，使举盾时切换到举盾模型。
 */
public final class BodyguardClient {
    private BodyguardClient() {
    }

    public static void register() {
        ModelPredicateProviderRegistry.register(
                SparkStrengthItems.democracyShield(),
                Identifier.ofVanilla("blocking"),
                (stack, world, entity, seed) ->
                        entity != null && entity.isUsingItem() && entity.getActiveItem() == stack ? 1.0F : 0.0F
        );
    }
}
