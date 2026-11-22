package ru.incremental.screen;

import java.util.List;

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
 * One row of the key binding list: the action name on the left, the current key on the right.
 *
 * <p>Implemented as a widget instead of hand-rolled hit testing, because a screen hands a click to
 * the child widget under the cursor and swallows it before the screen itself would see it. The row
 * also draws its own background, so the screen must not paint anything over it afterwards.</p>
 */
public class BindingRow extends AbstractWidget {
	/** The row that currently waits for a key press, if any. */
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

	public static boolean anyCapture() {
		return capturing != null;
	}

	/** The row that waits for a key, or null. */
	public static BindingRow capturingRow() {
		return capturing;
	}

	/**
	 * Assigns a key to the capturing row. Called by the screen, so the binding does not depend on
	 * which widget happens to own the focus.
	 */
	static boolean assignToCapturing(int glfwCode) {
		BindingRow row = capturing;
		if (row == null) {
			return false;
		}
		row.assign(glfwCode);
		return true;
	}

	public static void cancelCapture() {
		capturing = null;
	}

	private void startCapture() {
		capturing = this;
		this.errorUntil = 0L;
		// Keys are routed through the focused child, so the row has to hold the focus itself.
		setFocused(true);
	}

	@Override
	public boolean shouldTakeFocusAfterInteraction() {
		return true;
	}

	@Override
	protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		boolean hovered = isHoveredOrFocused();
		boolean active = isCapturing();
		boolean error = System.currentTimeMillis() < errorUntil;

		graphics.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), hovered ? 0xFF232833 : 0xFF1A1D24);
		if (active) {
			// A key press is being captured: mark the whole row, not just the key box.
			graphics.fill(getX(), getY(), getX() + 2, getY() + getHeight(), 0xFFFFD166);
		}

		Minecraft client = Minecraft.getInstance();
		graphics.drawString(client.font, Component.translatable(key.translationKey), getX() + 8, getY() + 7, 0xFFD6DBE5, false);

		// The key box: always visible, so the user can see what every action is bound to.
		int boxWidth = 104;
		int boxX = getX() + getWidth() - boxWidth - 2;
		graphics.fill(boxX, getY() + 1, boxX + boxWidth, getY() + getHeight() - 1, active ? 0xFF4A3F1F : 0xFF2C313C);

		Component label;
		int color;
		if (active) {
			label = Component.translatable("incremental.msg.press_key_short");
			color = 0xFFFFD166;
		} else if (error) {
			label = Component.translatable("incremental.msg.key_taken_short");
			color = 0xFFFF6B6B;
		} else {
			label = TasKey.describe(key);
			color = 0xFFE8ECF4;
		}
		graphics.drawCenteredString(client.font, label, boxX + boxWidth / 2, getY() + 7, color);
	}

	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		startCapture();
	}

	/**
	 * While capturing, a click becomes a mouse binding. The check lives in this override and not in
	 * {@link #onClick} because the base class only forwards the primary button there.
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
		if (event.key() == 1) {
			// Escape also arrives as key 1 on some layouts; treat it as cancel, never as a binding.
			cancelCapture();
			return true;
		}
		assign(event.key());
		return true;
	}

	void assign(int glfwCode) {
		capturing = null;
		if (glfwCode == org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN) {
			return;
		}
		if (IncrementalConfig.keyUsedBy(key, glfwCode)) {
			errorUntil = System.currentTimeMillis() + 2500L;
			return;
		}
		IncrementalConfig.setKey(key, glfwCode);
		screen.afterRebind();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		defaultButtonNarrationText(output);
	}

	/** Every bindable key, for diagnostics shown by the screen footer. */
	public static List<TasKey> bindable() {
		return TasKey.BINDABLE;
	}
}
