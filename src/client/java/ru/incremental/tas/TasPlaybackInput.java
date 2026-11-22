package ru.incremental.tas;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/**
 * Input handler used while a recording is playing back.
 *
 * <p>It replaces the vanilla {@code KeyboardInput} on the local player, so the recorded frame is
 * the only source of movement data during playback. When no frame is pending (paused playback,
 * frozen world) the player gets an empty input and therefore keeps standing still.</p>
 */
public class TasPlaybackInput extends ClientInput {
	private RecordedFrame frame;

	public void setFrame(RecordedFrame frame) {
		this.frame = frame;
	}

	public RecordedFrame frame() {
		return frame;
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
		float forward = (current.forward ? 1.0F : 0.0F) - (current.backward ? 1.0F : 0.0F);
		float left = (current.left ? 1.0F : 0.0F) - (current.right ? 1.0F : 0.0F);
		moveVector = new Vec2(left, forward).normalized();
	}
}
