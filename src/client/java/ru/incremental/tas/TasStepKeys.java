package ru.incremental.tas;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * Polls the TAS hotkeys straight from GLFW.
 *
 * <p>They have to work while the world is frozen (menu open, playback paused), where no game tick
 * runs, so key mappings alone are not enough. One physical press produces exactly one action;
 * holding a key repeats after a short delay, which is convenient for rewinding.</p>
 */
public final class TasStepKeys {
	private static final int REPEAT_DELAY_MS = 350;
	private static final int REPEAT_RATE_MS = 70;
	private static final int MOUSE_ATTACK = 0;
	private static final int MOUSE_USE = 1;

	private static final long[] pressedAt = new long[TasKey.values().length];
	private static final long[] repeatedAt = new long[TasKey.values().length];
	private static boolean prevAttack;
	private static boolean prevUse;

	private TasStepKeys() {
	}

	private static boolean isDown(Minecraft client, TasKey key) {
		int code = IncrementalConfig.getKey(key);
		if (code == GLFW.GLFW_KEY_UNKNOWN || client.getWindow() == null) {
			return false;
		}
		return InputConstants.isKeyDown(client.getWindow(), code);
	}

	/** One-shot press detection with optional auto-repeat (0 disables repeating). */
	public static boolean pollPress(TasKey key, int repeatMs) {
		Minecraft client = Minecraft.getInstance();
		if (!isDown(client, key)) {
			pressedAt[key.ordinal()] = 0L;
			repeatedAt[key.ordinal()] = 0L;
			return false;
		}
		long now = System.currentTimeMillis();
		int index = key.ordinal();
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
		return isDown(Minecraft.getInstance(), key);
	}

	private static boolean mouseDown(Minecraft client, int button) {
		return client.getWindow() != null && GLFW.glfwGetMouseButton(client.getWindow().handle(), button) == GLFW.GLFW_PRESS;
	}

	/**
	 * Frame stepping and live input sampling. Runs once per client tick, and also once per rendered
	 * frame while the world is frozen (nothing ticks then, so this is the only place that can react).
	 */
	public static void poll(Minecraft client) {
		boolean menu = client.screen != null;

		if (pollPress(TasKey.NEXT_FRAME, REPEAT_RATE_MS)) {
			TasStep.stepNextFrame();
		}
		if (pollPress(TasKey.PREV_FRAME, REPEAT_RATE_MS)) {
			TasStep.stepPrevFrame();
		}
		if (pollPress(TasKey.NEW_FRAME, 300)) {
			TasController.insertFrame();
		}
		if (pollPress(TasKey.REWIND, REPEAT_RATE_MS * 4)) {
			TasStep.stepBack(20);
		}
		if (pollPress(TasKey.FORWARD, REPEAT_RATE_MS * 4)) {
			TasStep.stepForward(20);
		}
		if (pollPress(TasKey.SPEED_UP, 150)) {
			TasController.speedUp();
		}
		if (pollPress(TasKey.SPEED_DOWN, 150)) {
			TasController.speedDown();
		}
		if (!menu && TasSession.isPlaying() && pollPress(TasKey.TOGGLE_PAUSE, 0)) {
			TasController.togglePause();
		}

		// Mouse buttons are sampled per tick; GLFW polling also works while the world is frozen.
		if (client.player != null && !menu) {
			boolean attack = mouseDown(client, MOUSE_ATTACK) && client.mouseHandler.isMouseGrabbed();
			boolean use = mouseDown(client, MOUSE_USE) && client.mouseHandler.isMouseGrabbed();
			boolean attackPressed = attack && !prevAttack;
			boolean usePressed = use && !prevUse;
			prevAttack = attack;
			prevUse = use;
			// Forward fresh clicks into vanilla when the TAS froze the world (vanilla skips them then).
			TasController.feedMouseClicks(client, attackPressed, usePressed);
		}
	}

	public static void reset() {
		for (int i = 0; i < pressedAt.length; i++) {
			pressedAt[i] = 0L;
			repeatedAt[i] = 0L;
		}
		prevAttack = false;
		prevUse = false;
	}
}
