package annina.sparkstrength.client.screen.tablet;

import net.minecraft.text.Text;

enum TabletTab {
    CONNECTIONS("screen.sparkstrength.tablet.tab.connections"),
    CHAT("screen.sparkstrength.tablet.tab.chat"),
    MEETING("screen.sparkstrength.tablet.tab.meeting"),
    SUSPECTS("screen.sparkstrength.tablet.tab.suspects");

    private final String translationKey;

    TabletTab(String translationKey) {
        this.translationKey = translationKey;
    }

    Text label() {
        return Text.translatable(translationKey);
    }
}
