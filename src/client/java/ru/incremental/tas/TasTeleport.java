package ru.incremental.tas;

import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the player on the recorded path during playback.
 *
 * <p>A recording stores absolute coordinates, but replaying them does not mean teleporting every
 * tick. The input is replayed exactly, the physics is deterministic, and the world is restored to the
 * state the run was recorded on (see {@link TasBlocks}) — so in the normal case the player simply
 * walks the recorded path and nothing needs to be corrected. That is what makes a replay look
 * smooth: the movement is produced by the game, not by a series of jumps.</p>
 *
 * <p>A teleport is a <em>correction</em>, used when the player drifts away from the recording (a
 * different world state, an entity that is not there anymore, a paused server, a frame jump). It goes
 * through {@code ServerGamePacketListenerImpl.teleport()}, the only route that keeps the server's
 * authoritative position in sync.</p>
 */
public final class TasTeleport {
	/** How far the player may drift from the recording before it is snapped back, in blocks. */
	private static final double TOLERANCE = 0.75D;
	/** Vertical tolerance is smaller: a wrong Y is always a real desync. */
	private static final double VERTICAL_TOLERANCE = 0.35D;

	private TasTeleport() {
	}

	private static String lastTarget;
	private static int lastTargetTick = -1;

	/**
	 * Playback entry point: corrects the position only when the player is obviously off the recorded
	 * path.
	 */
	public static void apply(Minecraft client, RecordedFrame frame) {
		apply(client, frame, false);
	}

	/**
	 * @param force snap to the frame position even when the player is close, used when playback starts
	 *              or jumps to another frame
	 */
	public static void apply(Minecraft client, RecordedFrame frame, boolean force) {
		if (frame == null || !frame.hasPosition || client.player == null) {
			return;
		}
		if (!force && !needsCorrection(client, frame)) {
			return;
		}
		MinecraftServer server = client.getSingleplayerServer();
		if (server == null) {
			return;
		}
		ServerLevel level = findLevel(server, frame.dimension);
		if (level == null) {
			return;
		}
		ServerPlayer player = server.getPlayerList().getPlayer(client.player.getUUID());
		if (player == null) {
			return;
		}
		String target = level.dimension().identifier() + "@" + frame.x + "," + frame.y + "," + frame.z;
		int tick = client.player.tickCount;
		if (target.equals(lastTarget) && tick == lastTargetTick) {
			return;
		}
		lastTarget = target;
		lastTargetTick = tick;

		Vec3 motion = new Vec3(frame.motionX, frame.motionY, frame.motionZ);
		PositionMoveRotation destination = new PositionMoveRotation(
				new Vec3(frame.x, frame.y, frame.z), motion, frame.yaw, frame.pitch);

		// The player state belongs to the server thread; the connection queues the packet for us.
		server.execute(() -> {
			if (player.level() != level) {
				// A dimension change is a full teleport transition, not a position packet.
				player.teleportTo(level, frame.x, frame.y, frame.z, Set.<Relative>of(), frame.yaw, frame.pitch, false);
			} else {
				player.connection.teleport(destination, Set.<Relative>of());
			}
		});
	}

	/** True when the local player is far enough from the recorded position to justify a snap. */
	private static boolean needsCorrection(Minecraft client, RecordedFrame frame) {
		var player = client.player;
		if (player == null) {
			return false;
		}
		String dimension = player.level().dimension().identifier().toString();
		if (frame.dimension != null && !frame.dimension.equals(dimension)) {
			return true;
		}
		double dx = player.getX() - frame.x;
		double dy = player.getY() - frame.y;
		double dz = player.getZ() - frame.z;
		if (Math.abs(dy) > VERTICAL_TOLERANCE) {
			return true;
		}
		return dx * dx + dz * dz > TOLERANCE * TOLERANCE;
	}

	private static ServerLevel findLevel(MinecraftServer server, String dimension) {
		if (dimension != null && !dimension.isEmpty()) {
			net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.tryParse(dimension);
			if (id != null) {
				for (ServerLevel level : server.getAllLevels()) {
					if (level.dimension().identifier().equals(id)) {
						return level;
					}
				}
			}
			// The recorded dimension no longer exists (or came from another world); refuse instead of
			// teleporting to coordinates that belong to a different place.
			return null;
		}
		// Old recordings without dimension data stay in the current dimension.
		return server.overworld();
	}

	/** Forgets the cached target, e.g. when playback stops. */
	public static void reset() {
		lastTarget = null;
		lastTargetTick = -1;
	}
}
