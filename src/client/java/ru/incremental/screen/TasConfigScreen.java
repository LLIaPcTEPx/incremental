package ru.incremental.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import ru.incremental.tas.IncrementalConfig;
import ru.incremental.tas.TasController;
import ru.incremental.tas.TasKey;
import ru.incremental.tas.TasSession;

/**
 * "TAS settings": every hotkey can be rebound here, plus the playback and recording options.
 *
 * <p>Drawing order matters: the panel and the list background are painted first, the widgets (the
 * binding rows with their key boxes) on top. Painting a background after {@code super.render()} would
 * cover the rows, which is exactly the "keys are invisible" bug this screen used to have.</p>
 */
public class TasConfigScreen extends TasScreen {
	private static final int ROW_H = 20;

	private int scroll;
	private int listX;
	private int listY;
	private int listWidth;
	private int listHeight;
	private int listRows = 6;

	public TasConfigScreen(Screen parent) {
		super(Component.translatable("incremental.screen.config.title"), parent);
	}

	@Override
	protected void buildWidgets() {
		panelWidth = Math.min(width - 20, 430);
		panelHeight = Math.min(height - 20, 316);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentTop = panelY + 26;

		listX = panelX + 8;
		listY = contentTop;
		listWidth = panelWidth - 16;

		// Two option rows and the footer below the list.
		listRows = Math.min(TasKey.BINDABLE.size(), fitRows(panelHeight, 26 + 24 + 24 + 4, ROW_H));
		listHeight = listRows * ROW_H;

		int optionsY = listY + listHeight + 4;
		int half = (listWidth - 4) / 2;

		addRenderableWidget(button(speedLabel(), listX, optionsY, half, 20, b -> {
			TasController.cycleSpeed();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.tick_rate.tooltip")));

		addRenderableWidget(button(toggle("incremental.option.record_motion", IncrementalConfig.recordCamera),
				listX + half + 4, optionsY, half, 20, b -> {
			IncrementalConfig.recordCamera = !IncrementalConfig.recordCamera;
			IncrementalConfig.save();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.record_motion.tooltip")));

		int options2Y = optionsY + 24;
		addRenderableWidget(button(toggle("incremental.option.overlay", IncrementalConfig.showOverlay),
				listX, options2Y, half, 20, b -> {
			IncrementalConfig.showOverlay = !IncrementalConfig.showOverlay;
			IncrementalConfig.save();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.overlay.tooltip")));

		addRenderableWidget(button(toggle("incremental.option.freeze", IncrementalConfig.freezeWhileManaging),
				listX + half + 4, options2Y, half, 20, b -> {
			IncrementalConfig.freezeWhileManaging = !IncrementalConfig.freezeWhileManaging;
			IncrementalConfig.save();
			TasController.refreshFreeze();
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("incremental.option.freeze.tooltip")));

		int footerY = panelY + panelHeight - 24;
		addRenderableWidget(button(Component.translatable("incremental.button.defaults"), listX, footerY, 96, 20, b -> {
			IncrementalConfig.resetAllKeys();
			IncrementalConfig.tickRate = 20.0D;
			BindingRow.cancelCapture();
			TasController.syncKeyMappings();
			rebuildWidgets();
		}));
		addRenderableWidget(doneButton(listX + listWidth - 70, footerY, 70));

		// One widget per visible row: the widget itself draws the name and the bound key.
		for (int row = 0; row < listRows; row++) {
			int index = row + scroll;
			if (index >= TasKey.BINDABLE.size()) {
				break;
			}
			TasKey key = TasKey.BINDABLE.get(index);
			addRenderableWidget(new BindingRow(this, key, listX, listY + row * ROW_H, listWidth, ROW_H));
		}
	}

	private Component speedLabel() {
		return Component.translatable("incremental.option.tick_rate")
				.append(": ")
				.append(TasController.speedLabel(IncrementalConfig.tickRate));
	}

	private static Component toggle(String translationKey, boolean value) {
		String state = Component.translatable(value ? "options.on" : "options.off").getString();
		return Component.translatable(translationKey).append(": " + state);
	}

	private int maxScroll() {
		return Math.max(0, TasKey.BINDABLE.size() - listRows);
	}

	/**
	 * Background first, widgets second. {@link TasScreen#render} paints the panel, so only the list
	 * area and the scroll bar are handled here, before the widgets are drawn by {@code super.render}.
	 */
	@Override
	protected void renderPanel(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		graphics.fill(listX - 1, listY - 1, listX + listWidth + 1, listY + listHeight + 1, PANEL_BORDER);
		graphics.fill(listX, listY, listX + listWidth, listY + listHeight, 0xFF101216);

		if (maxScroll() > 0) {
			int barX = listX + listWidth - 3;
			graphics.fill(barX, listY, barX + 2, listY + listHeight, 0xFF20242C);
			float ratio = (float) listRows / TasKey.BINDABLE.size();
			int thumb = Math.max(12, (int) (listHeight * ratio));
			int offset = (int) ((listHeight - thumb) * ((float) scroll / maxScroll()));
			graphics.fill(barX, listY + offset, barX + 2, listY + offset + thumb, ACCENT_SOFT);
		}
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		if (maxScroll() > 0) {
			graphics.drawCenteredString(font, Component.translatable("incremental.msg.scroll_hint"),
					panelX + panelWidth / 2, listY + listHeight + 52, MUTED);
		}
	}

	/** Called by a row after it stored a new binding. */
	void afterRebind() {
		TasController.syncKeyMappings();
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

	@Override
	public void onClose() {
		BindingRow.cancelCapture();
		TasSession.flash("incremental.msg.settings_saved");
		super.onClose();
	}
}
