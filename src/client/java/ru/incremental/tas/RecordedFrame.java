package ru.incremental.tas;

import net.minecraft.world.entity.player.Input;

/**
 * One "frame" of a TAS recording: everything the player did during one game tick.
 *
 * <p>Fields mirror vanilla's {@link Input} record (movement keys, jump, sneak, sprint) plus the
 * mouse buttons, the selected hotbar slot and the camera motion accumulated during that tick.</p>
 */
public final class RecordedFrame {
	public boolean forward;
	public boolean backward;
	public boolean left;
	public boolean right;
	public boolean jump;
	public boolean shift;
	public boolean sprint;
	public boolean attack;
	public boolean use;
	public int hotbar;
	public float yaw;
	public float pitch;

	public RecordedFrame() {
	}

	public RecordedFrame(Input input) {
		if (input != null) {
			forward = input.forward();
			backward = input.backward();
			left = input.left();
			right = input.right();
			jump = input.jump();
			shift = input.shift();
			sprint = input.sprint();
		}
	}

	/** Same as the vanilla input flags. */
	public Input toInput() {
		return new Input(forward, backward, left, right, jump, shift, sprint);
	}

	public RecordedFrame copy() {
		RecordedFrame frame = new RecordedFrame();
		frame.forward = forward;
		frame.backward = backward;
		frame.left = left;
		frame.right = right;
		frame.jump = jump;
		frame.shift = shift;
		frame.sprint = sprint;
		frame.attack = attack;
		frame.use = use;
		frame.hotbar = hotbar;
		frame.yaw = yaw;
		frame.pitch = pitch;
		return frame;
	}

	public static RecordedFrame empty() {
		RecordedFrame frame = new RecordedFrame();
		frame.hotbar = 0;
		return frame;
	}
}
