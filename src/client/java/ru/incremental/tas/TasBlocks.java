package ru.incremental.tas;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blocks a take changed, so a replay can start from a clean world and clean up after itself.
 *
 * <p>This is what every serious TAS tool does: a run is only reproducible when it starts from the
 * same blocks it was recorded on. Three things happen here.</p>
 *
 * <ul>
 *   <li><b>Capture.</b> While a take records, every block change that reaches the client is noted
 *       with the state it had <em>before</em> the first change. Nothing is restored during the run:
 *       the run may break the same block twice, and the first state is the one that matters.</li>
 *   <li><b>Rewind before playback.</b> Playback first puts every noted block back to that first
 *       state ("remove what the recording placed, restore what it broke"), then plays the take, which
 *       re-creates the changes tick by tick — the same visual flow as a TAS movie replay.</li>
 *   <li><b>Clean up after playback.</b> When playback ends, every block the take touched is put back
 *       to its original state, so the world looks untouched afterwards.</li>
 * </ul>
 *
 * <p>Writes use {@code setBlock} with {@code UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE}: the client sees the
 * change, but neighbour and shape updates are skipped, so a reset never cascades into torch drops,
 * water flow or falling sand — it just writes the recorded states back.</p>
 */
public final class TasBlocks {
	/** {@code Block.UPDATE_CLIENTS} | {@code Block.UPDATE_KNOWN_SHAPE}. */
	private static final int SILENT = 2 | 16;

	/** Block position (packed long) to the state before the first recorded change. */
	private static final Map<Long, String> ORIGINAL = new LinkedHashMap<>();
	/** Packed position to the dimension, so a reset writes into the right level. */
	private static final Map<Long, String> DIMENSIONS = new LinkedHashMap<>();

	private static boolean capturing;

	private TasBlocks() {
	}

	// ---------------------------------------------------------------- capture

	public static boolean isCapturing() {
		return capturing;
	}

	public static void setCapturing(boolean value) {
		capturing = value;
	}

	/** Remembers a block the take is about to change. Only the first state is kept. */
	public static void note(BlockPos pos, BlockState before) {
		if (pos == null || before == null) {
			return;
		}
		long key = pos.asLong();
		if (ORIGINAL.containsKey(key)) {
			// The first state is the original one; later changes to the same block are the take.
			return;
		}
		ORIGINAL.put(key, before.toString());
		DIMENSIONS.put(key, currentDimension());
	}

	private static String currentDimension() {
		var player = net.minecraft.client.Minecraft.getInstance().player;
		return player == null ? "" : player.level().dimension().identifier().toString();
	}

	public static boolean isEmpty() {
		return ORIGINAL.isEmpty();
	}

	public static void clear() {
		ORIGINAL.clear();
		DIMENSIONS.clear();
	}

	public static Map<String, String> snapshot() {
		Map<String, String> snapshot = new LinkedHashMap<>();
		ORIGINAL.forEach((key, state) -> snapshot.put(Long.toString(key), state + "|" + DIMENSIONS.getOrDefault(key, "")));
		return snapshot;
	}

	public static void load(Map<String, String> snapshot) {
		clear();
		if (snapshot == null) {
			return;
		}
		snapshot.forEach((rawKey, rawValue) -> {
			try {
				int split = rawValue.lastIndexOf('|');
				String state = split < 0 ? rawValue : rawValue.substring(0, split);
				String dimension = split < 0 ? "" : rawValue.substring(split + 1);
				long key = Long.parseLong(rawKey);
				ORIGINAL.put(key, state);
				DIMENSIONS.put(key, dimension);
			} catch (NumberFormatException ignored) {
				// A malformed entry is dropped instead of breaking the whole recording.
			}
		});
	}

	// ---------------------------------------------------------------- apply

	/**
	 * Writes every recorded block back to its original state.
	 *
	 * @return number of blocks that were changed
	 */
	public static int restoreOriginals() {
		if (ORIGINAL.isEmpty()) {
			return 0;
		}
		MinecraftServer server = net.minecraft.client.Minecraft.getInstance().getSingleplayerServer();
		if (server == null) {
			return 0;
		}
		int changed = 0;
		for (Map.Entry<Long, String> entry : ORIGINAL.entrySet()) {
			ServerLevel level = levelFor(server, DIMENSIONS.get(entry.getKey()));
			if (level == null) {
				continue;
			}
			BlockPos pos = BlockPos.of(entry.getKey());
			BlockState target = parse(entry.getValue());
			if (target == null) {
				continue;
			}
			if (level.setBlock(pos, target, SILENT)) {
				changed++;
			}
		}
		return changed;
	}

	/**
	 * Puts every block the take touched back to its original state and forgets the take's blocks.
	 * Used when playback stops, so the world is left exactly as it was before the replay.
	 */
	public static int cleanup() {
		int changed = restoreOriginals();
		clear();
		return changed;
	}

	private static ServerLevel levelFor(MinecraftServer server, String dimension) {
		if (dimension == null || dimension.isEmpty()) {
			return server.overworld();
		}
		var id = net.minecraft.resources.Identifier.tryParse(dimension);
		if (id == null) {
			return server.overworld();
		}
		for (ServerLevel level : server.getAllLevels()) {
			if (level.dimension().identifier().equals(id)) {
				return level;
			}
		}
		return null;
	}

	/** Parses a state string such as {@code minecraft:oak_log[axis=y]}. */
	private static BlockState parse(String text) {
		if (text == null || text.isEmpty()) {
			return null;
		}
		try {
			var result = net.minecraft.commands.arguments.blocks.BlockStateParser.parseForBlock(
					net.minecraft.core.registries.BuiltInRegistries.BLOCK,
					text, false);
			return result.blockState();
		} catch (Exception e) {
			// Unknown block (a mod was removed) or a syntax change: fall back to air instead of
			// leaving a block that should not be there.
			return Blocks.AIR.defaultBlockState();
		}
	}
}
