package ru.incremental.tas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Input handler installed on the local player while a recording is being replayed.
 *
 * <p>Vanilla's {@code KeyboardInput.tick()} runs inside {@code LocalPlayer.aiStep()} and
 * overwrites whatever was written to {@code input.keyPresses} before that, so a recorded frame
 * cannot simply be poked into the vanilla handler. Installing this handler makes the recorded frame
 * the only source of movement data: {@link #tick()} keeps returning the frame until a new one is
 * applied, and the handler returns an empty input when playback is paused or stopping, so the
 * player never keeps running with stale keys.</p>
 */
public class TasPlaybackInput extends ClientInput {
	private RecordedFrame frame;

	public void setFrame(RecordedFrame frame) {
		this.frame = frame;
	}

	public RecordedFrame frame() {
		return frame;
	}

	public void clear() {
		this.frame = null;
	}

	@Override
	public void tick() {
		RecordedFrame current = frame;
		if (current == null) {
			keyPresses = Input.EMPTY;
			moveVector = Vec2.ZERO;
			return;
		}
		keyPresses = current.toInput();
		float forwardImpulse = (current.forward ? 1.0F : 0.0F) - (current.backward ? 1.0F : 0.0F);
		float leftImpulse = (current.left ? 1.0F : 0.0F) - (current.right ? 1.0F : 0.0F);
		moveVector = new Vec2(leftImpulse, forwardImpulse).normalized();
	}

	/** Puts a vanilla keyboard input back on the local player. */
	public static void uninstall(Minecraft client) {
		if (client.player != null && client.player.input instanceof TasPlaybackInput) {
			client.player.input = new KeyboardInput(client.options);
		}
	}

	/** Installs the playback input if it is not installed yet; returns the live instance. */
	public static TasPlaybackInput install(Minecraft client) {
		if (client.player == null) {
			return null;
		}
		if (client.player.input instanceof TasPlaybackInput) {
			return (TasPlaybackInput) client.player.input;
		}
		TasPlaybackInput playback = new TasPlaybackInput();
		client.player.input = playback;
		return playback;
	}
}
