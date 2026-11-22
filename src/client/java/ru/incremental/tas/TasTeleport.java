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
 * Moves the player around during playback.
 *
 * <p>A recording stores absolute coordinates, so replaying one means putting the player exactly
 * where the take was captured, including the dimension. That cannot be done client side: the server
 * keeps its own copy of the player and snaps the client back whenever the jump does not look like a
 * legal movement. The supported route is {@code ServerGamePacketListenerImpl.teleport()}, which snaps
 * the server player, sets the awaiting-teleport state and sends the matching
 * {@code ClientboundPlayerPositionPacket}; the client accepts it and both sides stay in sync.</p>
 */
public final class TasTeleport {
	private TasTeleport() {
	}

	/** Last target, so the same position is not teleported to twice within one tick. */
	private static String lastTarget;
	private static int lastTargetTick = -1;

	/**
	 * Teleports the player to the frame position. Called from the client tick, before the level
	 * ticks, so the server handles the teleport in the same tick the frame belongs to.
	 */
	public static void apply(Minecraft client, RecordedFrame frame) {
		if (frame == null || !frame.hasPosition || client.player == null) {
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
