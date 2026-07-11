package annina.sparkstrength.item;

import annina.sparkstrength.role.engineer.EngineerCaptureDeviceService;
import dev.doctor4t.wathe.util.AdventureUsable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.List;

/**
 * Minecraft item Adapter for the Engineer capture-device domain service.
 * 工程师捕捉装置领域服务的 Minecraft 物品 Adapter。
 *
 * <p>The service owns placement policy, spawning, sounds, replay, detection, stun, and reports.
 * 服务拥有放置规则、生成、声音、回放、检测、定身与报告。</p>
 */
public final class CaptureDeviceItem extends Item implements AdventureUsable {
    public CaptureDeviceItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        return EngineerCaptureDeviceService.place(context);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.sparkstrength.capture_device.tooltip.line1")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
        tooltip.add(Text.translatable("item.sparkstrength.capture_device.tooltip.line2")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
        tooltip.add(Text.translatable("item.sparkstrength.capture_device.tooltip.line3")
                .styled(style -> style.withColor(0x808080).withItalic(false)));
    }
}
