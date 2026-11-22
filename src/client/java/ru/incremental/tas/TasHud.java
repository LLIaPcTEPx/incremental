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
		RecordedFrame frame = null;
		String text = null;
		if (TasSession.isRecording() || TasSession.isPlaying()) {
			RecordedFrame current = null;
			Recording recording = TasSession.isPlaying() ? TasSession.selected() : TasSession.active();
			int tick = TasSession.playhead();
			if (recording != null && recording.size() > 0) {
				int index = Math.max(0, Math.min(recording.size() - 1, tick - 1));
				current = recording.get(index);
			}
			frame = current;
		} else if (messageTicks > 0) {
			frame = null;
		}

		if (!IncrementalConfig.showOverlay && messageTicks <= 0) {
			return;
		}

		int centerX = Minecraft.getInstance().getWindow().getGuiScaledWidth() / 2;
		int y = Minecraft.getInstance().getWindow().getGuiScaledHeight() - 72;

		if (IncrementalConfig.showOverlay) {
			Component label = statusLabel();
			if (label != null) {
				graphics.drawCenteredString(client.font, label, centerX, y, 0xFFFFFFFF);
				y += 10;
			}
			String info = frameInfo(frame);
			if (info != null) {
				graphics.drawCenteredString(client.font, Component.literal(info).withStyle(ChatFormatting.GRAY), centerX, y, 0xFFAAAAAA);
			}
		}

		if (messageTicks > 0) {
			graphics.drawCenteredString(client.font, message, centerX, y + 12, 0xFFFFD166);
		}
	}

	private static Component statusLabel() {
		Recording recording = TasSession.isPlaying() ? TasSession.selected() : TasSession.active();
		return switch (TasSession.state()) {
			case RECORDING -> Component.translatable("incremental.overlay.recording", recording == null ? 0 : recording.size());
			case RECORDING_PAUSED -> Component.translatable("incremental.overlay.paused", recording == null ? 0 : recording.size());
			case PLAYING -> Component.translatable("incremental.overlay.playing", TasSession.playhead(), recording == null ? 0 : recording.size());
			case PLAY_PAUSED -> Component.translatable("incremental.overlay.play_paused", TasSession.playhead(), recording == null ? 0 : recording.size());
			case IDLE -> null;
		};
	}

	private static String frameInfo(RecordedFrame frame) {
		if (frame == null) {
			return null;
		}
		StringBuilder keys = new StringBuilder();
		if (frame.forward) keys.append("W");
		if (frame.backward) keys.append("S");
		if (frame.left) keys.append("A");
		if (frame.right) keys.append("D");
		if (frame.jump) keys.append("J");
		if (frame.shift) keys.append("⇧");
		if (frame.sprint) keys.append("⤢");
		if (frame.attack) keys.append("LMB");
		if (frame.use) keys.append("RMB");
		if (keys.isEmpty()) {
			keys.append("—");
		}
		return "[" + keys + "]  slot " + (frame.hotbar + 1) + "  " + String.format("yaw %.1f", frame.yaw);
	}
}
