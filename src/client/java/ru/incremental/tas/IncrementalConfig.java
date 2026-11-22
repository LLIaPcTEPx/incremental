package ru.incremental.tas;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Small JSON config for the mod: hotkeys (as raw GLFW key codes) and a few playback options.
 */
public final class IncrementalConfig {
	private static final Logger LOGGER = LoggerFactory.getLogger("Incremental/TAS");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static final LinkedHashMap<String, Integer> KEYS = new LinkedHashMap<>();

	public static double tickRate = 20.0D;
	public static boolean recordMotion = true;
	public static boolean showOverlay = true;
	public static boolean freezeWhileManaging = true;

	static {
		for (TasKey key : TasKey.BINDABLE) {
			KEYS.put(key.id, key.defaultKey);
		}
	}

	private IncrementalConfig() {
	}

	public static Path folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("incremental");
	}

	private static Path file() {
		return folder().resolve("config.json");
	}

	// ---------------------------------------------------------------- keys

	public static int getKey(TasKey key) {
		if (key == null) {
			return GLFW.GLFW_KEY_UNKNOWN;
		}
		if (!key.configurable) {
			return GLFW.GLFW_KEY_UNKNOWN;
		}
		Integer stored = KEYS.get(key.id);
		return stored == null ? key.defaultKey : stored;
	}

	public static void setKey(TasKey key, int glfwCode) {
		if (key == null || !key.configurable) {
			return;
		}
		KEYS.put(key.id, glfwCode);

		// Keep the vanilla key mapping in sync so the Controls screen and tooltips agree.
		var mapping = TasKey.mapping(key);
		if (mapping != null) {
			mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(glfwCode));
		}
		save();
	}

	public static void resetKey(TasKey key) {
		setKey(key, key.defaultKey);
	}

	public static void resetAllKeys() {
		for (TasKey key : TasKey.BINDABLE) {
			KEYS.put(key.id, key.defaultKey);
			var mapping = TasKey.mapping(key);
			if (mapping != null) {
				mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(key.defaultKey));
			}
		}
		save();
	}

	/** True when another bindable action already uses this key. */
	public static boolean keyUsedBy(TasKey self, int glfwCode) {
		if (glfwCode == GLFW.GLFW_KEY_UNKNOWN) {
			return false;
		}
		for (TasKey key : TasKey.BINDABLE) {
			if (key != self && getKey(key) == glfwCode) {
				return true;
			}
		}
		return false;
	}

	// ---------------------------------------------------------------- io

	public static void load() {
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
			if (root.has("keys") && root.get("keys").isJsonObject()) {
				JsonObject keys = root.getAsJsonObject("keys");
				for (String id : keys.keySet()) {
					if (KEYS.containsKey(id) && keys.get(id).isJsonPrimitive()) {
						KEYS.put(id, keys.get(id).getAsInt());
					}
				}
			}
			tickRate = clampRate(root.has("tickRate") ? root.get("tickRate").getAsDouble() : tickRate);
			recordMotion = readBoolean(root, "recordMotion", recordMotion);
			showOverlay = readBoolean(root, "showOverlay", showOverlay);
			freezeWhileManaging = readBoolean(root, "freezeWhileManaging", freezeWhileManaging);
		} catch (Exception e) {
			LOGGER.warn("Could not read {}", file, e);
		}
	}

	public static void save() {
		JsonObject root = new JsonObject();
		JsonObject keys = new JsonObject();
		KEYS.forEach(keys::addProperty);
		root.add("keys", keys);
		root.addProperty("tickRate", tickRate);
		root.addProperty("recordMotion", recordMotion);
		root.addProperty("showOverlay", showOverlay);
		root.addProperty("freezeWhileManaging", freezeWhileManaging);
		try {
			Files.createDirectories(folder());
			try (Writer writer = Files.newBufferedWriter(file())) {
				GSON.toJson(root, writer);
			}
		} catch (IOException e) {
			LOGGER.warn("Could not write {}", file(), e);
		}
	}

	public static double clampRate(double rate) {
		return Math.max(1.0D, Math.min(400.0D, rate));
	}

	private static boolean readBoolean(JsonObject root, String name, boolean fallback) {
		return root.has(name) && root.get(name).isJsonPrimitive() ? root.get(name).getAsBoolean() : fallback;
	}

	public static Map<String, Integer> rawKeys() {
		return new LinkedHashMap<>(KEYS);
	}
}
