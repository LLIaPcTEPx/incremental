package ru.incremental.tas;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Every hotkey of the mod.
 *
 * <p>The binding lives in the mod config as a raw GLFW code (mouse buttons are stored as
 * {@code -100 - button}), while a matching {@link KeyMapping} mirrors it in the vanilla "Controls"
 * screen. The mappings are never polled directly — hotkeys are read from GLFW so they also work
 * while the world is frozen — but they give the user the familiar controls UI and the
 * {@code key.incremental.*} translations.</p>
 */
public enum TasKey {
	START_RECORD("start_record", GLFW.GLFW_KEY_INSERT),
	STOP_RECORD("stop_record", GLFW.GLFW_KEY_END),
	TOGGLE_PAUSE("toggle_pause", GLFW.GLFW_KEY_C),
	SPEED_UP("speed_up", GLFW.GLFW_KEY_PERIOD),
	SPEED_DOWN("speed_down", GLFW.GLFW_KEY_COMMA),
	REWIND("rewind", GLFW.GLFW_KEY_R),
	FORWARD("forward", GLFW.GLFW_KEY_T),
	PREV_FRAME("prev_frame", GLFW.GLFW_KEY_F),
	NEXT_FRAME("next_frame", GLFW.GLFW_KEY_G),
	NEW_FRAME("new_frame", GLFW.GLFW_KEY_V),
	SAVE("save", GLFW.GLFW_KEY_LEFT_BRACKET),
	PLAY("play", GLFW.GLFW_KEY_RIGHT_BRACKET);

	/** Keys shown and edited in the settings screen, in this order. */
	public static final List<TasKey> BINDABLE = List.of(values());

	public final String id;
	public final int defaultKey;
	public final String translationKey;

	TasKey(String id, int defaultKey) {
		this.id = id;
		this.defaultKey = defaultKey;
		this.translationKey = "key.incremental." + id;
	}

	private static KeyMapping.Category category;
	private static KeyMapping[] mappings;

	public static KeyMapping.Category category() {
		if (category == null) {
			category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("incremental", "tas"));
		}
		return category;
	}

	/** Creates every key mapping. Called once during client initialisation. */
	public static void registerAll() {
		if (mappings != null) {
			return;
		}
		KeyMapping.Category category = category();
		mappings = new KeyMapping[values().length];
		for (TasKey key : values()) {
			mappings[key.ordinal()] = new KeyMapping(key.translationKey, InputConstants.Type.KEYSYM, key.defaultKey, category);
		}
	}

	public static KeyMapping mapping(TasKey key) {
		return mappings == null ? null : mappings[key.ordinal()];
	}

	/** The binding currently in use. */
	public InputConstants.Key boundKey() {
		int code = IncrementalConfig.getKey(this);
		if (code < 0) {
			return InputConstants.Type.MOUSE.getOrCreate(-100 - code);
		}
		return InputConstants.Type.KEYSYM.getOrCreate(code);
	}

	/** Human readable binding, e.g. "Insert" or "Not bound". */
	public static Component describe(TasKey key) {
		return key.boundKey().getDisplayName();
	}

	public static TasKey byId(String id) {
		for (TasKey key : values()) {
			if (key.id.equals(id)) {
				return key;
			}
		}
		return null;
	}
}
