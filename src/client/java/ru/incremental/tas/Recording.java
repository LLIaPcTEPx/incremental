package ru.incremental.tas;

import java.util.ArrayList;
import java.util.List;

/**
 * A named list of frames. Stored as a single JSON file inside {@code <game dir>/incremental/recordings}.
 */
public final class Recording {
	/** Name and therefore file name of this recording. */
	public String name;
	/** Epoch millis when the recording was last saved. */
	public long savedAt;
	public final List<RecordedFrame> frames = new ArrayList<>();

	public Recording(String name) {
		this.name = name;
	}

	public int size() {
		return frames.size();
	}

	public RecordedFrame get(int index) {
		return frames.get(index);
	}

	public String durationText() {
		int ticks = frames.size();
		int seconds = ticks / 20;
		return String.format("%d:%02d.%02d", seconds / 60, seconds % 60, (ticks % 20) * 5);
	}

	public static String sanitize(String input) {
		String cleaned = input == null ? "" : input.trim();
		cleaned = cleaned.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
		if (cleaned.length() > 64) {
			cleaned = cleaned.substring(0, 64).trim();
		}
		return cleaned;
	}
}
