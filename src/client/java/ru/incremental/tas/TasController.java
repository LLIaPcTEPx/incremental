package ru.incremental.tas;

import java.util.Set;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.ClientInput;
import net.minecraft.network.chat.Component;
import net.minecraft.server.ServerTickRateManager;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.lwjgl.glfw.GLFW;

/**
 * Records frames, replays them and owns the per-tick "who drives the player" logic.
 */
public final class TasController {
	private TasController() {
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
		// Starting a recording always starts from an empty take, the old content is replaced on save.
		selected.frames.clear();
		TasSession.beginRecording(selected);
		TasSession.flash("incremental.msg.rec_start", Component.literal(selected.name));
		TasFreeze.applyScreenState(Minecraft.getInstance().screen != null);
	}

	public static void stopRecording() {
		if (!TasSession.isRecording()) {
			return;
		}
		Recording recording = TasSession.active();
		int ticks = recording == null ? 0 : recording.size();
		TasSession.stopRecording();
		TasSession.flash("incremental.msg.rec_stop", ticks);
	}

	public static void toggleRecordingPause() {
		switch (TasSession.state()) {
			case RECORDING -> {
				TasSession.state = TasSession.State.RECORDING_PAUSED;
				TasSession.flash("incremental.msg.rec_pause");
			}
			case RECORDING_PAUSED -> {
				TasSession.state = TasSession.State.RECORDING;
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
		// Playback runs while the world ticks normally: give the mouse back to the game.
		Minecraft client = Minecraft.getInstance();
		if (client.screen == null && !client.mouseHandler.isMouseGrabbed()) {
			client.mouseHandler.grabMouse();
		}
	}

	public static void togglePlaybackPause() {
		switch (TasSession.state()) {
			case PLAYING -> {
				TasSession.state = TasSession.State.PLAY_PAUSED;
				TasFreeze.freezeServer();
				TasSession.flash("incremental.msg.play_pause");
			}
			case PLAY_PAUSED -> {
				TasSession.state = TasSession.State.PLAYING;
				TasFreeze.unfreezeServer();
				TasSession.flash("incremental.msg.play_resume");
			}
			default -> {
			}
		}
	}

	/** Starts playback already paused, for inspecting a recording frame by frame. */
	public static void startPlaybackPaused(Recording recording) {
		startPlayback(recording);
		if (TasSession.state() == TasSession.State.PLAYING) {
			TasSession.state = TasSession.State.PLAY_PAUSED;
			TasFreeze.setFrozen(true);
			TasSession.flash("incremental.msg.play_pause");
		}
	}

	public static void stopPlayback() {
		if (!TasSession.isPlaying()) {
			return;
		}
		Recording recording = TasSession.selected();
		TasSession.stopPlayback();
		TasSession.flash("incremental.msg.play_stop");
		Minecraft client = Minecraft.getInstance();
		if (recording != null && client.gui != null) {
			client.gui.setOverlayMessage(Component.translatable("incremental.msg.play_stop"), false);
		}
	}

	/** The single "C" action: pauses recording or playback, whatever is running. */
	public static void togglePause() {
		if (TasSession.isRecording()) {
			toggleRecordingPause();
		} else if (TasSession.isPlaying()) {
			togglePlaybackPause();
		}
	}

	/** Starts/stops recording or playback for the selected recording. */
	public static void toggleRecordOrPlay() {
		if (TasSession.isRecording()) {
			stopRecording();
		} else if (TasSession.isPlaying()) {
			stopPlayback();
		} else {
			startRecording();
		}
	}

	public static void playOrStop() {
		if (TasSession.isPlaying()) {
			stopPlayback();
		} else {
			startPlayback(TasSession.selected());
		}
	}

	public static void saveSelected() {
		Recording recording = TasSession.selected();
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		if (RecordingStorage.save(recording)) {
			TasSession.flash("incremental.msg.saved", Component.literal(recording.name));
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
		applyTickRate();
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

	/** Applies the configured speed to the live server tick rate while playing. */
	private static void applyTickRate() {
		Minecraft client = Minecraft.getInstance();
		if (client.getSingleplayerServer() == null) {
			return;
		}
		ServerTickRateManager manager = client.getSingleplayerServer().tickRateManager();
		if (TasSession.isPlaying()) {
			manager.setTickRate((float) IncrementalConfig.tickRate);
		}
	}

	/** Applies the configured playback speed to the server tick rate. */
	public static void applyPlaybackSpeed() {
		Minecraft client = Minecraft.getInstance();
		if (client.getSingleplayerServer() == null || !TasSession.isPlaying()) {
			return;
		}
		client.getSingleplayerServer().tickRateManager().setTickRate((float) IncrementalConfig.tickRate);
	}

	static void restoreTickRate() {
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
	 * Called from the player's {@code aiStep} before vanilla reads the input. This is where frames
	 * are recorded / played back, because vanilla already consumed the keyboard state at that point.
	 */
	public static void onPlayerInputTick() {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		TasStepKeys.poll(client);
		if (client.player == null) {
			return;
		}
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
		frame.shift = frame.shift || client.options.keyShift.isDown();
		recording.frames.add(frame);
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
		TasSession.flash("incremental.msg.play_finish", Component.literal(recording.name));
	}

	/** Injects a recorded frame into the vanilla input handler. */
	public static void applyFrame(Minecraft client, RecordedFrame frame) {
		if (client.player == null || frame == null) {
			return;
		}
		ClientInput input = client.player.input;
		Input wanted = frame.toInput();
		input.keyPresses = wanted;
		// Keep the virtual movement vector in sync, vanilla derives movement from it.
		TasMotion.applyMoveVector(input, wanted);
		if (frame.hotbar >= 0 && frame.hotbar < 9 && client.player.getInventory().getSelectedSlot() != frame.hotbar) {
			client.player.getInventory().setSelectedSlot(frame.hotbar);
		}
		setPressed(client, client.options.keyAttack, frame.attack);
		setPressed(client, client.options.keyUse, frame.use);
		if (IncrementalConfig.recordMotion) {
			TasMotion.applyLook(client, frame.yaw, frame.pitch);
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

	public static boolean screenAllowsButtons(Minecraft client) {
		return client.screen == null;
	}

	private static boolean mouseHeld(Minecraft client, int button) {
		return GLFW.glfwGetMouseButton(client.getWindow().handle(), button) == GLFW.GLFW_PRESS;
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
		Recording recording = TasSession.selected();
		if (recording == null) {
			TasSession.flash("incremental.msg.need_recording");
			return;
		}
		int tick = Math.max(0, Math.min(recording.size(), TasSession.playhead()));
		recording.frames.add(tick, RecordedFrame.empty());
		TasSession.setPlayhead(tick + 1);
		TasSession.flash("incremental.msg.frame_new");
	}

	// ------------------------------------------------------------ key polling

	/**
	 * GLFW keyboard repeat fires while a key is held, game ticks can be slower. Frames are stepped
	 * from the render loop with a short cooldown so one press equals one frame.
	 */
	private static final int FRAME_COOLDOWN_MS = 60;
	private static long lastFrameStep;

	public static boolean frameKeysReady() {
		return System.currentTimeMillis() - lastFrameStep >= FRAME_COOLDOWN_MS;
	}

	public static void markFrameStep() {
		lastFrameStep = System.currentTimeMillis();
	}

	public static boolean isKeyHeld(TasKey key) {
		Minecraft client = Minecraft.getInstance();
		if (client.getWindow() == null) {
			return false;
		}
		int code = IncrementalConfig.getKey(key);
		if (code == GLFW.GLFW_KEY_UNKNOWN) {
			return false;
		}
		return InputConstants.isKeyDown(client.getWindow(), code);
	}

	/** Consumes one queued press of a TasKey that is bound to the given key mapping. */
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

	/** True when the given screen should swallow TAS hotkeys (chat, text fields, other menus). */
	public static boolean screenBlocksHotkeys(Screen screen) {
		return screen != null;
	}

	public static Set<String> pressedKeyNames() {
		return Set.of();
	}
}
