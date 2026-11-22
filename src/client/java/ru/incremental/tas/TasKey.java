package ru.incremental.tas;

import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/**
 * Every hotkey of the mod. The binding itself lives in an {@link InputConstants.Key} inside the
 * mod config (so it is saved next to the recordings), while a hidden {@link KeyMapping} exposes the
 * same binding in the vanilla "Controls" screen.
 */
public enum TasKey {
	START_RECORD("start_record", "Начать запись", GLFW.GLFW_KEY_INSERT),
	STOP_RECORD("stop_record", "Остановка записи", GLFW.GLFW_KEY_END),
	TOGGLE_PAUSE("toggle_pause", "Пауза записи", GLFW.GLFW_KEY_C),
	SPEED_UP("speed_up", "Увеличение скорости", GLFW.GLFW_KEY_PERIOD),
	SPEED_DOWN("speed_down", "Уменьшение скорости", GLFW.GLFW_KEY_COMMA),
	REWIND("rewind", "Назад", GLFW.GLFW_KEY_R),
	FORWARD("forward", "Вперёд", GLFW.GLFW_KEY_T),
	PREV_FRAME("prev_frame", "Назад на 1 кадр", GLFW.GLFW_KEY_F),
	NEXT_FRAME("next_frame", "Вперёд на 1 кадр", GLFW.GLFW_KEY_G),
	NEW_FRAME("new_frame", "Новый кадр", GLFW.GLFW_KEY_V),
	SAVE("save", "Сохранение", GLFW.GLFW_KEY_LEFT_BRACKET),
	PLAY("play", "Воспроизведение выбранной записи", GLFW.GLFW_KEY_RIGHT_BRACKET),
	NEW_ENTRY("new_entry", "Создать запись", GLFW.GLFW_KEY_UNKNOWN);

	public final String id;
	public final String defaultValue;
	public final int defaultKey;
	public final String translationKey;
	public final boolean configurable;

	TasKey(String id, String defaultValue, int defaultKey) {
		this(id, defaultValue, defaultKey, true);
	}

	TasKey(String id, String defaultValue, int defaultKey, boolean configurable) {
		this.id = id;
		this.defaultValue = defaultValue;
		this.defaultKey = defaultKey;
		this.translationKey = "key.incremental." + id;
		this.configurable = configurable;
	}

	/** Keys actually stored in the config and shown in the settings screen. */
	public static final List<TasKey> BINDABLE = List.of(
			START_RECORD, STOP_RECORD, TOGGLE_PAUSE, SPEED_UP, SPEED_DOWN,
			REWIND, FORWARD, PREV_FRAME, NEXT_FRAME, NEW_FRAME, SAVE, PLAY
	);

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

	public static KeyMapping[] mappings() {
		return mappings == null ? new KeyMapping[0] : mappings.clone();
	}

	/** The binding currently in use (config override, otherwise the default). */
	public InputConstants.Key boundKey() {
		return InputConstants.Type.KEYSYM.getOrCreate(IncrementalConfig.getKey(this));
	}

	public static TasKey valueOfKey(String name) {
		try {
			return valueOf(name);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	public static TasKey byId(String id) {
		for (TasKey key : values()) {
			if (key.id.equals(id)) {
				return key;
			}
		}
		return null;
	}

	/** Human readable binding, e.g. "Insert" or "Не назначена". */
	public static Component describe(InputConstants.Key key) {
		if (key == null || key.equals(InputConstants.UNKNOWN)) {
			return Component.translatable("key.keyboard.unknown");
		}
		return key.getDisplayName();
	}

}
