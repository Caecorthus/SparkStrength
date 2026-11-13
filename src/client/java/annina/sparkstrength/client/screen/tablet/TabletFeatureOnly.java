package annina.sparkstrength.client.screen.tablet;

import annina.sparkstrength.client.screen.tablet.TabletTheme.Accent;
import annina.sparkstrength.network.tablet.TabletSnapshot;
import annina.sparkstrength.tablet.TabletFeature;
import annina.sparkstrength.tablet.TabletUiSession.Section;
import org.jetbrains.annotations.Nullable;

/**
 * Pure rules for feature-only holders: whether the snapshot is one, which feature section names the tablet, and the
 * accent the frame wears as a result. The caller passes the open section; nothing here reads screen state.
 * 仅功能持有者的纯规则：快照是否属于仅功能、由哪个功能分区命名平板，以及外框因此使用的强调色。打开的分区由调用方传入，
 * 这里不读取任何界面状态。
 */
final class TabletFeatureOnly {
    private TabletFeatureOnly() {
    }

    static Accent frameAccentFor(TabletSnapshot snapshot, Section openSection) {
        Section feature = featureOnlySection(snapshot, openSection);
        TabletFeature required = feature == null ? null : feature.requiredFeature();
        return required != null ? TabletTheme.accentFor(required) : TabletTheme.accentFor(snapshot.channel());
    }

    /**
     * Feature sections are the holder's only content: no channel now and none to switch to (a plain Attendant, a
     * SparkTraits Conscience Bomber).
     * 功能分区是持有者唯一的内容：当前无频道且没有可切换的频道（普通乘务员、SparkTraits 善良炸弹客）。
     */
    static boolean featureOnly(TabletSnapshot snapshot) {
        return snapshot.localHasTablet()
                && snapshot.channel() == null
                && snapshot.allowedChannels().isEmpty()
                && !snapshot.features().isEmpty();
    }

    /**
     * For a feature-only holder, the feature section that names the tablet (chip, badge, accent): the open section when
     * it is a granted feature section, else the first granted one. Null when the holder is not feature-only.
     * 仅功能持有者用于命名平板（胶囊、徽标、强调色）的功能分区：当前打开的分区若为已授予的功能分区则用它，否则用第一个已授予的；
     * 非仅功能持有者为 null。
     */
    static @Nullable Section featureOnlySection(TabletSnapshot snapshot, Section openSection) {
        if (!featureOnly(snapshot)) {
            return null;
        }
        TabletFeature open = openSection.requiredFeature();
        if (open != null && snapshot.hasFeature(open)) {
            return openSection;
        }
        for (Section section : Section.values()) {
            TabletFeature required = section.requiredFeature();
            if (required != null && snapshot.hasFeature(required)) {
                return section;
            }
        }
        return null;
    }
}
