package ru.incremental.tas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.TickRateManager;

/**
 * Frame-perfect tick control for single-player.
 *
 * <p>The integrated server keeps processing packets (so it never thinks the client timed out) but
 * game elements do not advance while a TAS screen is open or while playback is paused. A single
 * frame step asks for exactly one game tick; the client mirror of that tick runs as well because
 * the client world uses the same tick rate manager.</p>
 */
public final class TasFreeze {
	private static boolean stepRequested;
	private static boolean completedThisFrame;

	private TasFreeze() {
	}

	public static void beginRender() {
		completedThisFrame = false;
	}

	public static boolean completedStepThisFrame() {
		return completedThisFrame;
	}

	public static boolean isFrozen() {
		Minecraft client = Minecraft.getInstance();
		return client.level != null && client.level.tickRateManager().isFrozen();
	}

	/** Forces the freeze state; safe to call every tick. */
	public static void setFrozen(boolean frozen) {
		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client == null ? null : client.getSingleplayerServer();
		if (server == null) {
			return;
		}
		ServerTickRateManager manager = server.tickRateManager();
		if (manager.isFrozen() == frozen) {
			return;
		}
		if (frozen) {
			manager.setFrozen(true);
		} else {
			manager.stopStepping();
			manager.setFrozen(false);
			stepRequested = false;
		}
	}

	public static void unfreezeServer() {
		setFrozen(false);
		stepRequested = false;
	}

	/**
	 * Runs exactly one more game tick while frozen. When that tick has been processed by both the
	 * server and the client world, the callback runs once. Safe to call every rendered frame.
	 */
	public static void stepFrame(Runnable onCompleted) {
		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client.getSingleplayerServer();
		if (server == null) {
			return;
		}
		ServerTickRateManager manager = server.tickRateManager();
		if (!manager.isFrozen()) {
			manager.setFrozen(true);
		}
		if (!stepRequested && !completedThisFrame) {
			manager.stepGameIfPaused(1);
			stepRequested = true;
			return;
		}
		TickRateManager clientManager = client.level == null ? null : client.level.tickRateManager();
		boolean clientRan = clientManager != null && clientManager.runsNormally();
		if (stepRequested && clientRan) {
			stepRequested = false;
			completedThisFrame = true;
			if (onCompleted != null) {
				onCompleted.run();
			}
		}
	}

	/** Freezes / unfreezes depending on whether a TAS screen is open. */
	public static void applyScreenState(boolean screenOpen) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || client.getSingleplayerServer() == null) {
			return;
		}
		if (TasSession.isPlaying()) {
			return;
		}
		boolean shouldFreeze = screenOpen && IncrementalConfig.freezeWhileManaging;
		if (shouldFreeze) {
			setFrozen(true);
		} else {
			unfreezeServer();
		}
	}

	public static void reset() {
		stepRequested = false;
		completedThisFrame = false;
	}

	/** Restores the live game after the world was frozen by us. */
	public static void shutdown() {
		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client == null ? null : client.getSingleplayerServer();
		if (server != null) {
			server.tickRateManager().stopStepping();
			server.tickRateManager().setFrozen(false);
		}
		reset();
	}
}
