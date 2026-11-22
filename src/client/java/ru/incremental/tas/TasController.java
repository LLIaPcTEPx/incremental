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
			TasSession.flash("incremental.msg.recording");
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
		// The take starts at the playhead: rewind to the frame you want to redo, load the matching
		// save and record from there. Everything before that frame is kept, everything the new take
		// covers is overwritten.
		TasPlaybackInput.uninstall(Minecraft.getInstance());
		TasSession.beginRecording(Math.min(TasSession.playhead(), recording.size()));
		TasFreeze.setFrozen(false);
		TasSession.flash("incremental.msg.rec_start", Component.literal(recording.name));
	}

	public static void stopRecording() {
		if (!TasSession.isRecording()) {
			return;
		}
		Recording recording = TasSession.selected();
		int ticks = recording == null ? 0 : recording.size();
		TasSession.stopRecording();
		if (recording != null) {
			RecordingStorage.save(recording);
		}
		TasSession.flash("incremental.msg.rec_stop", ticks);
	}

	public static void toggleRecordingPause() {
		switch (TasSession.state()) {
			case RECORDING -> {
				TasSession.setState(TasSession.State.RECORDING_PAUSED);
				TasSession.flash("incremental.msg.rec_pause");
			}
			case RECORDING_PAUSED -> {
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
			TasSession.flash("incremental.msg.recording");
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
		TasSession.beginPlayback(tick);
		TasPlaybackInput.install(Minecraft.getInstance());
		if (Minecraft.getInstance().screen instanceof ru.incremental.screen.TasScreen) {
			// A pause screen would stop the level from ticking; playback needs the world running.
			Minecraft.getInstance().setScreen(null);
		}
		applySpeed();
		TasSession.flash("incremental.msg.play_start", Component.literal(recording.name));
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
		TasFreeze.unfreeze();
		restoreTickRate();
		clientTickTargetMillis = 0.0F;
		TasSession.flash("incremental.msg.play_stop");
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

	public static void saveEverything() {
		int saved = 0;
		for (Recording recording : RecordingStorage.all()) {
			if (RecordingStorage.save(recording)) {
				saved++;
			}
		}
		TasSession.flash("incremental.msg.saved_all_count", saved);
	}

	// ------------------------------------------------------------ speed

	public static double tickRate() {
		return IncrementalConfig.tickRate;
	}

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

	/** Applies the configured speed to the live server while a recording is playing. */
	public static void applySpeed() {
		ServerTickRateManager manager = manager();
		if (manager == null) {
			return;
		}
		if (!TasSession.isPlayingActive()) {
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
	 * Tick length the client has to run at while a recording plays, or 0 when the vanilla value
	 * applies. The integrated server and the client must tick at the same rate, otherwise the server
	 * rubber-bands the client-driven player.
	 */
	private static float clientTickTargetMillis;

	public static float clientTickTargetMillis() {
		return TasSession.isPlayingActive() ? clientTickTargetMillis : 0.0F;
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
		TasStep.tick();
		if (TasSession.isPlayingActive()) {
			playNextFrame(client);
			return;
		}
		if (!TasSession.isPlaying()) {
			return;
		}
		// Playback paused: run a single armed frame, if the user asked for one.
		RecordedFrame armed = TasStep.armedFrame();
		if (armed != null) {
			applyFrame(client, armed, true);
			TasStep.clearArmedFrame();
		}
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
		player.setYRot(yaw);
		player.setXRot(pitch);
		player.yRotO = yaw;
		player.xRotO = pitch;
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
		if (TasSession.isPlayingActive()) {
			applySpeed();
			if (TasFreeze.isFrozen()) {
				TasFreeze.unfreeze();
			}
			drainPhysicalClicks(client);
			return;
		}
		if (TasSession.state() == TasSession.State.PLAY_PAUSED && !TasFreeze.isFrozen()) {
			TasFreeze.setFrozen(true);
		}
		if (!TasSession.isBusy()) {
			restoreTickRateIfNeeded();
		}
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
