package ru.incremental.tas;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Runtime state of the TAS: which recording is selected, whether we record or play back and at
 * which tick of the recording we currently are.
 */
public final class TasSession {
	public enum State {
		IDLE,
		RECORDING,
		RECORDING_PAUSED,
		PLAYING,
		PLAY_PAUSED
	}

	private static State state = State.IDLE;
	private static String selectedName;
	private static int playhead;
	private static String lastHudMessage;

	private TasSession() {
	}

	// ---------------------------------------------------------------- state

	public static State state() {
		return state;
	}

	public static boolean isRecording() {
		return state == State.RECORDING || state == State.RECORDING_PAUSED;
	}

	public static boolean isRecordingActive() {
		return state == State.RECORDING;
	}

	public static boolean isPlaying() {
		return state == State.PLAYING || state == State.PLAY_PAUSED;
	}

	public static boolean isBusy() {
		return state != State.IDLE;
	}

	public static int playhead() {
		return playhead;
	}

	public static void setPlayhead(int tick) {
		playhead = tick;
	}

	public static String selectedName() {
		return selectedName;
	}

	public static Recording selected() {
		return RecordingStorage.find(selectedName);
	}

	public static void select(Recording recording) {
		selectedName = recording == null ? null : recording.name;
		playhead = 0;
	}

	/** Recording that new frames are appended to. */
	private static Recording active;

	public static Recording active() {
		return active;
	}

	static void beginRecording(Recording recording) {
		active = recording;
		selectedName = recording.name;
		playhead = 0;
		state = State.RECORDING;
	}

	static void stopRecording() {
		state = State.IDLE;
		active = null;
	}

	static void beginPlayback(Recording recording) {
		selectedName = recording.name;
		playhead = 0;
		state = State.PLAYING;
		TasFreeze.reset();
	}

	static void stopPlayback() {
		state = State.IDLE;
		playhead = 0;
		TasFreeze.unfreezeServer();
		TasFreeze.reset();
	}

	/** Called when the game closes the level so nothing keeps ticking frozen. */
	public static void worldUnloaded() {
		if (isPlaying()) {
			TasFreeze.unfreezeServer();
		}
		TasFreeze.reset();
		state = State.IDLE;
		active = null;
		playhead = 0;
	}

	// ---------------------------------------------------------------- helpers

	/** A TAS screen is open and the config asks to freeze the world while managing. */
	public static boolean screenFreezesWorld() {
		Minecraft client = Minecraft.getInstance();
		return client.screen instanceof ru.incremental.screen.TasScreen && IncrementalConfig.freezeWhileManaging;
	}

	public static boolean inSingleplayer() {
		Minecraft client = Minecraft.getInstance();
		return client.hasSingleplayerServer() && client.level != null && client.player != null;
	}

	public static void hudMessage(String text) {
		lastHudMessage = text;
	}

	public static String lastHudMessage() {
		return lastHudMessage;
	}

	/** Shows a message above the hotbar. */
	public static void flash(Component message) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui != null) {
			client.gui.setOverlayMessage(message, false);
		}
		TasHud.flashMessage(message);
	}

	public static void flash(String translationKey, Object... args) {
		flash(Component.translatable(translationKey, args));
	}

	public static void toast(Component title, Component description) {
		Minecraft client = Minecraft.getInstance();
		client.getToastManager().addToast(new SystemToast(SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description));
	}

	public static String formatTicks(int ticks) {
		return String.format(Locale.ROOT, "%d", ticks);
	}
}
