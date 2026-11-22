package ru.incremental.tas;

import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * Small indicator above the hotbar that shows what the TAS is doing.
 */
public final class TasHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("incremental", "tas_overlay");
	private static int messageTicks;
	private static Component message = Component.empty();

	private TasHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ID, TasHud::render);
	}

	public static void flashMessage(Component text) {
		message = text;
		messageTicks = 60;
	}

	public static void tick() {
		if (messageTicks > 0) {
			messageTicks--;
		}
	}

	private static void render(GuiGraphics graphics, DeltaTracker delta) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null || client.options.hideGui) {
			return;
		}
		if (!IncrementalConfig.showOverlay && messageTicks <= 0) {
			return;
		}

		int centerX = client.getWindow().getGuiScaledWidth() / 2;
		int y = client.getWindow().getGuiScaledHeight() - 72;

		if (IncrementalConfig.showOverlay) {
			Component label = statusLabel();
			if (label != null) {
				graphics.drawCenteredString(client.font, label, centerX, y, 0xFFFFFFFF);
				y += 10;
			}
			RecordedFrame frame = currentFrame();
			if (frame != null) {
				graphics.drawCenteredString(client.font,
						Component.literal(frame.describe()).withStyle(ChatFormatting.GRAY), centerX, y, 0xFFAAAAAA);
			}
		}

		if (messageTicks > 0) {
			graphics.drawCenteredString(client.font, message, centerX, y + 12, 0xFFFFD166);
		}
	}

	private static RecordedFrame currentFrame() {
		Recording recording = TasSession.selected();
		if (recording == null || recording.size() == 0) {
			return null;
		}
		int index = Math.max(0, Math.min(recording.size() - 1, TasSession.playhead() - 1));
		return recording.get(index);
	}

	private static Component statusLabel() {
		Recording recording = TasSession.selected();
		int size = recording == null ? 0 : recording.size();
		return switch (TasSession.state()) {
			case RECORDING -> Component.translatable("incremental.overlay.recording", size);
			case RECORDING_PAUSED -> Component.translatable("incremental.overlay.paused", size);
			case PLAYING -> Component.translatable("incremental.overlay.playing", TasSession.playhead(), size);
			case PLAY_PAUSED -> Component.translatable("incremental.overlay.play_paused", TasSession.playhead(), size);
			case IDLE -> null;
		};
	}
}
