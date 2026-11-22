package ru.incremental.tas;

import net.minecraft.client.Minecraft;

/**
 * Frame-by-frame navigation.
 *
 * <p>While playback is paused the world is frozen, so the player stands still and a single step
 * means: let exactly one server tick run with the recorded frame applied. Stepping backwards (and
 * stepping while not playing at all) only moves an inspection cursor and previews the recorded
 * camera pose, because a Minecraft world cannot be rewound — real rewinding means loading a save,
 * which is exactly the workflow frame stepping is meant to support.</p>
 */
public final class TasStep {
	private static RecordedFrame armedFrame;
	private static boolean armedFramePending;
	private static boolean awaitingTick;

	private TasStep() {
	}

	/** Frame that must be written into the game at the next client tick. */
	static RecordedFrame armedFrame() {
		return armedFrame;
	}

	static void clearArmedFrame() {
		armedFrame = null;
	}

	/** Discards a step in flight, e.g. when playback is paused or stopped by the user. */
	public static void clearPlaybackFrame() {
		armedFrame = null;
		armedFramePending = false;
		awaitingTick = false;
		Minecraft client = Minecraft.getInstance();
		if (client.player != null && client.player.input instanceof TasPlaybackInput) {
			((TasPlaybackInput) client.player.input).clear();
		}
	}

	/**
	 * Runs after the client tick. Requesting the server step only now guarantees that the input
	 * packet of the armed frame has already been handed to the server, so the single tick that is
	 * allowed to run actually sees the recorded input.
	 */
	public static void requestQueuedStep() {
		if (!armedFramePending) {
			return;
		}
		armedFramePending = false;
		awaitingTick = true;
		TasFreeze.requestStep();
	}

	public static void reset() {
		armedFrame = null;
		armedFramePending = false;
		awaitingTick = false;
	}

	/** Called once per client tick to notice when the requested tick has run. */
	public static void tick() {
		if (!awaitingTick) {
			return;
		}
		// consumeStepCompleted() also releases the pending flag, so a step can never get stuck and
		// block every later step.
		if (!TasFreeze.consumeStepCompleted()) {
			return;
		}
		awaitingTick = false;
		Recording recording = TasSession.selected();
		if (recording != null) {
			TasSession.setPlayhead(Math.min(recording.size(), TasSession.playhead() + 1));
			TasSession.flash("incremental.msg.frame", TasSession.playhead(), recording.size());
		}
	}

	// ------------------------------------------------------------ actions

	/** G ("next frame") and the frame button of the edit screen. */
	public static void stepForward(int frames) {
		Recording recording = TasSession.selected();
		if (recording == null || recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		if (!TasSession.isPlaying()) {
			moveCursor(recording, frames, true);
			return;
		}
		if (TasSession.state() != TasSession.State.PLAY_PAUSED) {
			TasController.togglePlaybackPause();
		}
		if (frames != 1) {
			// Big jumps only move the cursor: running a burst of frozen ticks is what "resume"
			// already does, and doing it here would silently run the world.
			moveCursor(recording, frames, false);
			return;
		}
		stepOneTick(recording);
	}

	private static void stepOneTick(Recording recording) {
		int tick = TasSession.playhead();
		if (tick >= recording.size()) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		if (awaitingTick || armedFramePending) {
			return;
		}
		if (!TasFreeze.isAvailable()) {
			TasSession.flash("incremental.msg.singleplayer");
			return;
		}
		TasFreeze.setFrozen(true);
		armedFrame = recording.get(tick);
		armedFramePending = true;
	}

	/** F ("previous frame") and R (rewind). */
	public static void stepBack(int frames) {
		Recording recording = TasSession.selected();
		if (recording == null || recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		if (TasSession.isPlayingActive()) {
			TasController.togglePlaybackPause();
		}
		moveCursor(recording, -frames, false);
	}

	private static void moveCursor(Recording recording, int delta, boolean forward) {
		int tick = Math.max(0, Math.min(recording.size(), TasSession.playhead() + delta));
		TasSession.setPlayhead(tick);
		if (tick > 0) {
			previewPose(recording.get(tick - 1));
		}
		if (forward) {
			TasSession.flash("incremental.msg.frame", tick, recording.size());
		} else {
			TasSession.flash(delta == -1 ? "incremental.msg.frame" : "incremental.msg.rewound", tick, recording.size());
		}
	}

	/** Shows a recorded camera pose without touching the world. */
	private static void previewPose(RecordedFrame frame) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || frame == null) {
			return;
		}
		TasController.applyCamera(client.player, frame);
		if (frame.hotbar >= 0 && frame.hotbar < 9) {
			client.player.getInventory().setSelectedSlot(frame.hotbar);
		}
	}
}
