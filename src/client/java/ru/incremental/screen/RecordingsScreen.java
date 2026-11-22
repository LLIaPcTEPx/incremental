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
 * "Выбор записи": the list of all recordings plus the record / play controls.
 *
 * <p>The very first entry is always the "Create recording" row which opens the name dialog.
 * Selecting a row only selects it; the actions live in the footer.</p>
 */
public class RecordingsScreen extends TasScreen {
	private static final int ROW_H = 22;

	private int scroll;
	private int listX;
	private int listY;
	private int listWidth;
	private int listHeight;
	private String deleteCandidate;
	private long deleteCandidateUntil;

	public RecordingsScreen(Screen parent) {
		super(Component.translatable("incremental.screen.recordings.title"), parent);
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	protected void buildWidgets() {
		panelWidth = Math.min(width - 20, 470);
		panelHeight = Math.min(height - 20, 286);
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;
		contentTop = panelY + 28;

		listX = panelX + 8;
		listY = contentTop;
		listWidth = panelWidth - 16;
		listHeight = panelHeight - 28 - 78;

		int footerY = panelY + panelHeight - 24;
		int row2Y = footerY - 24;

		addRenderableWidget(doneButton(panelX + panelWidth - 78, footerY, 70));

		addRenderableWidget(button(recordLabel(), panelX + 8, row2Y, 118, 20, b -> {
			if (TasSession.isRecording()) {
				TasController.stopRecording();
			} else {
				TasController.startRecording();
			}
			Minecraft.getInstance().setScreen(new RecordingsScreen(parent));
		}));

		addRenderableWidget(button(playLabel(), panelX + 8 + 122, row2Y, 118, 20, b -> {
			if (TasSession.isPlaying()) {
				TasController.stopPlayback();
			} else {
				TasController.playOrStop();
			}
			Minecraft.getInstance().setScreen(new RecordingsScreen(parent));
		})).setTooltip(Tooltip.create(Component.translatable("key.incremental.play")));

		addRenderableWidget(button(Component.translatable("incremental.button.edit"), panelX + 8 + 244, row2Y, 82, 20, b -> {
			Recording selected = TasSession.selected();
			if (selected == null) {
				return;
			}
			if (!TasSession.isPlaying()) {
				TasController.startPlaybackPaused(selected);
			}
			Minecraft.getInstance().setScreen(null);
		})).setTooltip(Tooltip.create(Component.translatable("incremental.button.edit")));

		addRenderableWidget(button(Component.translatable("incremental.button.save"), panelX + 8 + 330, row2Y, 82, 20, b -> {
			TasController.saveSelected();
			Minecraft.getInstance().setScreen(new RecordingsScreen(parent));
		}));

		addRenderableWidget(button(Component.translatable("incremental.button.rename"), panelX + 8, footerY, 82, 20, b -> {
			Recording selected = TasSession.selected();
			if (selected != null) {
				Minecraft.getInstance().setScreen(new NameDialogScreen(this, NameDialogScreen.Mode.RENAME, selected));
			}
		}));

		addRenderableWidget(button(deleteLabel(), panelX + 8 + 86, footerY, 98, 20, b -> {
			Recording selected = TasSession.selected();
			if (selected == null) {
				return;
			}
			long now = System.currentTimeMillis();
			if (deleteCandidate != null && deleteCandidate.equals(selected.name) && now < deleteCandidateUntil) {
				RecordingStorage.delete(selected);
				TasSession.select(null);
				deleteCandidate = null;
			} else {
				deleteCandidate = selected.name;
				deleteCandidateUntil = now + 2500L;
			}
			Minecraft.getInstance().setScreen(new RecordingsScreen(parent));
		}));
	}

	private static Component recordLabel() {
		if (!TasSession.isRecording()) {
			return Component.translatable("key.incremental.start_record");
		}
		return TasSession.state() == TasSession.State.RECORDING_PAUSED
				? Component.translatable("incremental.msg.rec_resume")
				: Component.translatable("key.incremental.stop_record");
	}

	private static Component playLabel() {
		if (!TasSession.isPlaying()) {
			return Component.translatable("incremental.button.play");
		}
		return TasSession.state() == TasSession.State.PLAY_PAUSED
				? Component.translatable("incremental.msg.play_resume")
				: Component.translatable("incremental.msg.play_stop");
	}

	private static Component deleteLabel() {
		return Component.translatable("incremental.button.delete");
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);

		List<Recording> recordings = RecordingStorage.all();
		int rows = recordings.size() + 1;
		int visibleRows = visibleRows();

		graphics.enableScissor(listX, listY, listX + listWidth, listY + listHeight);
		for (int row = 0; row < visibleRows; row++) {
			int index = row + scroll;
			if (index >= rows) {
				break;
			}
			int y = listY + row * ROW_H;
			boolean hovered = mouseX >= listX && mouseX <= listX + listWidth && mouseY >= y && mouseY < y + ROW_H - 2;

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
			int maxWidth = listWidth - 110;
			while (font.width(name) > maxWidth && name.length() > 1) {
				name = name.substring(0, name.length() - 1);
			}
			graphics.drawString(font, Component.literal(name), listX + 8, y + 7, selected ? 0xFFFFFFFF : TEXT, false);

			String info = recording.size() + " t · " + recording.durationText();
			int infoWidth = font.width(info);
			graphics.drawString(font, Component.literal(info).withStyle(ChatFormatting.GRAY), listX + listWidth - 8 - infoWidth, y + 7, MUTED, false);
		}
		graphics.disableScissor();

		if (rows <= 1) {
			drawCenteredText(graphics, Component.translatable("incremental.recordings.empty"), listY + listHeight / 2, MUTED);
		}

		if (rows > visibleRows) {
			int barX = listX + listWidth - 3;
			graphics.fill(barX, listY, barX + 2, listY + listHeight, 0xFF20242C);
			float ratio = (float) visibleRows / rows;
			int thumb = Math.max(12, (int) (listHeight * ratio));
			int maxScroll = rows - visibleRows;
			int offset = maxScroll == 0 ? 0 : (int) ((listHeight - thumb) * ((float) scroll / maxScroll));
			graphics.fill(barX, listY + offset, barX + 2, listY + offset + thumb, ACCENT_SOFT);
		}

		Recording selected = TasSession.selected();
		Component status = selected == null
				? Component.translatable("incremental.recordings.select")
				: Component.literal(selected.name + "  ·  " + selected.size() + " t  ·  " + selected.durationText());
		graphics.drawString(font, status, panelX + 8 + 92, panelY + panelHeight - 41, selected == null ? MUTED : TEXT, false);

		if (deleteCandidate != null && System.currentTimeMillis() < deleteCandidateUntil) {
			drawCenteredText(graphics, Component.translatable("incremental.recordings.confirm_delete"), listY - 11, DANGER);
		}
	}

