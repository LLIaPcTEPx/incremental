package ru.incremental.screen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import ru.incremental.tas.Recording;
import ru.incremental.tas.RecordingStorage;
import ru.incremental.tas.TasSession;

/**
 * Dialog that asks for the name of a recording. The same screen is reused for renaming.
 */
public class NameDialogScreen extends TasScreen {
	public enum Mode {
		CREATE,
		RENAME
	}

	private final Mode mode;
	private final Recording recording;
	private Component error;

	public NameDialogScreen(Screen parent, Mode mode, Recording recording) {
		super(Component.translatable(mode == Mode.CREATE ? "incremental.screen.create.title" : "incremental.screen.rename.title"), parent);
		this.mode = mode;
		this.recording = recording;
	}

	@Override
	public boolean isPauseScreen() {
		return true;
	}

	@Override
	protected void buildWidgets() {
		panelWidth = Math.min(width - 40, 320);
		panelHeight = 116;
		panelX = (width - panelWidth) / 2;
		panelY = (height - panelHeight) / 2;

		String initial = mode == Mode.RENAME && recording != null ? recording.name : "";
		nameField = new EditBox(font, panelX + 10, panelY + 44, panelWidth - 20, 20, Component.translatable("incremental.field.name"));
		nameField.setMaxLength(64);
		nameField.setValue(initial);
		nameField.setHint(Component.translatable("incremental.field.name"));
		nameField.setResponder(value -> error = null);
		addRenderableWidget(nameField);
		setInitialFocus(nameField);
		nameField.moveCursorToEnd(false);

		addRenderableWidget(button(Component.translatable(mode == Mode.CREATE ? "incremental.button.create" : "incremental.button.rename"),
				panelX + panelWidth - 168, panelY + panelHeight - 26, 76, 20, b -> confirm()));
		addRenderableWidget(cancelButton(panelX + panelWidth - 84, panelY + panelHeight - 26, 74));
	}

	private void confirm() {
		String value = nameField == null ? "" : nameField.getValue();
		String name = Recording.sanitize(value);
		if (name.isEmpty()) {
			error = Component.translatable("incremental.msg.need_name");
			return;
		}
		if (mode == Mode.CREATE) {
			if (RecordingStorage.exists(name)) {
				error = Component.translatable("incremental.msg.name_taken");
				return;
			}
			Recording created = RecordingStorage.create(name);
			if (created == null) {
				error = Component.translatable("incremental.msg.name_taken");
				return;
			}
			TasSession.select(created);
			TasSession.flash("incremental.msg.created", Component.literal(created.name));
		} else {
			if (recording == null) {
				onClose();
				return;
			}
			if (!RecordingStorage.rename(recording, name)) {
				error = Component.translatable("incremental.msg.name_taken");
				return;
			}
			TasSession.select(recording);
			TasSession.flash("incremental.msg.renamed", Component.literal(recording.name));
		}
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == 257 || event.key() == 335) {
			confirm();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		super.render(graphics, mouseX, mouseY, delta);
		graphics.drawString(font, Component.translatable("incremental.field.name"), panelX + 10, panelY + 32, MUTED, false);
		if (error != null) {
			graphics.drawString(font, error, panelX + 10, panelY + 68, DANGER, false);
		}
	}
}
