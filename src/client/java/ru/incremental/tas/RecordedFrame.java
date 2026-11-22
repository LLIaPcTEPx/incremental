package ru.incremental.tas;

import net.minecraft.world.entity.player.Input;

/**
 * One tick of a TAS recording.
 *
 * <p>A frame keeps everything that decides where the player ends up: the input flags vanilla reads
 * on the client (movement keys, jump, sneak, sprint), the mouse buttons and the hotbar slot, the
 * absolute camera rotation and, most importantly, the exact position and dimension the player was in
 * at the end of that tick. Replaying a frame teleports the player to that position, so a recording
 * always plays back where it was captured instead of wherever the player happens to stand.</p>
 *
 * <p>Rotation and position are stored absolutely on purpose: mouse deltas are scaled by the client
 * sensitivity, and a position delta would drift as soon as one tick diverges. Absolute values make
 * the replay reproducible.</p>
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
	/** Absolute position at the end of this tick. */
	public double x;
	public double y;
	public double z;
	/** Dimension the frame belongs to, as a resource location ("minecraft:overworld"). */
	public String dimension;
	/** Set when the frame has usable position data (older recordings do not). */
	public boolean hasPosition;
	/** Delta movement at the end of the tick, so a jump keeps its arc when it is replayed. */
	public double motionX;
	public double motionY;
	public double motionZ;

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
		frame.x = x;
		frame.y = y;
		frame.z = z;
		frame.dimension = dimension;
		frame.hasPosition = hasPosition;
		frame.motionX = motionX;
		frame.motionY = motionY;
		frame.motionZ = motionZ;
		return frame;
	}

	public static RecordedFrame empty() {
		RecordedFrame frame = new RecordedFrame();
		frame.hotbar = 0;
		return frame;
	}

	/** Short human readable summary used by the HUD. */
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
		return "[" + keys + "] slot " + (hotbar + 1);
	}

	/** "x 12.34 y 64.00 z -8.50" for the status line. */
	public String positionText() {
		if (!hasPosition) {
			return "no position data";
		}
		return String.format(java.util.Locale.ROOT, "%.2f / %.2f / %.2f", x, y, z);
	}
}
