package annina.sparkstrength.client.screen.bandit;

import annina.sparkstrength.SparkStrengthItems;
import annina.sparkstrength.item.BanditAmmoItem;
import annina.sparkstrength.item.BanditAttachmentItem;
import annina.sparkstrength.item.BanditRevolverItem;
import annina.sparkstrength.network.bandit.BanditRevolverActionC2SPayload;
import annina.sparkstrength.role.bandit.BanditAmmo;
import annina.sparkstrength.role.bandit.BanditAttachment;
import annina.sparkstrength.role.bandit.BanditAttachmentSlot;
import annina.sparkstrength.role.bandit.BanditRevolverRules;
import annina.sparkstrength.role.bandit.BanditRevolverService;
import annina.sparkstrength.role.bandit.BanditRevolverState;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.agmas.noellesroles.client.NoellesrolesClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Workbench of the Bandit's moddable revolver, opened by right-clicking the revolver in the inventory with an empty
 * cursor. Everything shown is read from the live stack every frame; clicks only send
 * {@code sparkstrength:bandit_revolver_action}, and the server's slot sync brings the result back.
 * 强盗改装左轮的改装界面，在背包中空手右键左轮打开。显示内容每帧读取实际物品；点击只发送
 * {@code sparkstrength:bandit_revolver_action}，结果由服务端的格子同步带回。
 */
public final class BanditWorkbenchScreen extends Screen {
    private static final int PANEL_WIDTH = 248;
    private static final int PANEL_HEIGHT = 170;
    /** NoellesRoles' Bandit role colour (90, 100, 40) and a lighter text shade of it. / 强盗身份色及其较亮的文字色。 */
    private static final int ACCENT = 0xFF5A6428;
    private static final int ACCENT_TEXT = 0xFFB9C66A;
    private static final int BACKGROUND = 0xF016181C;
    private static final int CELL = 0xFF25282E;
    private static final int CELL_HOVER = 0xFF343840;
    private static final int TEXT = 0xFFE8E8E8;
    private static final int MUTED = 0xFF8E949C;
    private static final int BETTER = 0xFF86D886;
    private static final int WORSE = 0xFFE87878;
    private static final int COLOR_PS12 = 0xFFE0B050;
    private static final int COLOR_PS12B = 0xFF6FB6E8;

