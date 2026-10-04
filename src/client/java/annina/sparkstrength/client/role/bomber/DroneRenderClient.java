package annina.sparkstrength.client.role.bomber;

import annina.sparkstrength.SparkStrengthEntities;
import annina.sparkstrength.client.renderer.BombDroneModel;
import annina.sparkstrength.client.renderer.DroneEntityRenderer;
import annina.sparkstrength.client.renderer.GrenadeDroneModel;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;

/** Entity renderers + model layers for both drones. / 两种无人机的实体渲染器与模型层。 */
public final class DroneRenderClient {
    private DroneRenderClient() {
    }

    public static void register() {
        EntityModelLayerRegistry.registerModelLayer(GrenadeDroneModel.LAYER, GrenadeDroneModel::getTexturedModelData);
        EntityModelLayerRegistry.registerModelLayer(BombDroneModel.LAYER, BombDroneModel::getTexturedModelData);
        EntityRendererRegistry.register(SparkStrengthEntities.grenadeDrone(), DroneEntityRenderer::grenade);
        EntityRendererRegistry.register(SparkStrengthEntities.bombDrone(), DroneEntityRenderer::bomb);
    }
}
