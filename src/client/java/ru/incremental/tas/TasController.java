package ru.incremental.tas;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.util.Mth;

/**
 * Drives recording and playback.
 *
 * <p>Both run inside the client tick, in a fixed order:</p>
 * <ol>
 *   <li>{@link #onClientTickStart()} runs at the head of {@code Minecraft.tick}. During playback the
 *       next recorded frame is written here, before vanilla reads the key bindings, so the attack /
 *       use clicks of the frame are consumed by vanilla in the very same tick.</li>
 *   <li>vanilla ticks the player, the world and the screen;</li>
 *   <li>{@link #onPlayerTickEnd()} runs at the tail of the player tick and samples the input of that
 *       tick into the recording.</li>
 * </ol>
 */
public final class TasController {
	private TasController() {
	}

	/** Tick rates used by the speed keys, in ticks per second (20 = normal). */
	private static final double[] SPEEDS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 25, 30, 40, 60, 80, 100, 150, 200, 300, 400};

	// ------------------------------------------------------------ recording

	public static void startRecording() {
		if (!TasSession.inSingleplayer()) {
			TasSession.flash("incremental.msg.singleplayer");
			return;
		}
		if (TasSession.isRecording()) {
			// A take that was paused by rewinding continues from the cursor, which is what the
			// record key means there; a take that is merely paused keeps its old meaning.
			if (!TasStep.resumeRewoundTake()) {
				TasSession.flash("incremental.msg.recording");
			}
			return;
		}
		if (TasSession.isPlaying()) {
			TasSession.flash("incremental.msg.playing");
			return;
		}
		Recording recording = TasSession.selected();
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		// The take starts at the playhead and overwrites frames in place. Nothing is deleted: the
		// frames before the playhead are untouched and the frames after it stay in the recording
		// until the new take grows long enough to reach them.
		TasPlaybackInput.uninstall(Minecraft.getInstance());
		TasStep.beginRecording(Math.min(TasSession.playhead(), recording.size()));
		// Capture the blocks this take changes. The states loaded from disk are the ones the take
		// already knows about; new changes are appended as the run goes.
		TasBlocks.load(recording.blocks);
		TasBlocks.setCapturing(true);
		TasFreeze.setFrozen(false);
		if (client().mouseHandler != null && client().screen == null && !client().mouseHandler.isMouseGrabbed()) {
			client().mouseHandler.grabMouse();
		}
		// The take is anchored where the player stands right now; say where that is, so the coordinate
		// capture is visible and not just implied.
		LocalPlayer player = client().player;
		String where = player == null ? ""
				: String.format(java.util.Locale.ROOT, "  @  %.1f / %.1f / %.1f", player.getX(), player.getY(), player.getZ());
		int kept = TasSession.framesAfterCursor();
		if (kept > 0) {
			// Say it out loud: the tail is kept, not deleted, which is what "continue recording after
			// a rewind" means.
			TasSession.flash("incremental.msg.rec_start_kept", Component.literal(recording.name + where), kept);
		} else {
			TasSession.flash("incremental.msg.rec_start", Component.literal(recording.name + where));
		}
	}

	public static void stopRecording() {
		if (!TasSession.isRecording()) {
			return;
		}
		boolean wasRewound = TasSession.isRewindPaused();
		Recording recording = TasSession.selected();
		int ticks = recording == null ? 0 : recording.size();
		TasSession.stopRecording();
		TasBlocks.setCapturing(false);
		TasStep.resetRewind();
		if (wasRewound) {
			// The rewind-paused take froze the world; stopping it must give the game back.
			TasFreeze.unfreeze();
		}
		int blocks = 0;
		if (recording != null) {
			// Store the captured block states with the take so a replay can restore them.
			recording.blocks.clear();
			recording.blocks.putAll(TasBlocks.snapshot());
			blocks = recording.blocks.size();
			RecordingStorage.save(recording);
		}
		TasBlocks.clear();
		if (blocks > 0) {
			TasSession.flash("incremental.msg.rec_stop_blocks", ticks, blocks);
		} else {
			TasSession.flash("incremental.msg.rec_stop", ticks);
		}
	}

	public static void toggleRecordingPause() {
		switch (TasSession.state()) {
			case RECORDING -> {
				TasSession.setState(TasSession.State.RECORDING_PAUSED);
				TasSession.flash("incremental.msg.rec_pause");
			}
			case RECORDING_PAUSED -> {
				if (TasStep.resumeRewoundTake()) {
					// A take paused by rewinding continues from the cursor, not from where it
					// stopped, and unfreezes the world.
					TasFreeze.setFrozen(false);
					return;
				}
				TasSession.setState(TasSession.State.RECORDING);
				TasSession.flash("incremental.msg.rec_resume");
			}
			default -> {
			}
		}
	}

	// ------------------------------------------------------------ playback

	public static void startPlayback() {
		startPlayback(0);
	}

	public static void startPlayback(int fromTick) {
		if (!TasSession.inSingleplayer()) {
			TasSession.flash("incremental.msg.singleplayer");
			return;
		}
		if (TasSession.isRecording()) {
			// A take that was paused by rewinding continues from the cursor, which is what the
			// record key means there; a take that is merely paused keeps its old meaning.
			if (!TasStep.resumeRewoundTake()) {
				TasSession.flash("incremental.msg.recording");
			}
			return;
		}
		Recording recording = TasSession.selected();
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		if (recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		int tick = Mth.clamp(fromTick, 0, recording.size() - 1);
		TasSession.select(recording);
		// The replay has to start from the blocks the take was recorded on: remove what the take
		// placed and restore what it broke. The run itself then re-creates those changes tick by
		// tick, which is the familiar "world is cleaned, then the run plays it back" flow.
		// Starting from the middle of a take is a different story: the world is not rewound to the
		// pre-run state there, because the blocks of the skipped part would all be wrong. The
		// player is teleported to the frame instead and the replay continues from the current world.
		boolean restored = tick == 0 && restoreBlocksFor(recording);
		TasSession.beginPlayback(tick);
		TasPlaybackInput.install(Minecraft.getInstance());
		if (Minecraft.getInstance().screen instanceof ru.incremental.screen.TasScreen) {
			// A pause screen would stop the level from ticking; playback needs the world running.
			Minecraft.getInstance().setScreen(null);
		}
		applySpeed();
		Minecraft client = Minecraft.getInstance();
		if (client.screen == null && !client.mouseHandler.isMouseGrabbed()) {
			client.mouseHandler.grabMouse();
		}
		RecordedFrame first = recording.get(tick);
		String where = first.hasPosition ? "  @  " + first.positionText() : "";
		if (restored) {
			// Say how many blocks were reset: the cleanup is visible in the world, this explains it.
			TasSession.flash("incremental.msg.play_start_blocks",
					Component.literal(recording.name + where), recording.blocks.size());
		} else {
			TasSession.flash("incremental.msg.play_start", Component.literal(recording.name + where));
		}
	}

	/**
	 * Loads this recording's block list and writes the original states back into the world.
	 *
	 * @return true when the replay actually changed something, so the caller can tell the user
	 */
	private static boolean restoreBlocksFor(Recording recording) {
		if (recording.blocks.isEmpty()) {
			TasBlocks.clear();
			return false;
		}
		TasBlocks.load(recording.blocks);
		int changed = TasBlocks.restoreOriginals();
		return changed > 0;
	}

	/** Starts playback already paused so the recording can be inspected frame by frame. */
	public static void startPlaybackPaused(int fromTick) {
		startPlayback(fromTick);
		if (TasSession.isPlayingActive()) {
			pausePlayback();
		}
	}

	public static void togglePlaybackPause() {
		switch (TasSession.state()) {
			case PLAYING -> pausePlayback();
			case PLAY_PAUSED -> resumePlayback();
			default -> {
			}
		}
	}

	private static void pausePlayback() {
		TasSession.setState(TasSession.State.PLAY_PAUSED);
		TasStep.clearPlaybackFrame();
		TasSession.flash("incremental.msg.play_pause");
	}

	private static void resumePlayback() {
		TasSession.setState(TasSession.State.PLAYING);
		TasFreeze.unfreeze();
		applySpeed();
		TasSession.flash("incremental.msg.play_resume");
	}

	public static void stopPlayback() {
		if (!TasSession.isPlaying()) {
			return;
		}
		TasSession.stopPlayback();
		TasPlaybackInput.uninstall(Minecraft.getInstance());
		TasTeleport.reset();
		TasFreeze.unfreeze();
		restoreTickRate();
		clientTickTargetMillis = 0.0F;
		// The take is over: take its blocks back out of the world, so nothing it built is left behind.
		int cleaned = TasBlocks.cleanup();
		if (cleaned > 0) {
			TasSession.flash("incremental.msg.play_stop_blocks", cleaned);
		} else {
			TasSession.flash("incremental.msg.play_stop");
		}
	}

	/** The single "pause" action: pauses whichever activity is running. */
	public static void togglePause() {
		if (TasSession.isRecording()) {
			toggleRecordingPause();
		} else if (TasSession.isPlaying()) {
			togglePlaybackPause();
		}
	}

	public static void playOrStop() {
		if (TasSession.isPlaying()) {
			stopPlayback();
		} else {
			startPlayback();
		}
	}

	// ------------------------------------------------------------ save

	public static void saveSelected() {
		Recording recording = TasSession.selected();
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		if (TasSession.isRecording()) {
			stopRecording();
		}
		if (RecordingStorage.save(recording)) {
			TasSession.flash("incremental.msg.saved", Component.literal(recording.name));
		}
	}

	// ------------------------------------------------------------ speed

	public static void speedUp() {
		changeSpeed(1);
	}

	public static void speedDown() {
		changeSpeed(-1);
	}

	private static void changeSpeed(int direction) {
		int index = nearestSpeedIndex(IncrementalConfig.tickRate);
		int next = Mth.clamp(index + direction, 0, SPEEDS.length - 1);
		if (next == index) {
			TasSession.flash("incremental.msg.speed", speedLabel(IncrementalConfig.tickRate));
			return;
		}
		IncrementalConfig.tickRate = SPEEDS[next];
		IncrementalConfig.save();
		applySpeed();
		TasSession.flash("incremental.msg.speed", speedLabel(IncrementalConfig.tickRate));
	}

	public static void cycleSpeed() {
		changeSpeed(1);
	}

	private static int nearestSpeedIndex(double rate) {
		int best = 0;
		double bestDistance = Double.MAX_VALUE;
		for (int i = 0; i < SPEEDS.length; i++) {
			double distance = Math.abs(SPEEDS[i] - rate);
			if (distance < bestDistance) {
				bestDistance = distance;
				best = i;
			}
		}
		return best;
	}

	public static Component speedLabel(double rate) {
		double multiplier = rate / 20.0D;
		String text = Math.abs(multiplier - Math.rint(multiplier)) < 1.0E-6
				? String.valueOf((int) Math.rint(multiplier))
				: String.format(java.util.Locale.ROOT, "%.2f", multiplier);
		return Component.literal(text + "x (" + String.format(java.util.Locale.ROOT, "%.0f", rate) + " t/s)");
	}

	/** Pushes the config bindings into the vanilla key mappings (after a rebind or a reset). */
	public static void syncKeyMappings() {
		IncrementalConfig.syncAllMappings();
	}

	/** Applies the configured speed to the live server while a recording is playing. */
	public static void applySpeed() {
		ServerTickRateManager manager = manager();
		if (manager == null) {
			return;
		}
		if (!speedApplied()) {
			return;
		}
		float rate = (float) IncrementalConfig.clampRate(IncrementalConfig.tickRate);
		if (Math.abs(manager.tickrate() - rate) > 1.0E-4) {
			manager.setTickRate(rate);
		}
		// The client derives its own tick target from the same manager, but never below 50 ms, so
		// speeds above 20 t/s need the client side asked explicitly (see MinecraftMixin).
		clientTickTargetMillis = 1000.0F / rate;
	}

	public static void restoreTickRate() {
		ServerTickRateManager manager = manager();
		if (manager != null && Math.abs(manager.tickrate() - 20.0F) > 1.0E-4) {
			manager.setTickRate(20.0F);
		}
	}

	/**
	 * Tick length the client has to run at while a take runs at a non-default speed, or 0 when the
	 * vanilla value applies. The integrated server and the client must tick at the same rate,
	 * otherwise the server rubber-bands the client-driven player — this is true while playing back
	 * and, just as much, while recording at a speed other than 20 t/s.
	 */
	private static float clientTickTargetMillis;

	public static float clientTickTargetMillis() {
		return speedApplied() ? clientTickTargetMillis : 0.0F;
	}

	/** True while the configured speed is being forced onto the world. */
	private static boolean speedApplied() {
		return TasSession.isPlayingActive() || TasSession.isRecordingActive();
	}

	private static Minecraft client() {
		return Minecraft.getInstance();
	}

	private static ServerTickRateManager manager() {
		Minecraft client = Minecraft.getInstance();
		return client.getSingleplayerServer() == null ? null : client.getSingleplayerServer().tickRateManager();
	}

	// ------------------------------------------------------------ per tick

	/** Called at the head of {@code Minecraft.tick}. */
	public static void onClientTickStart() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		// A step that was requested last tick has run by now; advance the playhead.
		TasStep.tick();
		if (TasSession.isPlayingActive()) {
			playNextFrame(client);
			return;
		}
		if (!TasSession.isPlaying()) {
			return;
		}
		// Playback paused: write the armed frame into the game, so this tick consumes it and the
		// server receives the matching input packet.
		RecordedFrame armed = TasStep.armedFrame();
		if (armed != null) {
			applyFrame(client, armed, true);
			TasStep.clearArmedFrame();
		}
	}

	/** Called at the tail of {@code Minecraft.tick}. */
	public static void onClientTickEnd() {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		// Now the input packet of the armed frame has reached the server: let it run one tick.
		TasStep.requestQueuedStep();
	}

	/**
	 * Called at the tail of {@code LocalPlayer.tick}, after vanilla consumed this tick's input.
	 */
	public static void onPlayerTickEnd() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		if (TasSession.isRecordingActive()) {
			captureFrame(client.player);
		}
	}

	private static void captureFrame(LocalPlayer player) {
		Recording recording = TasSession.selected();
		if (recording == null) {
			return;
		}
		RecordedFrame frame = new RecordedFrame(player.input.keyPresses);
		frame.attack = Minecraft.getInstance().options.keyAttack.isDown();
		frame.use = Minecraft.getInstance().options.keyUse.isDown();
		frame.hotbar = player.getInventory().getSelectedSlot();
		frame.yaw = player.getYRot();
		frame.pitch = player.getXRot();
		frame.x = player.getX();
		frame.y = player.getY();
		frame.z = player.getZ();
		frame.motionX = player.getDeltaMovement().x;
		frame.motionY = player.getDeltaMovement().y;
		frame.motionZ = player.getDeltaMovement().z;
		frame.dimension = player.level().dimension().identifier().toString();
		frame.hasPosition = true;
		recording.put(TasSession.recordCursor(), frame);
		TasSession.advanceRecordCursor();
	}

	private static void playNextFrame(Minecraft client) {
		Recording recording = TasSession.selected();
		if (recording == null) {
			stopPlayback();
			return;
		}
		int tick = TasSession.playhead();
		if (tick >= recording.size()) {
			finishPlayback(recording);
			return;
		}
		if (tick == 0) {
			// First frame of the take: put the player exactly where the run started, otherwise the
			// first tick would be replayed from wherever the user happened to stand.
			TasTeleport.apply(client, recording.get(tick), true);
		}
		applyFrame(client, recording.get(tick), true);
		TasSession.setPlayhead(tick + 1);
	}

	private static void finishPlayback(Recording recording) {
		stopPlayback();
		TasSession.flash("incremental.msg.play_finish", Component.literal(recording.name));
	}

	/**
	 * Writes one frame into the game: camera and hotbar immediately, movement through the playback
	 * input, and the recorded mouse buttons as fresh key-mapping clicks.
	 *
	 * @param clickButtons true when the frame is being played forward and the buttons must produce
	 *                     an attack / use action in this tick; false when only previewing a pose
	 */
	public static void applyFrame(Minecraft client, RecordedFrame frame, boolean clickButtons) {
		if (client.player == null || frame == null) {
			return;
		}
		// Position first: everything else (camera, clicks) is relative to where the player stands.
		TasTeleport.apply(client, frame);
		applyCamera(client.player, frame);
		if (frame.hotbar >= 0 && frame.hotbar < 9 && client.player.getInventory().getSelectedSlot() != frame.hotbar) {
			client.player.getInventory().setSelectedSlot(frame.hotbar);
		}
		TasPlaybackInput playback = TasPlaybackInput.install(client);
		if (playback != null) {
			playback.setFrame(frame);
		}
		if (clickButtons) {
			pressButtons(client, frame);
		}
	}

	/**
	 * Sets the recorded button state so vanilla's own key bind handling reacts to it. A rising edge
	 * queues one click, which vanilla consumes in this very tick — that is what produces exactly one
	 * attack or one item use per recorded press, no matter how the player bound the buttons.
	 */
	public static void pressButtons(Minecraft client, RecordedFrame frame) {
		setDown(client.options.keyAttack, frame.attack);
		setDown(client.options.keyUse, frame.use);
	}

	/** Removes clicks the physical mouse queued while playback drives the player. */
	public static void drainPhysicalClicks(Minecraft client) {
		while (client.options.keyAttack.consumeClick()) {
			// Playback owns the buttons.
		}
		while (client.options.keyUse.consumeClick()) {
			// Playback owns the buttons.
		}
	}

	private static void setDown(KeyMapping mapping, boolean down) {
		if (mapping == null) {
			return;
		}
		boolean wasDown = mapping.isDown();
		mapping.setDown(down);
		if (down && !wasDown) {
			InputConstants.Key key = ((ru.incremental.mixin.KeyMappingAccessor) mapping).incremental$getKey();
			KeyMapping.click(key);
		}
		if (!down) {
			while (mapping.consumeClick()) {
				// A released button must never fire later.
			}
		}
	}

	/** Places the player exactly where the recording says the camera was. */
	public static void applyCamera(LocalPlayer player, RecordedFrame frame) {
		if (!IncrementalConfig.recordCamera) {
			return;
		}
		float yaw = frame.yaw;
		float pitch = Mth.clamp(frame.pitch, -90.0F, 90.0F);
		if (player.getYRot() == yaw && player.getXRot() == pitch) {
			return;
		}
		// yRotO/xRotO hold the angle of the *previous* tick: the renderer lerps between them and the
		// current rotation with the frame's partial tick, which is what makes a turn look continuous.
		// Setting them to the new angle (as an earlier version did) removed that interpolation and
		// made the camera snap once per tick — the "choppy playback" the replay used to have.
		player.yRotO = player.getYRot();
		player.xRotO = player.getXRot();
		player.setYRot(yaw);
		player.setXRot(pitch);
		// The body follows the head, otherwise the shoulders lag behind a fast recorded turn.
		player.setYHeadRot(yaw);
		player.setYBodyRot(yaw);
	}

	// ------------------------------------------------------------ screens & frames

	/**
	 * Keeps the freeze in sync with the open screen: TAS screens freeze the world (when the option is
	 * on), leaving them unfreezes it again. Playback state always wins.
	 */
	public static void onScreenChanged(Minecraft client, net.minecraft.client.gui.screens.Screen screen) {
		if (client.level == null || client.getSingleplayerServer() == null) {
			return;
		}
		if (TasSession.isPlaying()) {
			if (TasSession.state() == TasSession.State.PLAY_PAUSED) {
				TasFreeze.setFrozen(true);
			}
			return;
		}
		boolean tasScreen = screen instanceof ru.incremental.screen.TasScreen;
		if (tasScreen && IncrementalConfig.freezeWhileManaging) {
			TasFreeze.setFrozen(true);
		} else if (TasFreeze.isFrozen() && !TasSession.isRecording()) {
			TasFreeze.unfreeze();
		}
	}

	/**
	 * Called once per rendered frame. Rendering also happens when the world is frozen or a menu is
	 * open, which makes this the reliable place for the speed and the freeze bookkeeping.
	 */
	public static void onRenderFrame(Minecraft client) {
		if (speedApplied()) {
			applySpeed();
			if (TasSession.isPlayingActive() && TasFreeze.isFrozen()) {
				TasFreeze.unfreeze();
			}
			if (TasSession.isPlayingActive()) {
				drainPhysicalClicks(client);
			}
			return;
		}
		if (TasSession.state() == TasSession.State.PLAY_PAUSED && !TasFreeze.isFrozen()) {
			TasFreeze.setFrozen(true);
		}
		if (!TasSession.isBusy()) {
			restoreTickRateIfNeeded();
		}
	}

	/** Re-reads the "freeze while managing" option while a TAS screen is open. */
	public static void refreshFreeze() {
		Minecraft client = Minecraft.getInstance();
		onScreenChanged(client, client.screen);
	}

	private static void restoreTickRateIfNeeded() {
		ServerTickRateManager manager = manager();
		if (manager != null && Math.abs(manager.tickrate() - 20.0F) > 1.0E-4) {
			manager.setTickRate(20.0F);
		}
	}

	// ------------------------------------------------------------ editing

	public static void nextFrame() {
		TasStep.stepForward(1);
	}

	public static void prevFrame() {
		TasStep.stepBack(1);
	}

	/** R — jump a second back. */
	public static void jumpBack() {
		TasStep.stepBack(20);
	}

	/** T — jump a second forward. */
	public static void jumpForward() {
		TasStep.stepForward(20);
	}

	/** V — insert an empty frame at the playhead. */
	public static void insertFrame() {
		Recording recording = TasSession.selected();
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		int tick = Mth.clamp(TasSession.playhead(), 0, recording.size());
		recording.insert(tick, RecordedFrame.empty());
		TasSession.setPlayhead(tick + 1);
		TasSession.flash("incremental.msg.frame_new");
	}

	/** Deletes the frame at the playhead (used by the frame list in the UI). */
	public static void deleteFrame() {
		Recording recording = TasSession.selected();
		if (recording == null || recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		int tick = Mth.clamp(TasSession.playhead(), 0, recording.size() - 1);
		recording.remove(tick);
		TasSession.setPlayhead(Math.min(tick, recording.size()));
		TasSession.flash("incremental.msg.frame_deleted", tick + 1);
	}
}
