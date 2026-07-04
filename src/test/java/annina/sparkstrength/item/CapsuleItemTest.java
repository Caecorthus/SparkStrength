package annina.sparkstrength.item;

import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.text.TranslatableTextContent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CapsuleItemTest {
    @Test
    void poisonedContentTextUsesPoisonTranslationAndColors() {
        assertPoisonedContent(CapsuleItem.contentDisplayText("Cookie", true, false), "Cookie", 0x1E5014);
        assertPoisonedContent(CapsuleItem.contentDisplayText("Cookie", false, true), "Cookie", 0x00BFFF);
        assertPoisonedContent(CapsuleItem.contentDisplayText("Cookie", true, true), "Cookie", 0x0F8789);
    }

    @Test
    void filledNameAndTooltipBothShowPoisonedContents() {
        Text name = CapsuleItem.filledNameText("Suspicious Stew", true, false);
        TranslatableTextContent nameContent = assertTranslation(name, "item.sparkstrength.capsule.filled");
        assertPoisonedContent(textArgument(nameContent, 0), "Suspicious Stew", 0x1E5014);

        Text tooltip = CapsuleItem.tooltipText("Suspicious Stew", true, false);
        TranslatableTextContent tooltipContent = assertTranslation(tooltip, "item.sparkstrength.capsule.tooltip");
        assertPoisonedContent(textArgument(tooltipContent, 0), "Suspicious Stew", 0x1E5014);
    }

    private static void assertPoisonedContent(Text text, String expectedName, int expectedColor) {
        TranslatableTextContent content = assertTranslation(text, "item.sparkstrength.capsule.poisoned_content");
        assertEquals(expectedName, textArgument(content, 0).getString());
        TextColor color = text.getStyle().getColor();
        assertNotNull(color);
        assertEquals(expectedColor, color.getRgb());
    }

    private static TranslatableTextContent assertTranslation(Text text, String key) {
        TranslatableTextContent content = assertInstanceOf(TranslatableTextContent.class, text.getContent());
        assertEquals(key, content.getKey());
        return content;
    }

    private static Text textArgument(TranslatableTextContent content, int index) {
        return assertInstanceOf(Text.class, content.getArgs()[index]);
    }
}
