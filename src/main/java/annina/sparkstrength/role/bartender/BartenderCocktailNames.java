package annina.sparkstrength.role.bartender;

import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Builds the two signature cocktail names as per-segment colour gradients. Each segment is its own translatable
 * key, so the gradient survives server-built text (the round replay) and every client language.
 * 构建两款特调的逐段渐变名称。每段都是独立的可翻译 key，因此服务端生成的文本（对局回放）和各客户端语言都能保留渐变。
 */
public final class BartenderCocktailNames {
    private BartenderCocktailNames() {
    }

    public static MutableText signatureName(BartenderRules.Signature signature) {
        MutableText name = Text.empty();
        for (int segment = 0; segment < BartenderRules.SIGNATURE_NAME_SEGMENTS; segment++) {
            int rgb = signature.segmentRgb(segment);
            name.append(Text.translatable(signature.translationKey() + "." + segment)
                    .styled(style -> style.withColor(rgb)));
        }
        return name;
    }
}
