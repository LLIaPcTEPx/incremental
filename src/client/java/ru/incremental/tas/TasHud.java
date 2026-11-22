package ru.incremental.tas;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;

/**
 * The TAS status line above the hotbar.
 *
 * <p>Everything is drawn on <b>one</b> line: the vanilla overlay message, the hotbar and this element
 * all live in the same small area, and stacking several lines here is what made the text overlap
 * itself. The mod therefore never calls {@code Gui.setOverlayMessage} and shows status + last action
 * on a single centred row, well above the vanilla message slot.</p>
 */
public final class TasHud {
	private static final Identifier ID = Identifier.fromNamespaceAndPath("incremental", "tas_overlay");
	private static final int MESSAGE_TICKS = 70;

	private static int messageTicks;
	private static Component message = Component.empty();

	private TasHud() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.HOTBAR, ID, TasHud::render);
	}

	public static void flashMessage(Component text) {
		message = text;
		messageTicks = MESSAGE_TICKS;
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
		boolean status = IncrementalConfig.showOverlay && TasSession.isBusy();
		boolean flash = messageTicks > 0;
		if (!status && !flash) {
			return;
		}

		int centerX = client.getWindow().getGuiScaledWidth() / 2;
		// The vanilla overlay message sits right above the hotbar; keep a clear gap so nothing
		// doubles up when both are visible at once.
		int y = client.getWindow().getGuiScaledHeight() - 68;

		// A dark plate behind the text makes it readable over any terrain and prevents the
		// "text on text" look even when the background is busy.
		Component line = status ? statusLine() : null;
		if (line != null) {
			int width = client.font.width(line);
			graphics.fill(centerX - width / 2 - 3, y - 2, centerX + width / 2 + 3, y + 9, 0x90000000);
			graphics.drawCenteredString(client.font, line, centerX, y, 0xFFFFFFFF);
			y -= 12;
		}

		if (flash) {
			int width = client.font.width(message);
			int flashY = status ? y : client.getWindow().getGuiScaledHeight() - 68;
			graphics.fill(centerX - width / 2 - 3, flashY - 2, centerX + width / 2 + 3, flashY + 9, 0x90000000);
			graphics.drawCenteredString(client.font, message, centerX, flashY, 0xFFFFD166);
		}
	}

	private static Component statusLine() {
		Recording recording = TasSession.selected();
		int size = recording == null ? 0 : recording.size();
		String tick = recording == null ? "?" : String.valueOf(recording.size());
		String speed = TasController.speedLabel(IncrementalConfig.tickRate).getString();

		String state = switch (TasSession.state()) {
			case RECORDING -> Component.translatable("incremental.overlay.recording").getString();
			case RECORDING_PAUSED -> (TasSession.isRewindPaused()
					? Component.translatable("incremental.overlay.rewound").getString()
					: Component.translatable("incremental.overlay.paused").getString());
			case PLAYING -> Component.translatable("incremental.overlay.playing").getString();
			case PLAY_PAUSED -> Component.translatable("incremental.overlay.play_paused").getString();
			case IDLE -> "";
		};

		// "REC 123/123  1x (20 t/s)  tick 8421  x/y/z"
		StringBuilder line = new StringBuilder(state);
		line.append(' ').append(TasSession.playhead()).append('/').append(tick);
		line.append("  ").append(speed);
		if (Minecraft.getInstance().level != null) {
			line.append("  tick ").append(Minecraft.getInstance().level.getGameTime());
		}
		if (Minecraft.getInstance().player != null) {
			line.append("  ").append(String.format(java.util.Locale.ROOT, "%.1f %.1f %.1f",
					Minecraft.getInstance().player.getX(),
					Minecraft.getInstance().player.getY(),
					Minecraft.getInstance().player.getZ()));
		}
		line.append(" / ").append(size);
		if (TasSession.isRewindPaused()) {
			// "kept 340" tells the user the rest of the run is still there after a rewind.
			line.append("  kept ").append(TasSession.framesAfterCursor());
		}
		return Component.literal(line.toString());
	}
}
