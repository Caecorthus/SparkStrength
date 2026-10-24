package annina.sparkstrength.client.renderer;

import annina.sparkstrength.SparkStrength;
import annina.sparkstrength.entity.PartygoerEntity;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.MobEntityRenderer;
import net.minecraft.util.Identifier;

/**
 * Renders the Partygoer with the owner-supplied skin (2026-10-21, a yellow figure with a red smiley).
 * 用主人提供的皮肤渲染派对客（2026-10-21，红色笑脸的黄色人形）。
 */
public final class PartygoerEntityRenderer extends MobEntityRenderer<PartygoerEntity, PartygoerModel> {
    public static final Identifier TEXTURE = SparkStrength.id("textures/entity/partygoer.png");

    public PartygoerEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new PartygoerModel(context.getPart(PartygoerModel.LAYER)), 0.5F);
    }

    @Override
    public Identifier getTexture(PartygoerEntity entity) {
        return TEXTURE;
    }

    @Override
    protected boolean hasLabel(PartygoerEntity entity) {
        return false;
    }
}
