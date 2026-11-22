package ru.incremental.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import ru.incremental.tas.IncrementalConfig;
import ru.incremental.tas.TasKey;

/**
 * One row of the key binding list: shows the action, captures the key the user presses and reports
 * conflicts. Implemented as a widget (instead of hand-rolled hit testing) because a screen routes a
 * click to the child widget that is under the cursor, and anything else would be swallowed before
 * the screen ever sees it.
 */
public class BindingRow extends AbstractWidget {
	/** Set while this row waits for a key press. */
	private static BindingRow capturing;

	private final TasConfigScreen screen;
	private final TasKey key;
	private long errorUntil;

	public BindingRow(TasConfigScreen screen, TasKey key, int x, int y, int width, int height) {
		super(x, y, width, height, Component.translatable(key.translationKey));
		this.screen = screen;
		this.key = key;
	}

	public TasKey key() {
		return key;
	}

	public boolean isCapturing() {
		return capturing == this;
	}

	public static void cancelCapture() {
		capturing = null;
	}

	private void startCapture() {
		capturing = this;
		this.errorUntil = 0L;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		// The row background is drawn by the screen; only the right-hand binding box is drawn here.
		int boxWidth = 112;
		int boxX = getX() + getWidth() - boxWidth;
		boolean active = isCapturing();
		boolean error = System.currentTimeMillis() < errorUntil;
		graphics.fill(boxX, getY() + 1, boxX + boxWidth, getY() + 21, active ? 0xFF4A3F1F : 0xFF232833);
		Component label = active
				? Component.translatable("incremental.msg.press_key").withStyle(net.minecraft.ChatFormatting.YELLOW)
				: error ? Component.translatable("incremental.msg.key_taken").withStyle(net.minecraft.ChatFormatting.RED)
				: TasKey.describe(key);
		graphics.drawCenteredString(Minecraft.getInstance().font, label, boxX + boxWidth / 2, getY() + 7,
				active ? 0xFFFFD166 : error ? 0xFFFF6B6B : 0xFFD6DBE5);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		startCapture();
	}

	/**
	 * A click while capturing becomes a mouse binding. The check sits in this override instead of
	 * {@link #onClick} because the base class only passes the primary button through to the handler.
	 */
	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (isCapturing()) {
			if (event.button() >= 0 && event.button() <= 7) {
				assign(-100 - event.button());
			} else {
				cancelCapture();
			}
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!isCapturing()) {
			return false;
		}
		if (event.isEscape()) {
			cancelCapture();
			return true;
		}
		assign(event.key());
		return true;
	}

	private void assign(int glfwCode) {
		capturing = null;
		if (glfwCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
			return;
		}
		if (IncrementalConfig.keyUsedBy(key, glfwCode)) {
			errorUntil = System.currentTimeMillis() + 2500L;
			return;
		}
		IncrementalConfig.setKey(key, glfwCode);
		screen.rebindRow(this);
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}
}
