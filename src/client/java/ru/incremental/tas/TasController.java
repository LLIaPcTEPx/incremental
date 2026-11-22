package ru.incremental.tas;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import org.lwjgl.glfw.GLFW;

/**
 * Records frames, replays them and owns the per-tick "who drives the player" logic.
 */
public final class TasController {
	private TasController() {
	}

	// ------------------------------------------------------------ working copy

	/**
	 * The frames that are being recorded / edited. They are relative to the tick recording started,
	 * so an existing (saved) recording is only replaced when the user saves.
	 */
	private static Recording working;
	private static boolean workingDirty;

	public static Recording working() {
		return working;
	}

	public static boolean workingDirty() {
		return workingDirty;
	}

	// ------------------------------------------------------------ start / stop

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
		Recording selected = TasSession.selected();
		if (selected == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}

		// Continue from whatever is already in the selected recording, so a take can be redone.
		working = new Recording(selected.name);
		working.frames.addAll(selected.frames);
		workingDirty = false;
		TasSession.beginRecording(working);
		TasSession.flash("incremental.msg.rec_start", Component.literal(selected.name));
	}

	public static void stopRecording() {
		if (!TasSession.isRecording()) {
			return;
		}
		Recording recording = TasSession.active();
		workingDirty = true;
		int ticks = recording == null ? 0 : recording.size();
		TasSession.stopRecording();
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

	public static void startPlayback(Recording recording) {
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
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		if (recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		TasSession.beginPlayback(recording);
		TasSession.flash("incremental.msg.play_start", Component.literal(recording.name));
		installPlaybackInput(Minecraft.getInstance());
		applyPlaybackSpeed();
		Minecraft client = Minecraft.getInstance();
		if (client.screen == null && !client.mouseHandler.isMouseGrabbed()) {
			client.mouseHandler.grabMouse();
		}
	}

	/** Starts playback already paused, for inspecting a recording frame by frame. */
	public static void startPlaybackPaused(Recording recording) {
		startPlayback(recording);
		if (TasSession.state() == TasSession.State.PLAYING) {
			TasSession.setState(TasSession.State.PLAY_PAUSED);
			TasFreeze.setFrozen(true);
			TasSession.flash("incremental.msg.play_pause");
		}
	}

	public static void togglePlaybackPause() {
		switch (TasSession.state()) {
			case PLAYING -> {
				TasSession.setState(TasSession.State.PLAY_PAUSED);
				TasFreeze.setFrozen(true);
				TasSession.flash("incremental.msg.play_pause");
			}
			case PLAY_PAUSED -> {
				TasSession.setState(TasSession.State.PLAYING);
				TasFreeze.unfreezeServer();
				applyPlaybackSpeed();
				TasSession.flash("incremental.msg.play_resume");
			}
			default -> {
			}
		}
	}

	public static void stopPlayback() {
		if (!TasSession.isPlaying()) {
			return;
		}
		TasSession.stopPlayback();
		restorePlayerInput(Minecraft.getInstance());
		TasSession.flash("incremental.msg.play_stop");
	}

	/** The single "C" action: pauses recording or playback, whatever is running. */
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
			startPlayback(TasSession.selected());
		}
	}

	// ------------------------------------------------------------ save

	/** Writes the take that was just recorded into the selected recording. */
	public static void saveSelected() {
		Recording selected = TasSession.selected();
		if (selected == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		if (TasSession.isRecording()) {
			stopRecording();
		}
		if (working != null && working.name.equals(selected.name) && (workingDirty || working.size() != selected.size())) {
			selected.frames.clear();
			selected.frames.addAll(working.frames);
		}
		if (RecordingStorage.save(selected)) {
			workingDirty = false;
			TasSession.flash("incremental.msg.saved", Component.literal(selected.name));
		}
	}

	/** Saves whatever the selected recording is, without an active take. */
	public static void saveEverything() {
		Recording selected = TasSession.selected();
		if (selected != null) {
			RecordingStorage.save(selected);
			TasSession.flash("incremental.msg.saved", Component.literal(selected.name));
		}
	}

	// ------------------------------------------------------------ speed

	private static final double[] SPEEDS = {1, 2, 3, 4, 5, 6, 8, 10, 12, 15, 20, 30, 40, 60, 80, 100, 150, 200, 300, 400};

	public static void speedUp() {
		changeSpeed(1);
	}

	public static void speedDown() {
		changeSpeed(-1);
	}

	private static void changeSpeed(int direction) {
		int index = nearestSpeedIndex(IncrementalConfig.tickRate);
		int next = Math.max(0, Math.min(SPEEDS.length - 1, index + direction));
		IncrementalConfig.tickRate = SPEEDS[next];
		IncrementalConfig.save();
		applyPlaybackSpeed();
		TasSession.flash("incremental.msg.speed", speedLabel(IncrementalConfig.tickRate));
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
		String text = multiplier == Math.rint(multiplier) ? String.valueOf((int) multiplier) : String.format("%.2f", multiplier);
		return Component.literal(text + "x (" + String.format("%.0f", rate) + " t/s)");
	}

	/** Applies the configured playback speed to the live server tick rate. */
	public static void applyPlaybackSpeed() {
		Minecraft client = Minecraft.getInstance();
		if (client.getSingleplayerServer() == null || !TasSession.isPlaying()) {
			return;
		}
		ServerTickRateManager manager = client.getSingleplayerServer().tickRateManager();
		manager.setTickRate((float) IncrementalConfig.tickRate);
	}

	public static void restoreTickRate() {
		Minecraft client = Minecraft.getInstance();
		if (client.getSingleplayerServer() != null) {
			client.getSingleplayerServer().tickRateManager().setTickRate(20.0F);
		}
	}

	/** Idle: make sure the game is not left on a TAS speed. */
	public static void restoreTickRateIfIdle(Minecraft client) {
		if (!TasSession.isBusy() && client.getSingleplayerServer() != null) {
			client.getSingleplayerServer().tickRateManager().setTickRate(20.0F);
		}
	}

	// ------------------------------------------------------------ per tick

	/**
	 * Called from the player's tick, before vanilla reads the input: this is where frames are
	 * recorded and where the next recorded frame is applied.
	 */
	public static void onPlayerInputTick() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		TasStepKeys.poll(client);

		if (TasSession.isRecordingActive()) {
			recordFrame(client);
		} else if (TasSession.isPlaying()) {
			playFrame(client);
		}
	}

	private static void recordFrame(Minecraft client) {
		Recording recording = TasSession.active();
		if (recording == null) {
			return;
		}
		RecordedFrame frame = new RecordedFrame(client.player.input.keyPresses);
		frame.attack = mouseHeld(client, 0);
		frame.use = mouseHeld(client, 1);
		frame.hotbar = client.player.getInventory().getSelectedSlot();
		if (IncrementalConfig.recordMotion) {
			frame.yaw = TasMotion.takeYaw();
			frame.pitch = TasMotion.takePitch();
		}
		recording.frames.add(frame);
		workingDirty = true;
		TasSession.setPlayhead(recording.size());
	}

	private static void playFrame(Minecraft client) {
		Recording recording = TasSession.selected();
		if (recording == null) {
			return;
		}
		int tick = TasSession.playhead();
		if (tick >= recording.size()) {
			finishPlayback(recording);
			return;
		}
		RecordedFrame frame = recording.get(tick);
		applyFrame(client, frame);
		TasSession.setPlayhead(tick + 1);
	}

	private static void finishPlayback(Recording recording) {
		TasSession.stopPlayback();
		restorePlayerInput(Minecraft.getInstance());
		TasSession.flash("incremental.msg.play_finish", Component.literal(recording.name));
	}

	/** Injects a recorded frame: movement through the custom input, buttons through the bindings. */
	public static void applyFrame(Minecraft client, RecordedFrame frame) {
		if (client.player == null || frame == null) {
			return;
		}
		installPlaybackInput(client);
		ClientInput input = client.player.input;
		if (input instanceof TasPlaybackInput playbackInput) {
			playbackInput.setFrame(frame);
		} else {
			input.keyPresses = frame.toInput();
		}

		if (frame.hotbar >= 0 && frame.hotbar < 9 && client.player.getInventory().getSelectedSlot() != frame.hotbar) {
			client.player.getInventory().setSelectedSlot(frame.hotbar);
		}
		setPressed(client, client.options.keyAttack, frame.attack);
		setPressed(client, client.options.keyUse, frame.use);
		if (IncrementalConfig.recordMotion) {
			TasMotion.applyLook(client, frame.yaw, frame.pitch);
		}
	}

	/** Swaps the vanilla keyboard input for the playback input (and pauses the vanilla one). */
	public static void installPlaybackInput(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (client.player.input instanceof TasPlaybackInput) {
			return;
		}
		TasPlaybackInput playbackInput = new TasPlaybackInput();
		playbackInput.setFrame(null);
		client.player.input = playbackInput;
		KeyMapping.releaseAll();
	}

	/** Gives control back to the player. */
	public static void restorePlayerInput(Minecraft client) {
		if (client.player == null) {
			return;
		}
		if (client.player.input instanceof TasPlaybackInput) {
			client.player.input = new net.minecraft.client.player.KeyboardInput(client.options);
			KeyMapping.releaseAll();
		}
	}

	private static boolean mouseHeld(Minecraft client, int button) {
		return client.getWindow() != null && GLFW.glfwGetMouseButton(client.getWindow().handle(), button) == GLFW.GLFW_PRESS;
	}

	private static void setPressed(Minecraft client, KeyMapping mapping, boolean pressed) {
		if (mapping == null) {
			return;
		}
		if (!pressed) {
			mapping.setDown(false);
			while (mapping.consumeClick()) {
				// Drain queued clicks so a released button does not fire later.
			}
		} else if (!mapping.isDown()) {
			mapping.setDown(true);
			// Makes vanilla treat this as a fresh press: one attack / one use per recorded tick.
			KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(mapping == client.options.keyAttack ? 0 : 1));
		}
	}

	/** Forwards a mouse click to vanilla when the TAS froze the world (vanilla skips it then). */
	public static void feedMouseClicks(Minecraft client, boolean attack, boolean use) {
		if (!TasFreeze.isFrozen() || client.player == null) {
			return;
		}
		if (attack) {
			KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(0));
		}
		if (use) {
			KeyMapping.click(InputConstants.Type.MOUSE.getOrCreate(1));
		}
	}

	// ------------------------------------------------------------ frame editing

	public static void nextFrame() {
		TasStep.stepNextFrame();
	}

	public static void prevFrame() {
		TasStep.stepPrevFrame();
	}

	/** R / T — jump a few frames back or forth. */
	public static void jumpBack() {
		TasStep.stepBack(20);
	}

	public static void jumpForward() {
		TasStep.stepForward(20);
	}

	/** V — inserts an empty frame at the playhead so a new tick can be timed. */
	public static void insertFrame() {
		Recording recording = TasSession.isRecording() ? TasSession.active() : TasSession.selected();
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		int tick = Math.max(0, Math.min(recording.size(), TasSession.playhead()));
		recording.frames.add(tick, RecordedFrame.empty());
		TasSession.setPlayhead(tick + 1);
		TasSession.flash("incremental.msg.frame_new");
	}

	// ------------------------------------------------------------ key helpers

	public static boolean consumeMappingClick(TasKey key) {
		KeyMapping mapping = TasKey.mapping(key);
		if (mapping == null) {
			return false;
		}
		boolean clicked = false;
		while (mapping.consumeClick()) {
			clicked = true;
		}
		return clicked;
	}
}
