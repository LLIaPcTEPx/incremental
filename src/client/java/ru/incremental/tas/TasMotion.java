package ru.incremental.tas;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;

/**
 * Camera motion support. While recording the raw mouse delta produced by Minecraft itself is
 * captured once per tick (see {@code MouseHandlerMixin}); while playing back the recorded delta is
 * fed through the player's own {@code turn()} implementation, which is exactly what the mouse does.
 */
public final class TasMotion {
	private static double pendingYaw;
	private static double pendingPitch;

	private TasMotion() {
	}

	/** Called by the mouse handler mixin when the game consumes a mouse delta. */
	public static void onMouseTurn(double deltaX, double deltaY) {
		if (TasSession.isRecordingActive()) {
			pendingYaw += deltaX;
			pendingPitch += deltaY;
		} else if (TasSession.isPlaying()) {
			// Playback drives the camera, ignore the real mouse so it cannot fight the recording.
			Minecraft client = Minecraft.getInstance();
			client.mouseHandler.setIgnoreFirstMove();
		}
	}

	public static float takeYaw() {
		float value = (float) pendingYaw;
		pendingYaw = 0.0D;
		return value;
	}

	public static float takePitch() {
		float value = (float) pendingPitch;
		pendingPitch = 0.0D;
		return value;
	}

	public static void reset() {
		pendingYaw = 0.0D;
		pendingPitch = 0.0D;
	}

	/** Applies a recorded camera delta through the same maths vanilla uses for the mouse. */
	public static void applyLook(Minecraft client, float yawDelta, float pitchDelta) {
		if (client.player == null || (yawDelta == 0.0F && pitchDelta == 0.0F)) {
			return;
		}
		double sensitivity = (Double) client.options.sensitivity().get() * 0.6D + 0.2D;
		double sens = sensitivity * sensitivity * sensitivity * 8.0D;
		double xo = yawDelta * sens;
		double yo = pitchDelta * sens;
		if (client.options.smoothCamera) {
			xo *= 0.5D;
			yo *= 0.5D;
		}
		boolean invertX = (Boolean) client.options.invertMouseX().get();
		boolean invertY = (Boolean) client.options.invertMouseY().get();
		client.player.turn(invertX ? -xo : xo, invertY ? -yo : yo);
	}

	/**
	 * The vanilla input handler derives the movement vector from the key mappings it polls; the
	 * recorded key flags are enough, so nothing else has to be pushed here.
	 */
	public static void applyMoveVector(ClientInput input, Input wanted) {
	}
}
