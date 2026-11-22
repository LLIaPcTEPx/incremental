package ru.incremental.tas;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads and stores recordings as JSON files under {@code <game dir>/incremental/recordings}.
 * Saving is deliberately manual: the user decides when a recording is persisted.
 */
public final class RecordingStorage {
	private static final Logger LOGGER = LoggerFactory.getLogger("Incremental/TAS");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String EXTENSION = ".json";

	private static final List<Recording> RECORDINGS = new ArrayList<>();
	private static boolean loaded;

	private RecordingStorage() {
	}

	public static Path folder() {
		return IncrementalConfig.folder().resolve("recordings");
	}

	private static Path pathFor(String name) {
		return folder().resolve(name + EXTENSION);
	}

	public static void ensureLoaded() {
		if (loaded) {
			return;
		}
		loaded = true;
		reload();
	}

	public static void reload() {
		RECORDINGS.clear();
		Path folder = folder();
		if (!Files.isDirectory(folder)) {
			return;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, "*" + EXTENSION)) {
			List<Path> files = new ArrayList<>();
			stream.forEach(files::add);
			files.sort(Comparator.comparing(Path::getFileName));
			for (Path file : files) {
				Recording recording = read(file);
				if (recording != null) {
					RECORDINGS.add(recording);
				}
			}
		} catch (IOException e) {
			LOGGER.warn("Could not list {}", folder, e);
		}
	}

	public static List<Recording> all() {
		ensureLoaded();
		return List.copyOf(RECORDINGS);
	}

	public static Recording find(String name) {
		ensureLoaded();
		if (name == null) {
			return null;
		}
		for (Recording recording : RECORDINGS) {
			if (recording.name.equals(name)) {
				return recording;
			}
		}
		return null;
	}

	public static boolean exists(String name) {
		return find(name) != null;
	}

	/** Registers a new (empty) recording in the list. It is written to disk on the first save. */
	public static Recording create(String rawName) {
		ensureLoaded();
		String name = Recording.sanitize(rawName);
		if (name.isEmpty()) {
			return null;
		}
		Recording existing = find(name);
		if (existing != null) {
			return null;
		}
		Recording recording = new Recording(name);
		RECORDINGS.add(recording);
		return recording;
	}

	public static boolean rename(Recording recording, String rawName) {
		ensureLoaded();
		String name = Recording.sanitize(rawName);
		if (recording == null || name.isEmpty()) {
			return false;
		}
		if (name.equals(recording.name)) {
			return true;
		}
		if (find(name) != null) {
			return false;
		}
		delete(recording);
		recording.name = name;
		RECORDINGS.add(recording);
		if (recording.savedAt > 0) {
			save(recording);
		}
		return true;
	}

	public static boolean delete(Recording recording) {
		ensureLoaded();
		if (recording == null) {
			return false;
		}
		RECORDINGS.remove(recording);
		try {
			Files.deleteIfExists(pathFor(recording.name));
		} catch (IOException e) {
			LOGGER.warn("Could not delete recording {}", recording.name, e);
		}
		return true;
	}

	/** Writes the recording to disk. */
	public static boolean save(Recording recording) {
		if (recording == null) {
			return false;
		}
		long now = System.currentTimeMillis();
		JsonObject root = new JsonObject();
		root.addProperty("format", 2);
		root.addProperty("name", recording.name);
		root.addProperty("savedAt", now);
		root.addProperty("minecraft", "1.21.11");
		JsonArray frames = new JsonArray();
		for (RecordedFrame frame : recording.frames) {
			JsonObject json = new JsonObject();
			json.addProperty("fwd", frame.forward);
			json.addProperty("back", frame.backward);
			json.addProperty("left", frame.left);
			json.addProperty("right", frame.right);
			json.addProperty("jump", frame.jump);
			json.addProperty("shift", frame.shift);
			json.addProperty("sprint", frame.sprint);
			json.addProperty("attack", frame.attack);
			json.addProperty("use", frame.use);
			json.addProperty("hotbar", frame.hotbar);
			json.addProperty("yaw", frame.yaw);
			json.addProperty("pitch", frame.pitch);
			if (frame.hasPosition) {
				json.addProperty("x", frame.x);
				json.addProperty("y", frame.y);
				json.addProperty("z", frame.z);
				json.addProperty("mx", frame.motionX);
				json.addProperty("my", frame.motionY);
				json.addProperty("mz", frame.motionZ);
				if (frame.dimension != null) {
					json.addProperty("dim", frame.dimension);
				}
			}
			frames.add(json);
		}
		root.add("frames", frames);
		try {
			Files.createDirectories(folder());
			try (Writer writer = Files.newBufferedWriter(pathFor(recording.name))) {
				GSON.toJson(root, writer);
			}
			recording.savedAt = now;
			return true;
		} catch (IOException e) {
			LOGGER.error("Could not save recording {}", recording.name, e);
			return false;
		}
	}

	private static Recording read(Path file) {
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonElement element = JsonParser.parseReader(reader);
			if (!element.isJsonObject()) {
				return null;
			}
			JsonObject root = element.getAsJsonObject();
			String name = root.has("name") ? root.get("name").getAsString() : stripExtension(file);
			Recording recording = new Recording(Recording.sanitize(name));
			recording.savedAt = root.has("savedAt") ? root.get("savedAt").getAsLong() : 0L;
			if (root.has("frames") && root.get("frames").isJsonArray()) {
				for (JsonElement frameElement : root.getAsJsonArray("frames")) {
					if (!frameElement.isJsonObject()) {
						continue;
					}
					JsonObject json = frameElement.getAsJsonObject();
					RecordedFrame frame = new RecordedFrame();
					frame.forward = bool(json, "fwd");
					frame.backward = bool(json, "back");
					frame.left = bool(json, "left");
					frame.right = bool(json, "right");
					frame.jump = bool(json, "jump");
					frame.shift = bool(json, "shift");
					frame.sprint = bool(json, "sprint");
					frame.attack = bool(json, "attack");
					frame.use = bool(json, "use");
					frame.hotbar = json.has("hotbar") ? json.get("hotbar").getAsInt() : 0;
					frame.yaw = json.has("yaw") ? json.get("yaw").getAsFloat() : 0.0F;
					frame.pitch = json.has("pitch") ? json.get("pitch").getAsFloat() : 0.0F;
					if (json.has("x") && json.has("y") && json.has("z")) {
						frame.x = json.get("x").getAsDouble();
						frame.y = json.get("y").getAsDouble();
						frame.z = json.get("z").getAsDouble();
						frame.motionX = json.has("mx") ? json.get("mx").getAsDouble() : 0.0D;
						frame.motionY = json.has("my") ? json.get("my").getAsDouble() : 0.0D;
						frame.motionZ = json.has("mz") ? json.get("mz").getAsDouble() : 0.0D;
						frame.dimension = json.has("dim") ? json.get("dim").getAsString() : null;
						frame.hasPosition = true;
					}
					recording.frames.add(frame);
				}
			}
			return recording;
		} catch (Exception e) {
			LOGGER.warn("Could not read recording {}", file, e);
			return null;
		}
	}

	private static boolean bool(JsonObject json, String name) {
		return json.has(name) && json.get(name).getAsBoolean();
	}

	private static String stripExtension(Path file) {
		String fileName = file.getFileName().toString();
		return fileName.endsWith(EXTENSION) ? fileName.substring(0, fileName.length() - EXTENSION.length()) : fileName;
	}
}
