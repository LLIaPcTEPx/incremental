package ru.incremental;

import net.minecraft.client.Minecraft;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import ru.incremental.tas.IncrementalConfig;
import ru.incremental.tas.RecordingStorage;
import ru.incremental.tas.TasHud;
import ru.incremental.tas.TasKey;

/**
 * Client entry point: key mappings, config, HUD overlay and the global lifecycle hooks.
 *
 * <p>The per-tick logic itself lives in mixins, because it has to run at specific points of the
 * vanilla tick (before the key bindings are read and after the player consumed its input).</p>
 */
public class IncrementalClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		IncrementalConfig.load();
		TasKey.registerAll();
		IncrementalConfig.syncAllMappings();
		RecordingStorage.ensureLoaded();
		TasHud.register();

		ClientTickEvents.END_CLIENT_TICK.register(client -> onEndTick(client));
	}

	private static void onEndTick(Minecraft client) {
		TasHud.tick();
		if (client.player == null || client.level == null) {
			return;
		}
		// Safety net: while nothing runs, the world must never be left frozen or sped up.
		if (!ru.incremental.tas.TasSession.isBusy()) {
			ru.incremental.tas.TasFreeze.unfreeze();
		}
	}
}
