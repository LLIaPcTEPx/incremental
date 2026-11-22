package ru.incremental.tas;

import net.minecraft.client.Minecraft;

/**
 * Frame stepping during playback.
 *
 * <p>While playback is paused the world is frozen (the integrated server does not advance). The
 * "next frame" action asks for exactly one game tick and advances the playhead, which applies the
 * next recorded frame to the player on that tick. "Previous frame" is a cheap rewind: the recorded
 * camera rotation of the frame before the playhead is re-applied so the frame can be inspected.</p>
 */
public final class TasStep {
	private TasStep() {
	}

	/** Requests one game tick and moves the playhead forward when that tick has run. */
	public static void stepNextFrame() {
		Recording recording = TasSession.selected();
		if (TasSession.isRecording()) {
			TasSession.flash("incremental.msg.playback_not_recording");
			return;
		}
		if (recording == null || recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		if (!TasSession.isPlaying()) {
			// Not playing: only move the inspection cursor.
			TasSession.setPlayhead(Math.min(recording.size(), TasSession.playhead() + 1));
			TasSession.flash("incremental.msg.frame", TasSession.playhead(), recording.size());
			return;
		}
		if (TasSession.state() != TasSession.State.PLAY_PAUSED) {
			// Playing normally — pause first so single stepping makes sense.
			TasController.togglePlaybackPause();
		}
		if (TasSession.playhead() >= recording.size()) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		TasFreeze.stepFrame(() -> {
			// The tick ran: apply the frame that is now current so the next step starts from it.
			Minecraft client = Minecraft.getInstance();
			int tick = TasSession.playhead();
			if (tick < recording.size()) {
				TasController.applyFrame(client, recording.get(tick));
				TasSession.setPlayhead(tick + 1);
			}
			TasSession.flash("incremental.msg.frame", TasSession.playhead(), recording.size());
		});
	}

	/** Steps back one recorded frame and previews it. */
	public static void stepPrevFrame() {
		Recording recording = TasSession.selected();
		if (recording == null || recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		if (TasSession.state() == TasSession.State.PLAYING) {
			TasController.togglePlaybackPause();
		}
		int tick = Math.max(0, TasSession.playhead() - 1);
		TasSession.setPlayhead(tick);
		if (TasSession.isPlaying()) {
			TasController.applyFrame(Minecraft.getInstance(), recording.get(tick));
			TasFreeze.setFrozen(true);
		}
		TasSession.flash("incremental.msg.frame", tick + 1, recording.size());
	}

	public static void stepBack(int frames) {
		Recording recording = TasSession.selected();
		if (recording == null || recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		if (TasSession.state() == TasSession.State.PLAYING) {
			TasController.togglePlaybackPause();
		}
		int tick = Math.max(0, TasSession.playhead() - frames);
		TasSession.setPlayhead(tick);
		if (TasSession.isPlaying()) {
			TasController.applyFrame(Minecraft.getInstance(), recording.get(tick));
		}
		TasSession.flash("incremental.msg.rewound", tick);
	}

	public static void stepForward(int frames) {
		Recording recording = TasSession.selected();
		if (recording == null || recording.size() == 0) {
			TasSession.flash("incremental.msg.frame_empty");
			return;
		}
		int tick = Math.min(recording.size(), TasSession.playhead() + frames);
		TasSession.setPlayhead(tick);
		TasSession.flash("incremental.msg.forwarded", tick);
	}

	public static void fastForwardWhilePlaying() {
		Minecraft client = Minecraft.getInstance();
		if (TasSession.isPlaying() && client.getSingleplayerServer() != null) {
			client.getSingleplayerServer().tickRateManager().setTickRate((float) IncrementalConfig.tickRate);
		}
	}
}
