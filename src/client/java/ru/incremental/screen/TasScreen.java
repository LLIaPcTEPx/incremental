package ru.incremental.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Shared look and helpers for the mod's screens. They are pause screens, so the world keeps its
 * paused state while they are open, and closing one returns to the screen that opened it.
 */
public abstract class TasScreen extends Screen {
	public static final int PANEL_BG = 0xF0101014;
	public static final int PANEL_BORDER = 0xFF3A3F4B;
	public static final int HEADER_BG = 0xFF191D25;
	public static final int HEADER = 0xFFE8ECF4;
	public static final int TEXT = 0xFFD6DBE5;
	public static final int MUTED = 0xFF8B93A3;
	public static final int ACCENT = 0xFF7ACDF4;
	public static final int ACCENT_SOFT = 0xFF2A6F88;
	public static final int ROW_BG = 0xFF1A1D24;
	public static final int ROW_HOVER = 0xFF232833;
	public static final int ROW_SELECTED = 0xFF25414D;
	public static final int DANGER = 0xFFFF6B6B;

	protected final Screen parent;
	protected int panelX;
	protected int panelY;
	protected int panelWidth;
	protected int panelHeight;
	protected int contentTop;

	/** Rows of a scrollable list that fit into {@code height} at the current GUI scale. */
	protected static int fitRows(int height, int reserved, int rowHeight) {
		return Math.max(2, (height - reserved) / rowHeight);
	}

	protected TasScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected void init() {
		panelWidth = Math.min(width - 40, 470);
		panelHeight = Math.min(height - 30, 300);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentTop = panelY + 30;
		buildWidgets();
	}

	protected abstract void buildWidgets();

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		renderTransparentBackground(graphics);
		graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, PANEL_BORDER);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 24, HEADER_BG);
		graphics.drawString(font, title, panelX + 10, panelY + 8, HEADER, false);
		super.render(graphics, mouseX, mouseY, delta);
	}

	protected void drawCenteredText(GuiGraphics graphics, Component text, int y, int color) {
		graphics.drawCenteredString(font, text, panelX + panelWidth / 2, y, color);
	}

	protected Button button(Component label, int x, int y, int w, int h, Button.OnPress onPress) {
		return Button.builder(label, onPress).bounds(x, y, w, h).build();
	}

	protected Button doneButton(int x, int y, int w) {
		return button(CommonComponents.GUI_DONE, x, y, w, 20, b -> onClose());
	}

	protected Button cancelButton(int x, int y, int w) {
		return button(CommonComponents.GUI_CANCEL, x, y, w, 20, b -> onClose());
	}

	@Override
	public void onClose() {
		Minecraft client = Minecraft.getInstance();
		client.setScreen(parent);
	}
}
