package ru.incremental;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;

import ru.incremental.tas.IncrementalConfig;
import ru.incremental.tas.RecordingStorage;
import ru.incremental.tas.TasController;
import ru.incremental.tas.TasFreeze;
import ru.incremental.tas.TasHud;
import ru.incremental.tas.TasKey;
import ru.incremental.tas.TasSession;
import ru.incremental.tas.TasStepKeys;

/**
 * Client entry point: key mappings, config, HUD overlay and the global hotkeys.
 */
public class IncrementalClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		TasKey.registerAll();
		IncrementalConfig.load();
		RecordingStorage.ensureLoaded();
		applyKeyMappings();

		TasHud.register();

		ClientTickEvents.END_CLIENT_TICK.register(IncrementalClient::onEndTick);
		ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> TasFreeze.applyScreenState(true));
	}

	/** Copies the mod config into the vanilla key mappings so the controls screen matches. */
	private static void applyKeyMappings() {
		for (TasKey key : TasKey.BINDABLE) {
			KeyMapping mapping = TasKey.mapping(key);
			if (mapping != null) {
				mapping.setKey(InputConstants.Type.KEYSYM.getOrCreate(IncrementalConfig.getKey(key)));
			}
		}
		KeyMapping.resetMapping();
	}

	private static void onEndTick(Minecraft client) {
		TasHud.tick();
		if (client.player == null || client.level == null) {
			return;
		}
		handleHotkeys(client);
		stateTick(client);
	}

	/** Global hotkeys. Text fields never receive them, the pause menu uses its own buttons. */
	private static void handleHotkeys(Minecraft client) {
		boolean textInput = client.screen != null && client.screen.getFocused() instanceof EditBox;
		if (textInput) {
			return;
		}

		for (TasKey key : TasKey.BINDABLE) {
			if (key == TasKey.TOGGLE_PAUSE || key == TasKey.SPEED_UP || key == TasKey.SPEED_DOWN) {
				continue;
			}
			if (TasController.consumeMappingClick(key)) {
				dispatch(key);
			}
		}

		// C must also work while the world is frozen or a menu is open.
		if (TasStepKeys.pollPress(TasKey.TOGGLE_PAUSE, 0)) {
			TasController.togglePause();
		}
	}

	private static void dispatch(TasKey key) {
		switch (key) {
			case START_RECORD -> TasController.startRecording();
			case STOP_RECORD -> TasController.stopRecording();
			case REWIND -> TasController.jumpBack();
			case FORWARD -> TasController.jumpForward();
			case PREV_FRAME -> TasController.prevFrame();
			case NEXT_FRAME -> TasController.nextFrame();
			case NEW_FRAME -> TasController.insertFrame();
			case SAVE -> TasController.saveSelected();
			case PLAY -> TasController.playOrStop();
			default -> {
			}
		}
	}

	/** Applies the freeze policy and the playback tick rate once per tick. */
	private static void stateTick(Minecraft client) {
		boolean tasScreen = TasSession.screenFreezesWorld();

		switch (TasSession.state()) {
			case PLAYING -> {
				TasFreeze.unfreezeServer();
				TasController.applyPlaybackSpeed();
			}
			case PLAY_PAUSED -> {
				if (!TasFreeze.isFrozen()) {
					TasFreeze.setFrozen(true);
				}
			}
			default -> {
				boolean shouldFreeze = tasScreen || (TasSession.isRecording() && client.screen != null && IncrementalConfig.freezeWhileManaging);
				if (shouldFreeze && !TasFreeze.isFrozen()) {
					TasFreeze.setFrozen(true);
				} else if (!shouldFreeze && TasFreeze.isFrozen() && !(client.screen instanceof ru.incremental.screen.TasScreen)) {
					TasFreeze.unfreezeServer();
				}
				TasController.restoreTickRateIfIdle(client);
			}
		}
	}
}
