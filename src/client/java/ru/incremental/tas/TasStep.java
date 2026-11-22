package ru.incremental.tas;

import net.minecraft.client.Minecraft;

/**
 * Frame-by-frame navigation.
 *
 * <p>While playback is paused the world is frozen and a single forward step means: let exactly one
 * server tick run with the recorded frame applied. Stepping backwards moves an inspection cursor and
 * puts the player back at the position and rotation stored in that frame, so scrubbing through a
 * take shows the same places it was recorded at. Block state and entities are not rewound — the world
 * itself cannot be un-ticked — but the player is always where the take says, which is what frame
 * editing needs.</p>
 *
 * <p><b>Rewinding during a take.</b> Stepping back while recording pauses the take, freezes the
 * world and pins the write position to the playhead. From there the cursor can be moved freely in
 * both directions and shown poses can be inspected. Stepping forward to the frame where the take was
 * paused (or starting the recording again with the record hotkey) resumes it; everything that was
 * already recorded is kept, so a rewind never deletes the rest of the run.</p>
 */
public final class TasStep {
	private static RecordedFrame armedFrame;
	private static boolean armedFramePending;
	private static boolean awaitingTick;
	/**
	 * Playhead the take was at when the user rewound into it. This is where the cursor has to get
	 * back to before recording continues.
	 */
	private static int rewindReturnTick = -1;

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
		resetRewind();
	}

	/** Forgets the rewind bookkeeping (the session flag is cleared separately). */
	public static void resetRewind() {
		rewindReturnTick = -1;
		TasSession.setRewindPaused(false);
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
			if (TasSession.isRewindPaused()) {
				// The stepped frame is the new write position.
				TasSession.setRecordCursor(TasSession.playhead());
			}
			TasSession.flash("incremental.msg.frame", TasSession.playhead(), recording.size());
		}
	}

	// ------------------------------------------------------------ rewind state

	/**
	 * True when the take is paused because the user rewound into it. Reading it through TasSession
	 * keeps the flag in one place, this helper only documents where it comes from.
	 */
	public static boolean rewindPaused() {
		return TasSession.isRewindPaused();
	}

	/** Called when a take starts (record hotkey or the record button). */
	static void beginRecording(int writePosition) {
		rewindReturnTick = -1;
		TasSession.setRewindPaused(false);
		TasSession.beginRecording(writePosition);
	}

	/**
	 * Ends the rewind-paused state: the take continues writing at the given position.
	 *
	 * @param writePosition frame the next captured tick is written to
	 */
	private static void resumeTake(int writePosition) {
		rewindReturnTick = -1;
		TasSession.setRewindPaused(false);
		TasSession.setRecordCursor(writePosition);
		TasSession.setState(TasSession.State.RECORDING);
		TasFreeze.setFrozen(false);
		TasSession.flash("incremental.msg.rec_resume");
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
			if (TasSession.isRewindPaused()) {
				stepForwardWhileRewound(recording, frames);
				return;
			}
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

	/**
	 * Moving forward while a rewound take waits for the cursor: the cursor moves first, and the take
	 * resumes on its own once it reaches the position where it was paused.
	 */
	private static void stepForwardWhileRewound(Recording recording, int frames) {
		int target = Math.min(recording.size(), TasSession.playhead() + frames);
		TasSession.setPlayhead(target);
		if (target > 0) {
			previewPose(recording.get(target - 1));
		}
		TasSession.setRecordCursor(target);

		if (rewindReturnTick >= 0 && target >= rewindReturnTick) {
			resumeTake(target);
			return;
		}
		// Still before the pause point: keep the take paused and the world frozen.
		TasFreeze.setFrozen(true);
		TasSession.flash("incremental.msg.frame", target, recording.size());
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
		// Rewinding while a take runs pauses it, like scrubbing a timeline: the world freezes, the
		// write position follows the cursor and recording resumes as soon as the cursor is moved
		// forward to the point where the take stopped.
		boolean firstRewind = !TasSession.isRewindPaused() && TasSession.isRecording();
		if (firstRewind) {
			// `isRecording()` covers both "capturing" and "already paused with C": either way a
			// rewind from now on owns the write position.
			rewindReturnTick = TasSession.recordCursor();
			TasSession.setState(TasSession.State.RECORDING_PAUSED);
			TasSession.setRewindPaused(true);
		}
		moveCursor(recording, -frames, false);
		if (TasSession.isRewindPaused()) {
			TasSession.setRecordCursor(TasSession.playhead());
			TasFreeze.setFrozen(true);
			if (firstRewind) {
				// Only on the first rewind: the take is now paused and the tail is still there.
				TasSession.flash("incremental.msg.rec_rewound", TasSession.playhead(), recording.size());
			}
		}
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

	/** Puts the player at the recorded position and rotation, without running a tick. */
	private static void previewPose(RecordedFrame frame) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || frame == null) {
			return;
		}
		TasTeleport.apply(client, frame);
		TasController.applyCamera(client.player, frame);
		if (frame.hotbar >= 0 && frame.hotbar < 9) {
			client.player.getInventory().setSelectedSlot(frame.hotbar);
		}
	}

	/** Whether the record hotkey should continue a rewound take instead of starting a new one. */
	public static boolean resumeRewoundTake() {
		if (!TasSession.isRewindPaused()) {
			return false;
		}
		resumeTake(TasSession.playhead());
		return true;
	}
}
