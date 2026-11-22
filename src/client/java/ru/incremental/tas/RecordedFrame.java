package ru.incremental.tas;

import net.minecraft.world.entity.player.Input;

/**
 * One tick of a TAS recording.
 *
 * <p>A frame stores the input flags vanilla reads on the client (movement keys, jump, sneak,
 * sprint), the mouse buttons and the selected hotbar slot, plus the camera state. The camera is
 * stored as absolute rotation: mouse deltas are scaled by the client's sensitivity, so replaying
 * deltas would break as soon as the sensitivity setting changes. Absolute rotation is also what the
 * server receives, which keeps the replay identical to the original take.</p>
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
	/** Absolute camera rotation at the end of this tick, in degrees. */
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

	/** Short human readable summary used by the HUD and the frame list. */
	public String describe() {
		StringBuilder keys = new StringBuilder();
		if (forward) keys.append("W");
		if (backward) keys.append("S");
		if (left) keys.append("A");
		if (right) keys.append("D");
		if (jump) keys.append("J");
		if (shift) keys.append("⇧");
		if (sprint) keys.append("⤢");
		if (attack) keys.append("LMB");
		if (use) keys.append("RMB");
		if (keys.isEmpty()) {
			keys.append("—");
		}
		return "[" + keys + "]  slot " + (hotbar + 1)
				+ String.format("  yaw %.0f  pitch %.0f", yaw, pitch);
	}
}
