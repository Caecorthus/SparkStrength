package annina.sparkstrength.role.bandit;

/** The three places a part goes on the Bandit's revolver. / 强盗改装左轮上能装配件的三个部位。 */
public enum BanditAttachmentSlot {
    BARREL("barrel"),
    GRIP("grip"),
    CYLINDER("cylinder");

    private final String key;

    BanditAttachmentSlot(String key) {
        this.key = key;
    }

    /** Lang/key fragment. / 语言键片段。 */
    public String key() {
        return key;
    }
}