	private int visibleRows() {
		return Math.max(1, listHeight / ROW_H);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (event.button() == 0) {
			List<Recording> recordings = RecordingStorage.all();
			int rows = recordings.size() + 1;
			int visibleRows = visibleRows();
			for (int row = 0; row < visibleRows; row++) {
				int index = row + scroll;
				if (index >= rows) {
					break;
				}
				int y = listY + row * ROW_H;
				if (event.x() >= listX && event.x() <= listX + listWidth && event.y() >= y && event.y() < y + ROW_H - 2) {
					Minecraft client = Minecraft.getInstance();
					if (index == 0) {
						client.setScreen(new NameDialogScreen(this, NameDialogScreen.Mode.CREATE, null));
					} else {
						TasSession.select(recordings.get(index - 1));
						client.setScreen(new RecordingsScreen(parent));
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
			int maxScroll = Math.max(0, RecordingStorage.all().size() + 1 - visibleRows());
			scroll = Mth.clamp(scroll - (int) Math.signum(vertical), 0, maxScroll);
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 && TasSession.selected() != null) {
			if (TasSession.isPlaying()) {
				TasController.stopPlayback();
			} else {
				TasController.startPlayback(TasSession.selected());
				Minecraft.getInstance().setScreen(null);
			}
			return true;
		}
		if (event.key() == 261 && TasSession.selected() != null) {
			long now = System.currentTimeMillis();
			Recording selected = TasSession.selected();
			if (deleteCandidate != null && deleteCandidate.equals(selected.name) && now < deleteCandidateUntil) {
				RecordingStorage.delete(selected);
				TasSession.select(null);
				deleteCandidate = null;
				Minecraft.getInstance().setScreen(new RecordingsScreen(parent));
			} else {
				deleteCandidate = selected.name;
				deleteCandidateUntil = now + 2500L;
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
		}
	}
}
