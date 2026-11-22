package ru.incremental.screen;

import java.util.List;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import ru.incremental.tas.Recording;
import ru.incremental.tas.RecordingStorage;
import ru.incremental.tas.TasController;
import ru.incremental.tas.TasSession;

/**
 * "Recordings": the list of every recording plus the record / play / edit controls.
 *
 * <p>The first row of the list is always the {@code + Create recording} entry, which opens the name
 * dialog. Recordings can be created, selected, renamed, deleted and played from here.</p>
 */
public class RecordingsScreen extends TasScreen {
	private static final int ROW_H = 22;

	private int scroll;
	private int listX;
	private int listY;
	private int listWidth;
	private int listHeight;
	private int listRows = 6;
	private String deleteCandidate;
	private long deleteCandidateUntil;

	public RecordingsScreen(Screen parent) {
		super(Component.translatable("incremental.screen.recordings.title"), parent);
	}

	@Override
	protected void buildWidgets() {
		panelWidth = Math.min(width - 20, 470);
		panelHeight = Math.min(height - 20, 320);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentTop = panelY + 28;

		listX = panelX + 8;
		listY = contentTop;
		listWidth = panelWidth - 16;
		// Title header, two button rows, the save row, the status line and the footer.
		listRows = fitRows(panelHeight, 28 + 24 + 24 + 24 + 60, ROW_H);
		listHeight = listRows * ROW_H;

		int y = listY + listHeight + 6;
		int half = (listWidth - 4) / 2;

		addRenderableWidget(button(recordLabel(), listX, y, half, 20, b -> {
			if (TasSession.isRecording()) {
				TasController.stopRecording();
			} else {
				TasController.startRecording();
			}
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("key.incremental.start_record")));

		addRenderableWidget(button(playLabel(), listX + half + 4, y, half, 20, b -> {
			if (TasSession.isPlaying()) {
				TasController.stopPlayback();
			} else {
				TasController.startPlayback();
			}
			rebuildWidgets();
		})).setTooltip(Tooltip.create(Component.translatable("key.incremental.play")));

		y += 24;
		int third = (listWidth - 8) / 3;
		addRenderableWidget(button(Component.translatable("incremental.button.edit"), listX, y, third, 20, b -> {
			if (TasSession.selected() != null && !TasSession.isPlaying()) {
				TasController.startPlaybackPaused(0);
				Minecraft.getInstance().setScreen(null);
			}
		})).setTooltip(Tooltip.create(Component.translatable("incremental.button.edit.tooltip")));

		addRenderableWidget(button(Component.translatable("incremental.button.rename"), listX + third + 4, y, third, 20, b -> {
			Recording selected = TasSession.selected();
			if (selected != null) {
				Minecraft.getInstance().setScreen(new NameDialogScreen(this, NameDialogScreen.Mode.RENAME, selected));
			}
		}));

		addRenderableWidget(button(deleteLabel(), listX + (third + 4) * 2, y, third, 20, b -> {
			Recording selected = TasSession.selected();
			if (selected == null) {
				return;
			}
			long now = System.currentTimeMillis();
			if (selected.name.equals(deleteCandidate) && now < deleteCandidateUntil) {
				RecordingStorage.delete(selected);
				TasSession.select(null);
				deleteCandidate = null;
				TasSession.flash("incremental.msg.deleted", Component.literal(selected.name));
			} else {
				deleteCandidate = selected.name;
				deleteCandidateUntil = now + 2500L;
			}
			rebuildWidgets();
		}));

		addRenderableWidget(button(Component.translatable("incremental.button.save"), listX, y + 24, third, 20, b -> {
			TasController.saveSelected();
			rebuildWidgets();
		}));

		addRenderableWidget(doneButton(panelX + panelWidth - 78, panelY + panelHeight - 24, 70));
	}

	private static Component recordLabel() {
		if (!TasSession.isRecording()) {
			return Component.translatable("incremental.button.record");
		}
		return TasSession.state() == TasSession.State.RECORDING_PAUSED
				? Component.translatable("incremental.msg.rec_resume")
				: Component.translatable("incremental.button.stop_record");
	}

	private static Component playLabel() {
		if (!TasSession.isPlaying()) {
			return Component.translatable("incremental.button.play");
		}
		return TasSession.state() == TasSession.State.PLAY_PAUSED
				? Component.translatable("incremental.msg.play_resume")
				: Component.translatable("incremental.msg.play_stop");
	}

	private Component deleteLabel() {
		Recording selected = TasSession.selected();
		if (selected != null && selected.name.equals(deleteCandidate) && System.currentTimeMillis() < deleteCandidateUntil) {
			return Component.translatable("incremental.button.delete_confirm").withStyle(ChatFormatting.RED);
		}
		return Component.translatable("incremental.button.delete");
	}

	private int rowCount() {
		return RecordingStorage.all().size() + 1;
	}

	private int maxScroll() {
		return Math.max(0, rowCount() - listRows);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		List<Recording> recordings = RecordingStorage.all();
		int rows = recordings.size() + 1;

		graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
		for (int row = 0; row < listRows; row++) {
			int index = row + scroll;
			if (index >= rows) {
				break;
			}
			int y = listY + row * ROW_H;
			boolean hovered = mouseX >= listX && mouseX < listX + listWidth && mouseY >= y && mouseY < y + ROW_H - 2;

			if (index == 0) {
				graphics.fill(listX, y, listX + listWidth, y + ROW_H - 2, hovered ? 0xFF1F3340 : 0xFF17242B);
				graphics.drawString(font, "+ " + Component.translatable("incremental.button.create").getString(), listX + 8, y + 7, ACCENT, false);
				continue;
			}

			Recording recording = recordings.get(index - 1);
			boolean selected = recording.name.equals(TasSession.selectedName());
			int color = selected ? ROW_SELECTED : hovered ? ROW_HOVER : ROW_BG;
			graphics.fill(listX, y, listX + listWidth, y + ROW_H - 2, color);
			if (selected) {
				graphics.fill(listX, y, listX + 2, y + ROW_H - 2, ACCENT);
			}

			String name = recording.name;
			int maxWidth = listWidth - 120;
			while (font.width(name) > maxWidth && name.length() > 1) {
				name = name.substring(0, name.length() - 1);
			}
			graphics.drawString(font, Component.literal(name), listX + 8, y + 7, selected ? 0xFFFFFFFF : TEXT, false);

			String info = recording.size() + " t · " + recording.durationText();
			graphics.drawString(font, Component.literal(info).withStyle(ChatFormatting.GRAY),
					listX + listWidth - 8 - font.width(info), y + 7, MUTED, false);
		}
		graphics.disableScissor();

		if (rows == 1) {
			drawCenteredText(graphics, Component.translatable("incremental.recordings.empty"), listY + listHeight / 2, MUTED);
		}

		if (maxScroll() > 0) {
			int barX = listX + listWidth - 3;
			graphics.fill(barX, listY, barX + 2, listY + listHeight, 0xFF20242C);
			float ratio = (float) listRows / rows;
			int thumb = Math.max(12, (int) (listHeight * ratio));
			int offset = (int) ((listHeight - thumb) * ((float) scroll / maxScroll()));
			graphics.fill(barX, listY + offset, barX + 2, listY + offset + thumb, ACCENT_SOFT);
		}

		Recording selected = TasSession.selected();
		Component status = selected == null
				? Component.translatable("incremental.recordings.select")
				: Component.literal(selected.name + "   " + selected.size() + " t   " + selected.durationText()
						+ (selected.savedAt == 0L ? "   " + Component.translatable("incremental.recordings.unsaved").getString() : ""));
		graphics.drawString(font, status, listX, listY + listHeight + 78, selected == null ? MUTED : TEXT, false);

		if (deleteCandidate != null && System.currentTimeMillis() < deleteCandidateUntil) {
			drawCenteredText(graphics, Component.translatable("incremental.recordings.confirm_delete"), listY + listHeight + 96, DANGER);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0 && event.x() >= listX && event.x() <= listX + listWidth
				&& event.y() >= listY && event.y() < listY + listHeight) {
			List<Recording> recordings = RecordingStorage.all();
			int rows = recordings.size() + 1;
			for (int row = 0; row < listRows; row++) {
				int index = row + scroll;
				if (index >= rows) {
					break;
				}
				int y = listY + row * ROW_H;
				if (event.y() >= y && event.y() < y + ROW_H - 2) {
					Minecraft client = Minecraft.getInstance();
					if (index == 0) {
						client.setScreen(new NameDialogScreen(this, NameDialogScreen.Mode.CREATE, null));
					} else {
						TasSession.select(recordings.get(index - 1));
						rebuildWidgets();
					}
					return true;
				}
			}
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
		if (mouseX >= listX && mouseX <= listX + listWidth && mouseY >= listY && mouseY <= listY + listHeight) {
			if (maxScroll() > 0) {
				scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0, maxScroll());
				return true;
			}
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		// Enter on a selected recording starts / stops playback and goes straight back to the game.
		if (event.key() == 257 && TasSession.selected() != null) {
			if (TasSession.isPlaying()) {
				TasController.stopPlayback();
			} else {
				TasController.startPlayback();
				Minecraft.getInstance().setScreen(null);
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void tick() {
		super.tick();
		if (deleteCandidate != null && System.currentTimeMillis() >= deleteCandidateUntil) {
			deleteCandidate = null;
			rebuildWidgets();
		}
	}
}