    private record Zone(int x, int y, int width, int height, Runnable action, List<Text> tooltip) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
        }
    }

    /** Click targets of the frame last drawn. / 最近一帧绘制出的可点击区域。 */
    private final List<Zone> zones = new ArrayList<>();

    public BanditWorkbenchScreen() {
        super(Text.translatable("screen.sparkstrength.bandit_workbench.title"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        zones.clear();
        if (client == null || client.player == null) {
            return;
        }
        PlayerInventory inventory = client.player.getInventory();
        int gunSlot = BanditRevolverService.findRevolverSlot(inventory);
        if (gunSlot < 0) {
            close();
            return;
        }
        ItemStack gun = inventory.getStack(gunSlot);
        BanditRevolverState state = BanditRevolverItem.state(gun);
        int left = (width - PANEL_WIDTH) / 2;
        int top = (height - PANEL_HEIGHT) / 2;

        context.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, BACKGROUND);
        context.fill(left, top, left + PANEL_WIDTH, top + 2, ACCENT);
        context.fill(left, top + PANEL_HEIGHT - 2, left + PANEL_WIDTH, top + PANEL_HEIGHT, ACCENT);
        context.drawText(textRenderer, title, left + 10, top + 9, ACCENT_TEXT, false);

        drawGun(context, gun, left + 10, top + 24);
        drawParts(context, state, inventory, left + 96, top + 24, mouseX, mouseY);
        drawStats(context, state, left + 10, top + 104);
        drawCylinder(context, state, left + 10, top + 131, mouseX, mouseY);
        drawSupplies(context, state, inventory, left + 136, top + 131, mouseX, mouseY);

        KeyBinding ability = NoellesrolesClient.abilityBind;
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.sparkstrength.bandit_workbench.hint"), width / 2, top + PANEL_HEIGHT + 6, MUTED);
        context.drawCenteredTextWithShadow(textRenderer,
                Text.translatable("screen.sparkstrength.bandit_workbench.hint_load",
                        ability == null ? Text.literal("G") : ability.getBoundKeyLocalizedText()),
                width / 2, top + PANEL_HEIGHT + 17, MUTED);

        for (Zone zone : zones) {
            if (zone.contains(mouseX, mouseY) && !zone.tooltip().isEmpty()) {
                context.drawTooltip(textRenderer, zone.tooltip(), mouseX, mouseY);
                break;
            }
        }
    }

    private void drawGun(DrawContext context, ItemStack gun, int x, int y) {
        context.fill(x, y, x + 76, y + 76, CELL);
        context.getMatrices().push();
        context.getMatrices().translate(x + 6.0F, y + 6.0F, 0.0F);
        context.getMatrices().scale(4.0F, 4.0F, 1.0F);
        context.drawItem(gun, 0, 0);
        context.getMatrices().pop();
    }

    private void drawParts(DrawContext context, BanditRevolverState state, PlayerInventory inventory, int x, int y,
                           int mouseX, int mouseY) {
        for (BanditAttachmentSlot slot : BanditAttachmentSlot.values()) {
            int rowY = y + slot.ordinal() * 26;
            BanditAttachment part = state.part(slot);
            List<Text> tooltip = new ArrayList<>();
            Runnable action = null;
            if (part != null) {
                tooltip.add(Text.translatable("item.sparkstrength.bandit_" + part.key()).formatted(Formatting.GOLD));
                tooltip.add(Text.translatable("item.sparkstrength.bandit_" + part.key() + ".description")
                        .formatted(Formatting.GRAY));
                tooltip.add(Text.translatable("screen.sparkstrength.bandit_workbench.click_remove")
                        .formatted(Formatting.DARK_GRAY));
                action = () -> send(BanditRevolverService.Action.REMOVE, slot.ordinal());
            }
            Zone zone = new Zone(x, rowY, 142, 24, action, tooltip);
            zones.add(zone);
            context.fill(x, rowY, x + 142, rowY + 24,
                    part != null && zone.contains(mouseX, mouseY) ? CELL_HOVER : CELL);
            context.fill(x + 3, rowY + 3, x + 21, rowY + 21, 0xFF16181C);
            if (part != null) {
                context.drawItem(new ItemStack(SparkStrengthItems.banditAttachment(part)), x + 4, rowY + 4);
            }
            context.drawText(textRenderer, Text.translatable("item.sparkstrength.bandit_part.slot." + slot.key()),
                    x + 26, rowY + 3, MUTED, false);
            context.drawText(textRenderer, part == null
                            ? Text.translatable("item.sparkstrength.bandit_revolver.part_none")
                            : Text.translatable("item.sparkstrength.bandit_" + part.key()),
                    x + 26, rowY + 13, part == null ? MUTED : TEXT, false);
        }
    }

    private void drawStats(DrawContext context, BanditRevolverState state, int x, int y) {
        BanditAttachment barrel = state.barrelPart();
        BanditAttachment grip = state.gripPart();
        BanditAttachment cylinder = state.cylinderPart();
        double range = BanditRevolverRules.rangeBlocks(barrel);
        int cooldown = BanditRevolverRules.cooldownTicks(barrel, grip);
        double speed = BanditRevolverRules.speedBonus(barrel, grip);
        int load = BanditRevolverRules.loadTicks(cylinder);
        int hits = BanditRevolverRules.hits(barrel, null);
        String[] keys = {"range", "cooldown", "speed", "load", "hits"};
        String[] values = {
                String.format(Locale.ROOT, "%.0f", range),
                seconds(cooldown),
                "+" + Math.round(speed * 100.0D) + "%",
                seconds(load),
                "x" + hits
        };
        int[] verdicts = {
                Double.compare(range, BanditRevolverRules.BASE_RANGE_BLOCKS),
                Integer.compare(BanditRevolverRules.BASE_COOLDOWN_TICKS, cooldown),
                Double.compare(speed, 0.0D),
                Integer.compare(BanditRevolverRules.BASE_LOAD_TICKS, load),
                Integer.compare(hits, 1)
        };
        int cellWidth = 44;
        for (int index = 0; index < keys.length; index++) {
            int cellX = x + index * (cellWidth + 2);
            context.fill(cellX, y, cellX + cellWidth, y + 22, CELL);
            context.drawText(textRenderer, Text.translatable("screen.sparkstrength.bandit_workbench.stat." + keys[index]),
                    cellX + 3, y + 2, MUTED, false);
            context.drawText(textRenderer, values[index], cellX + 3, y + 12,
                    verdicts[index] > 0 ? BETTER : verdicts[index] < 0 ? WORSE : TEXT, false);
        }
    }

    private static String seconds(int ticks) {
        String text = String.format(Locale.ROOT, "%.2f", ticks / 20.0D);
        text = text.replaceAll("0+$", "").replaceAll("\\.$", "");
        return text + "s";
    }

    private void drawCylinder(DrawContext context, BanditRevolverState state, int x, int y, int mouseX, int mouseY) {
        context.drawText(textRenderer, state.pending().isEmpty()
                ? Text.translatable("screen.sparkstrength.bandit_workbench.cylinder",
                state.rounds().size(), state.capacity())
                : Text.translatable("screen.sparkstrength.bandit_workbench.cylinder_pending",
                state.rounds().size(), state.capacity(), state.pending().size()), x, y, MUTED, false);
        int chambered = state.rounds().size();
        for (int index = 0; index < state.capacity(); index++) {
            int cellX = x + index * 14;
            int cellY = y + 12;
            BanditAmmo ammo = state.roundAt(index);
            List<Text> tooltip = new ArrayList<>();
            Runnable action = null;
            if (ammo != null) {
                int chamber = index;
                tooltip.add(Text.translatable("item.sparkstrength." + ammo.key() + "_round").formatted(Formatting.GOLD));
                tooltip.add(Text.translatable(index < chambered
                        ? (index == 0 ? "screen.sparkstrength.bandit_workbench.round_next"
                        : "screen.sparkstrength.bandit_workbench.round_chambered")
                        : "screen.sparkstrength.bandit_workbench.round_pending").formatted(Formatting.GRAY));
                tooltip.add(Text.translatable("screen.sparkstrength.bandit_workbench.click_unload")
                        .formatted(Formatting.DARK_GRAY));
                action = () -> send(BanditRevolverService.Action.UNLOAD, chamber);
            }
            Zone zone = new Zone(cellX, cellY, 12, 12, action, tooltip);
            zones.add(zone);
            context.fill(cellX, cellY, cellX + 12, cellY + 12,
                    ammo != null && zone.contains(mouseX, mouseY) ? CELL_HOVER : CELL);
            if (ammo == null) {
                continue;
            }
            int color = ammo == BanditAmmo.PS12B ? COLOR_PS12B : COLOR_PS12;
            if (index < chambered) {
                context.fill(cellX + 3, cellY + 3, cellX + 9, cellY + 9, color);
            } else {
                int dim = (color & 0x00FFFFFF) | 0x99000000;
                context.fill(cellX + 3, cellY + 3, cellX + 9, cellY + 4, dim);
                context.fill(cellX + 3, cellY + 8, cellX + 9, cellY + 9, dim);
                context.fill(cellX + 3, cellY + 4, cellX + 4, cellY + 8, dim);
                context.fill(cellX + 8, cellY + 4, cellX + 9, cellY + 8, dim);
            }
            if (index == 0 && chambered > 0) {
                context.fill(cellX + 1, cellY + 13, cellX + 11, cellY + 14, TEXT);
            }
        }
    }

    /** Parts and rounds lying in the inventory: click to fit or push one. / 物品栏里的配件与子弹：点击安装或塞入一发。 */
    private void drawSupplies(DrawContext context, BanditRevolverState state, PlayerInventory inventory, int x, int y,
                              int mouseX, int mouseY) {
        context.drawText(textRenderer, Text.translatable("screen.sparkstrength.bandit_workbench.supplies"),
                x, y, MUTED, false);
        int column = 0;
        for (int slot = 0; slot < inventory.size() && column < 5; slot++) {
            ItemStack stack = inventory.getStack(slot);
            List<Text> tooltip = new ArrayList<>();
            Runnable action;
            if (stack.getItem() instanceof BanditAttachmentItem partItem) {
                BanditAttachment part = partItem.attachment();
                tooltip.add(stack.getName().copy().formatted(Formatting.GOLD));
                tooltip.add(Text.translatable("item.sparkstrength.bandit_" + part.key() + ".description")
                        .formatted(Formatting.GRAY));
                tooltip.add(Text.translatable("screen.sparkstrength.bandit_workbench.click_fit")
                        .formatted(Formatting.DARK_GRAY));
                action = () -> send(BanditRevolverService.Action.INSTALL, part.code());
            } else if (stack.getItem() instanceof BanditAmmoItem ammoItem) {
                BanditAmmo ammo = ammoItem.ammo();
                tooltip.add(stack.getName().copy().formatted(Formatting.GOLD));
                tooltip.add(Text.translatable("item.sparkstrength." + ammo.key() + "_round.description")
                        .formatted(Formatting.GRAY));
                tooltip.add(Text.translatable("screen.sparkstrength.bandit_workbench.click_load")
                        .formatted(Formatting.DARK_GRAY));
                action = () -> {
                    if (hasShiftDown()) {
                        send(BanditRevolverService.Action.LOAD_ALL, 0);
                    } else {
                        send(BanditRevolverService.Action.LOAD, ammo.code());
                    }
                };
            } else {
                continue;
            }
            int cellX = x + column * 21;
            int cellY = y + 11;
            Zone zone = new Zone(cellX, cellY, 18, 18, action, tooltip);
            zones.add(zone);
            context.fill(cellX, cellY, cellX + 18, cellY + 18, zone.contains(mouseX, mouseY) ? CELL_HOVER : CELL);
            context.drawItem(stack, cellX + 1, cellY + 1);
            context.drawItemInSlot(textRenderer, stack, cellX + 1, cellY + 1);
            column++;
        }
        if (column == 0) {
            context.drawText(textRenderer, Text.translatable("screen.sparkstrength.bandit_workbench.supplies_none"),
                    x, y + 16, MUTED, false);
        }
    }

    private void send(BanditRevolverService.Action action, int arg) {
        ClientPlayNetworking.send(new BanditRevolverActionC2SPayload(action, arg));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (Zone zone : zones) {
                if (zone.action() != null && zone.contains(mouseX, mouseY)) {
                    zone.action().run();
                    if (client != null) {
                        client.getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                    }
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (client != null && client.options.inventoryKey.matchesKey(keyCode, scanCode)) {
            close();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
