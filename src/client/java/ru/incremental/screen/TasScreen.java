package ru.incremental.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Shared look and helpers for the mod's screens. They behave like the vanilla pause menu: the world
 * stays paused, and closing them goes back to the screen that opened them.
 */
public abstract class TasScreen extends Screen {
	public static final int PANEL_BG = 0xF0101014;
	public static final int PANEL_BORDER = 0xFF3A3F4B;
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
	protected EditBox nameField;
	protected int panelX;
	protected int panelY;
	protected int panelWidth;
	protected int panelHeight;
	protected int contentTop;
	protected long openedAt;

	protected TasScreen(Component title, Screen parent) {
		super(title);
		this.parent = parent;
	}

	@Override
	protected void init() {
		openedAt = System.currentTimeMillis();
		panelWidth = Math.min(width - 40, 460);
		panelHeight = Math.min(height - 30, 300);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentTop = panelY + 30;
		buildWidgets();
	}

	protected abstract void buildWidgets();

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		this.renderTransparentBackground(graphics);
		graphics.fill(panelX - 1, panelY - 1, panelX + panelWidth + 1, panelY + panelHeight + 1, PANEL_BORDER);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + panelHeight, PANEL_BG);
		graphics.fill(panelX, panelY, panelX + panelWidth, panelY + 24, 0xFF191D25);
		graphics.drawString(font, title, panelX + 10, panelY + 8, HEADER, false);

		super.render(graphics, mouseX, mouseY, delta);
	}

	protected void drawCenteredText(GuiGraphics graphics, Component text, int y, int color) {
		graphics.drawCenteredString(font, text, panelX + panelWidth / 2, y, color);
	}

	protected void drawLabel(GuiGraphics graphics, String text, int x, int y) {
		graphics.drawString(font, Component.literal(text), x, y, MUTED, false);
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

	/** True while the screen is fading in; used to swallow the click that opened it. */
	protected boolean justOpened() {
		return System.currentTimeMillis() - openedAt < 120;
	}

	protected EditBox createNameField(int x, int y, int w, int h, String initial) {
		EditBox box = new EditBox(font, x, y, w, h, Component.translatable("incremental.field.name"));
		box.setMaxLength(64);
		box.setValue(initial == null ? "" : initial);
		box.setHint(Component.translatable("incremental.field.name"));
		return box;
	}
}
