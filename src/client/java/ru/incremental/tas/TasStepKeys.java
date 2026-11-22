package ru.incremental.tas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

/**
 * Polls the TAS hotkeys straight from GLFW.
 *
 * <p>They are polled instead of being ordinary key bindings because the hotkeys must work while the
 * world is frozen or a menu is open — exactly the situations where vanilla does not route key
 * presses to key bindings. One physical press produces exactly one action; holding a key repeats
 * after a short delay, which is convenient for walking through frames.</p>
 */
public final class TasStepKeys {
	private static final int REPEAT_DELAY_MS = 350;
	private static final int REPEAT_RATE_MS = 80;

	private static final long[] pressedAt = new long[TasKey.values().length];
	private static final long[] repeatedAt = new long[TasKey.values().length];

	private TasStepKeys() {
	}

	private static boolean down(Minecraft client, int glfwCode) {
		if (glfwCode == GLFW.GLFW_KEY_UNKNOWN || client.getWindow() == null) {
			return false;
		}
		if (glfwCode < 0) {
			// Mouse bindings are stored as -100 - button.
			return GLFW.glfwGetMouseButton(client.getWindow().handle(), -100 - glfwCode) == GLFW.GLFW_PRESS;
		}
		return GLFW.glfwGetKey(client.getWindow().handle(), glfwCode) == GLFW.GLFW_PRESS;
	}

	private static boolean down(Minecraft client, TasKey key) {
		return down(client, IncrementalConfig.getKey(key));
	}

	/** One-shot press detection with optional auto-repeat (0 disables repeating). */
	public static boolean pollPress(TasKey key, int repeatMs) {
		Minecraft client = Minecraft.getInstance();
		int index = key.ordinal();
		if (!down(client, key)) {
			pressedAt[index] = 0L;
			repeatedAt[index] = 0L;
			return false;
		}
		long now = System.currentTimeMillis();
		if (pressedAt[index] == 0L) {
			pressedAt[index] = now;
			repeatedAt[index] = now;
			return true;
		}
		if (repeatMs > 0 && now - pressedAt[index] >= REPEAT_DELAY_MS && now - repeatedAt[index] >= repeatMs) {
			repeatedAt[index] = now;
			return true;
		}
		return false;
	}

	public static boolean held(TasKey key) {
		return down(Minecraft.getInstance(), key);
	}

	/**
	 * Reads the hotkeys and forwards them to the controller. Runs once per client tick and, while the
	 * world is frozen, once per rendered frame as well: nothing ticks then, so this is the only place
	 * that can react.
	 */
	public static void poll(Minecraft client) {
		if (client.player == null || client.level == null) {
			return;
		}
		if (textInputOpen(client)) {
			// The player is typing a recording name — hotkeys must not steal the keys.
			return;
		}

		if (pollPress(TasKey.START_RECORD, 0)) {
			TasController.startRecording();
		}
		if (pollPress(TasKey.STOP_RECORD, 0)) {
			TasController.stopRecording();
		}
		if (pollPress(TasKey.TOGGLE_PAUSE, 0)) {
			TasController.togglePause();
		}
		if (pollPress(TasKey.SPEED_UP, 140)) {
			TasController.speedUp();
		}
		if (pollPress(TasKey.SPEED_DOWN, 140)) {
			TasController.speedDown();
		}
		if (pollPress(TasKey.REWIND, REPEAT_RATE_MS * 3)) {
			TasController.jumpBack();
		}
		if (pollPress(TasKey.FORWARD, REPEAT_RATE_MS * 3)) {
			TasController.jumpForward();
		}
		if (pollPress(TasKey.PREV_FRAME, REPEAT_RATE_MS)) {
			TasController.prevFrame();
		}
		if (pollPress(TasKey.NEXT_FRAME, REPEAT_RATE_MS)) {
			TasController.nextFrame();
		}
		if (pollPress(TasKey.NEW_FRAME, 300)) {
			TasController.insertFrame();
		}
		if (pollPress(TasKey.SAVE, 0)) {
			TasController.saveSelected();
		}
		if (pollPress(TasKey.PLAY, 0)) {
			TasController.playOrStop();
		}
	}

	/** True when a screen with a focused text box is open (recording name, chat, sign, ...). */
	public static boolean textInputOpen(Minecraft client) {
		Screen screen = client.screen;
		return screen != null && screen.getFocused() instanceof EditBox;
	}

	public static void reset() {
		for (int i = 0; i < pressedAt.length; i++) {
			pressedAt[i] = 0L;
			repeatedAt[i] = 0L;
		}
	}
}
