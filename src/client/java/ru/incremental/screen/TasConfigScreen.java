package ru.incremental.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import ru.incremental.tas.IncrementalConfig;
import ru.incremental.tas.TasController;
import ru.incremental.tas.TasKey;

/**
 * "TAS settings": every hotkey can be rebound here, plus the playback options.
 *
 * <p>Rebinding listens on the screen itself: clicking a binding puts the screen into capture mode,
 * the next key (or mouse button) that is pressed becomes the new binding, {@code Esc} cancels.</p>
 */
public class TasConfigScreen extends TasScreen {
	private static final int ROW_H = 22;
	private static final int LIST_ROWS = 12;

	private TasKey capturing;
	private long errorUntil;
	private int scroll;

	private int listX;
	private int listY;
	private int listWidth;
	private int listHeight;

	public TasConfigScreen(Screen parent) {
		super(Component.translatable("incremental.screen.config.title"), parent);
	}

	@Override
	protected void buildWidgets() {
		panelWidth = Math.min(width - 20, 470);
		panelHeight = Math.min(height - 20, 336);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentTop = panelY + 28;

		listX = panelX + 8;
		listY = contentTop;
		listWidth = panelWidth - 16;
		listHeight = LIST_ROWS * ROW_H;

		int y = listY + listHeight + 4;

		// Option row: speed, camera, overlay, freeze.
		int optionWidth = (listWidth - 3 * 4) / 4;
		addRenderableWidget(button(speedLabel(), listX, y, optionWidth, 20, b -> {
			TasController.cycleSpeed();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.tick_rate.tooltip")));

		addRenderableWidget(button(toggle("incremental.option.record_motion", IncrementalConfig.recordCamera),
				listX + optionWidth + 4, y, optionWidth, 20, b -> {
			IncrementalConfig.recordCamera = !IncrementalConfig.recordCamera;
			IncrementalConfig.save();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.record_motion.tooltip")));

		addRenderableWidget(button(toggle("incremental.option.overlay", IncrementalConfig.showOverlay),
				listX + (optionWidth + 4) * 2, y, optionWidth, 20, b -> {
			IncrementalConfig.showOverlay = !IncrementalConfig.showOverlay;
			IncrementalConfig.save();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.overlay.tooltip")));

		addRenderableWidget(button(toggle("incremental.option.freeze", IncrementalConfig.freezeWhileManaging),
				listX + (optionWidth + 4) * 3, y, optionWidth, 20, b -> {
			IncrementalConfig.freezeWhileManaging = !IncrementalConfig.freezeWhileManaging;
			IncrementalConfig.save();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.freeze.tooltip")));

		// Footer: defaults + done.
		int footerY = panelY + panelHeight - 24;
		addRenderableWidget(button(Component.translatable("incremental.button.defaults"), listX, footerY, 96, 20, b -> {
			IncrementalConfig.resetAllKeys();
			capturing = null;
			TasController.restoreTickRate();
			rebuildWidgets();
		}));
		addRenderableWidget(doneButton(listX + listWidth - 70, footerY, 70));

		// One invisible button per visible row acts as the binding slot.
		for (int row = 0; row < LIST_ROWS; row++) {
			int index = row + scroll;
			if (index >= TasKey.BINDABLE.size()) {
				break;
			}
			TasKey key = TasKey.BINDABLE.get(index);
			int rowY = listY + row * ROW_H;
			addRenderableWidget(button(Component.empty(), listX + listWidth - 116, rowY + 1, 112, 20, b -> {
				capturing = capturing == key ? null : key;
				rebuildWidgets();
			})).setTooltip(Tooltip.create(Component.translatable("incremental.msg.press_key")));
		}
	}

	private Component speedLabel() {
		return TasController.speedLabel(IncrementalConfig.tickRate);
	}

	private static Component toggle(String translationKey, boolean value) {
		String state = Component.translatable(value ? "options.on" : "options.off").getString();
		return Component.translatable(translationKey).append(": " + state);
	}

	private int maxScroll() {
		return Math.max(0, TasKey.BINDABLE.size() - LIST_ROWS);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
		for (int row = 0; row < LIST_ROWS; row++) {
			int index = row + scroll;
			if (index >= TasKey.BINDABLE.size()) {
				break;
			}
			TasKey key = TasKey.BINDABLE.get(index);
			int y = listY + row * ROW_H;
			boolean hovered = mouseX >= listX && mouseX < listX + listWidth && mouseY >= y && mouseY < y + ROW_H - 2;
			graphics.fill(listX, y, listX + listWidth, y + ROW_H - 2, hovered ? ROW_HOVER : ROW_BG);
			graphics.drawString(font, Component.translatable(key.translationKey), listX + 6, y + 7, TEXT, false);

			Component value = capturing == key
					? Component.translatable("incremental.msg.press_key").withStyle(net.minecraft.ChatFormatting.YELLOW)
					: TasKey.describe(key);
			int x = listX + listWidth - 116;
			graphics.fill(x, y + 1, x + 112, y + 21, capturing == key ? 0xFF4A3F1F : 0xFF232833);
			graphics.drawCenteredString(font, value, x + 56, y + 7, capturing == key ? 0xFFFFD166 : TEXT);
		}
		graphics.disableScissor();

		if (maxScroll() > 0) {
			int barX = listX + listWidth - 3;
			graphics.fill(barX, listY, barX + 2, listY + listHeight, 0xFF20242C);
			float ratio = (float) LIST_ROWS / TasKey.BINDABLE.size();
			int thumb = Math.max(12, (int) (listHeight * ratio));
			int offset = (int) ((listHeight - thumb) * ((float) scroll / maxScroll()));
			graphics.fill(barX, listY + offset, barX + 2, listY + offset + thumb, ACCENT_SOFT);
		}

		if (errorUntil > System.currentTimeMillis()) {
			drawCenteredText(graphics, Component.translatable("incremental.msg.key_taken"), panelY + panelHeight - 34, DANGER);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (capturing != null) {
			if (event.button() == 0 || event.button() == 1) {
				assign(-100 - event.button());
				return true;
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (capturing != null) {
			if (event.isEscape()) {
				capturing = null;
				return true;
			}
			assign(event.key());
			return true;
		}
		return super.keyPressed(event);
	}

	private void assign(int glfwCode) {
		if (capturing == null) {
			return;
		}
		if (glfwCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
			capturing = null;
			return;
		}
		if (IncrementalConfig.keyUsedBy(capturing, glfwCode)) {
			capturing = null;
			errorUntil = System.currentTimeMillis() + 2500L;
			return;
		}
		IncrementalConfig.setKey(capturing, glfwCode);
		capturing = null;
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
			if (maxScroll() > 0) {
				scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0, maxScroll());
				rebuildWidgets();
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}
}
