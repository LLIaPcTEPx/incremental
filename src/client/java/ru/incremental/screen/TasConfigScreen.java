package ru.incremental.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import ru.incremental.tas.IncrementalConfig;
import ru.incremental.tas.TasController;
import ru.incremental.tas.TasKey;

/**
 * "Настройка мода": every hotkey can be rebound here, plus speed and playback options.
 */
public class TasConfigScreen extends TasScreen {
	private static final int ROW_H = 22;

	/** Key binding capture uses a widget so it integrates with the screen focus/input chain. */
	private static TasKey capturing;
	private static long errorUntil;

	private int scroll;
	private int listX;
	private int listY;
	private int listWidth;
	private int listHeight;
	private final Button[] bindingButtons = new Button[TasKey.BINDABLE.size()];

	private static TasKey capturing() {
		return capturing;
	}

	private static void setCapturing(TasKey key) {
		capturing = key;
	}

	public TasConfigScreen(Screen parent) {
		super(Component.translatable("incremental.screen.config.title"), parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	protected void buildWidgets() {
		panelWidth = Math.min(width - 20, 520);
		panelHeight = Math.min(height - 20, 320);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentTop = panelY + 28;

		listX = panelX + 8;
		listY = contentTop;
		listWidth = panelWidth - 16;
		listHeight = panelHeight - 28 - 56;

		int footerY = panelY + panelHeight - 24;

		addRenderableWidget(button(Component.translatable("incremental.button.defaults"), panelX + 8, footerY, 86, 20, b -> {
			IncrementalConfig.resetAllKeys();
			setCapturing(null);
			rebuildWidgets();
		}));

		addRenderableWidget(button(TasController.speedLabel(IncrementalConfig.tickRate), panelX + 100, footerY, 120, 20, b -> {
			double[] rates = {5, 10, 15, 20, 30, 40, 60, 100, 200};
			int index = 0;
			for (int i = 0; i < rates.length; i++) {
				if (Math.abs(rates[i] - IncrementalConfig.tickRate) < Math.abs(rates[index] - IncrementalConfig.tickRate)) {
					index = i;
				}
			}
			IncrementalConfig.tickRate = rates[(index + 1) % rates.length];
			IncrementalConfig.save();
			TasController.applyPlaybackSpeed();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.tick_rate.tooltip")));

		addRenderableWidget(button(toggleLabel("incremental.option.record_motion", IncrementalConfig.recordMotion), panelX + 226, footerY, 142, 20, b -> {
			IncrementalConfig.recordMotion = !IncrementalConfig.recordMotion;
			IncrementalConfig.save();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.record_motion.tooltip")));

		addRenderableWidget(button(toggleLabel("incremental.option.overlay", IncrementalConfig.showOverlay), panelX + 374, footerY, 82, 20, b -> {
			IncrementalConfig.showOverlay = !IncrementalConfig.showOverlay;
			IncrementalConfig.save();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.overlay.tooltip")));

		int visibleRows = visibleRows();
		for (int i = 0; i < TasKey.BINDABLE.size(); i++) {
			int row = i - scroll;
			if (row < 0 || row >= visibleRows) {
				bindingButtons[i] = null;
				continue;
			}
			TasKey key = TasKey.BINDABLE.get(i);
			int y = listY + row * ROW_H;
			TasKey rowKey = key;
			bindingButtons[i] = addRenderableWidget(button(Component.empty(), listX + listWidth - 108, y, 104, 20, b -> setCapturing(capturing() == rowKey ? null : rowKey)));
		}
	}

	private static Component toggleLabel(String translationKey, boolean value) {
		return Component.translatable(translationKey).append(": ").append(Component.translatable(value ? "options.on" : "options.off"));
	}

	private int visibleRows() {
		return Math.max(1, listHeight / ROW_H);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
		int visibleRows = visibleRows();
		for (int i = scroll; i < TasKey.BINDABLE.size() && i < scroll + visibleRows; i++) {
			TasKey key = TasKey.BINDABLE.get(i);
			int y = listY + (i - scroll) * ROW_H;
			boolean hovered = mouseX >= listX && mouseX <= listX + listWidth - 120 && mouseY >= y && mouseY < y + ROW_H;
			graphics.fill(listX, y, listX + listWidth, y + ROW_H - 2, hovered ? ROW_HOVER : ROW_BG);
			graphics.drawString(font, Component.literal(key.defaultValue), listX + 6, y + 7, TEXT, false);

			Component binding = capturing() == key
					? Component.translatable("incremental.msg.press_key")
					: TasKey.describe(key.boundKey());
			Button button = bindingButtons[i];
			if (button != null) {
				button.setMessage(binding);
				button.setX(listX + listWidth - 108);
				button.setY(y);
			}
		}
		graphics.disableScissor();

		if (TasKey.BINDABLE.size() > visibleRows) {
			int barX = listX + listWidth - 3;
			graphics.fill(barX, listY, barX + 2, listY + listHeight, 0xFF20242C);
			float ratio = (float) visibleRows / TasKey.BINDABLE.size();
			int thumb = Math.max(12, (int) (listHeight * ratio));
			int maxScroll = TasKey.BINDABLE.size() - visibleRows;
			int offset = maxScroll == 0 ? 0 : (int) ((listHeight - thumb) * ((float) scroll / maxScroll));
			graphics.fill(barX, listY + offset, barX + 2, listY + offset + thumb, ACCENT_SOFT);
		}

		if (errorUntil > System.currentTimeMillis()) {
			drawCenteredText(graphics, Component.translatable("incremental.msg.key_taken"), panelY + panelHeight - 38, DANGER);
		}
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		TasKey key = capturing();
		if (key != null) {
			if (event.isEscape()) {
				setCapturing(null);
				return true;
			}
			assign(key, event.key());
			setCapturing(null);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		TasKey key = capturing();
		if (key != null) {
			if (justOpened()) {
				return true;
			}
			assign(key, -100 - event.button());
			setCapturing(null);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
			int maxScroll = Math.max(0, TasKey.BINDABLE.size() - visibleRows());
			scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0, maxScroll);
			for (int i = 0; i < TasKey.BINDABLE.size(); i++) {
				Button button = bindingButtons[i];
				if (button == null) {
					continue;
				}
				int row = i - scroll;
				button.visible = row >= 0 && row < visibleRows();
				button.setY(listY + row * ROW_H);
			}
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}



	private void assign(TasKey key, int glfwCode) {
		if (glfwCode == GLFW.GLFW_KEY_UNKNOWN) {
			return;
		}
		if (IncrementalConfig.keyUsedBy(key, glfwCode)) {
			errorUntil = System.currentTimeMillis() + 2000L;
			return;
		}
		IncrementalConfig.setKey(key, glfwCode);
		rebuildWidgets();
	}
}
