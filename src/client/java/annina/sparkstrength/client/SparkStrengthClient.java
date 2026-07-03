package annina.sparkstrength.client;

import net.fabricmc.api.ClientModInitializer;

public final class SparkStrengthClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // 当前客户端逻辑主要由 mixin 注入背包界面完成。
        // 这里保留入口，后续如果要增加 HUD 或客户端网络包可以直接集中注册。
    }
}
