package ru.incremental.tas;

import java.util.Locale;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

/**
 * Runtime state of the TAS: which recording is selected, whether we record or play back and at
 * which tick of the recording we currently are.
 *
 * <p>The selected {@link Recording} is the single source of truth. Recording writes into it,
 * playback reads from it, and it only touches the disk when the user saves.</p>
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
	/** Frame index the current take writes to; frames before it are kept untouched. */
	private static int recordCursor;

	private TasSession() {
	}

	// ---------------------------------------------------------------- state

	public static State state() {
		return state;
	}

	static void setState(State newState) {
		state = newState;
	}

	public static boolean isRecording() {
		return state == State.RECORDING || state == State.RECORDING_PAUSED;
	}

	/** Recording that actually captures frames right now (not paused). */
	public static boolean isRecordingActive() {
		return state == State.RECORDING;
	}

	public static boolean isPlaying() {
		return state == State.PLAYING || state == State.PLAY_PAUSED;
	}

	public static boolean isPlayingActive() {
		return state == State.PLAYING;
	}

	public static boolean isBusy() {
		return state != State.IDLE;
	}

	public static int playhead() {
		return playhead;
	}

	public static void setPlayhead(int tick) {
		playhead = Math.max(0, tick);
	}

	// ---------------------------------------------------------------- selection

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

	/** Called after a rename so the selection follows the new name. */
	public static void renamed(String oldName, String newName) {
		if (oldName != null && oldName.equals(selectedName)) {
			selectedName = newName;
		}
	}

	// ---------------------------------------------------------------- transitions

	/** Starts a take at the given frame: everything up to it is kept, the rest is overwritten. */
	static void beginRecording(int fromTick) {
		recordCursor = Math.max(0, fromTick);
		playhead = recordCursor;
		state = State.RECORDING;
	}

	/** Called when a take starts: frames after the take's first frame are stale and get dropped. */
	static void truncateFromRecordingStart() {
		Recording recording = selected();
		if (recording == null) {
			return;
		}
		while (recording.size() > recordCursor) {
			recording.remove(recording.size() - 1);
		}
	}

	public static int recordCursor() {
		return recordCursor;
	}

	static void advanceRecordCursor() {
		recordCursor++;
		playhead = recordCursor;
	}

	static void stopRecording() {
		state = State.IDLE;
	}

	static void beginPlayback(int fromTick) {
		playhead = Math.max(0, fromTick);
		state = State.PLAYING;
	}

	static void stopPlayback() {
		state = State.IDLE;
		playhead = 0;
	}

	/** Called when the game leaves the world so nothing keeps running. */
	public static void worldUnloaded() {
		TasFreeze.unfreeze();
		TasFreeze.reset();
		state = State.IDLE;
		playhead = 0;
	}

	// ---------------------------------------------------------------- helpers

	public static boolean inSingleplayer() {
		Minecraft client = Minecraft.getInstance();
		return client.hasSingleplayerServer() && client.level != null && client.player != null;
	}

	/** Shows a message above the hotbar and in the TAS overlay. */
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
