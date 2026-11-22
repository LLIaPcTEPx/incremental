package ru.incremental.tas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.server.ServerTickRateManager;

/**
 * Freezes the integrated server so the world can be inspected and stepped one tick at a time.
 *
 * <p>The freeze is done through the vanilla tick rate manager, which is exactly what
 * {@code /tick freeze} does: the server keeps running and answering packets (so the client never
 * times out) but no game logic advances. The client mirrors the state through
 * {@code ClientboundTickingStatePacket}, so it stops rendering movement as well. One tick is run
 * with {@code stepGameIfPaused(1)}.</p>
 */
public final class TasFreeze {
	private static boolean stepPending;
	private static long stepStartedAt;

	private TasFreeze() {
	}

	private static ServerTickRateManager manager() {
		Minecraft client = Minecraft.getInstance();
		IntegratedServer server = client == null ? null : client.getSingleplayerServer();
		return server == null ? null : server.tickRateManager();
	}

	public static boolean isAvailable() {
		return manager() != null;
	}

	public static boolean isFrozen() {
		ServerTickRateManager manager = manager();
		return manager != null && manager.isFrozen();
	}

	/** Freezes or unfreezes the world. No-op in multiplayer. */
	public static void setFrozen(boolean frozen) {
		ServerTickRateManager manager = manager();
		if (manager == null) {
			return;
		}
		if (frozen) {
			if (!manager.isFrozen()) {
				manager.setFrozen(true);
			}
			return;
		}
		stepPending = false;
		if (manager.stopStepping()) {
			// The step flag is cleared, the freeze itself follows below.
		}
		if (manager.isFrozen()) {
			manager.setFrozen(false);
		}
	}

	public static void unfreeze() {
		setFrozen(false);
	}

	/**
	 * Runs exactly one more tick while frozen.
	 *
	 * @return true when the tick has been requested and is still in flight, false when the world is
	 *         not frozen or the previous step is still pending
	 */
	public static boolean requestStep() {
		ServerTickRateManager manager = manager();
		if (manager == null) {
			return false;
		}
		if (!manager.isFrozen()) {
			manager.setFrozen(true);
		}
		if (stepPending) {
			return false;
		}
		if (!manager.stepGameIfPaused(1)) {
			return false;
		}
		stepPending = true;
		stepStartedAt = System.nanoTime();
		return true;
	}

	/** True once the requested tick has been consumed by the server. */
	public static boolean consumeStepCompleted() {
		if (!stepPending) {
			return false;
		}
		ServerTickRateManager manager = manager();
		if (manager == null) {
			stepPending = false;
			return false;
		}
		// The server clears frozenTicksToRun after running the tick; a safety timeout makes sure a
		// lost step can never lock the UI.
		boolean ran = manager.frozenTicksToRun() <= 0;
		boolean timedOut = System.nanoTime() - stepStartedAt > 1_000_000_000L;
		if (ran || timedOut) {
			stepPending = false;
			return true;
		}
		return false;
	}

	public static boolean stepPending() {
		return stepPending;
	}

	public static void reset() {
		stepPending = false;
	}

	/** Restores the live game after the world was frozen by the mod. */
	public static void shutdown() {
		ServerTickRateManager manager = manager();
		if (manager != null) {
			manager.stopStepping();
			if (manager.isFrozen()) {
				manager.setFrozen(false);
			}
		}
		reset();
	}
}
