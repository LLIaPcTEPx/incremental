package ru.incremental.mixin;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.screen.RecordingsScreen;
import ru.incremental.screen.TasConfigScreen;

/**
 * Adds exactly two TAS buttons to the in-game pause menu: "TAS settings" and "Recordings".
 *
 * <p>The vanilla menu is a two-column grid centred at a quarter of the screen height. The buttons
 * are placed in a third row underneath the grid, where the layout leaves free space.</p>
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {
	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void incremental$addTasButtons(CallbackInfo ci) {
		PauseScreen self = (PauseScreen) (Object) this;
		if (!self.showsPauseMenu()) {
			return;
		}
		int buttonWidth = 98;
		int gap = 8;
		int totalWidth = buttonWidth * 2 + gap;
		int x = this.width / 2 - totalWidth / 2;
		int y = this.height / 4 + 122;

		Button config = Button.builder(Component.translatable("incremental.menu.config"), button ->
						this.minecraft.setScreen(new TasConfigScreen(self)))
				.bounds(x, y, buttonWidth, 20)
				.tooltip(Tooltip.create(Component.translatable("incremental.menu.config.tooltip")))
				.build();
		Button recordings = Button.builder(Component.translatable("incremental.menu.recordings"), button ->
						this.minecraft.setScreen(new RecordingsScreen(self)))
				.bounds(x + buttonWidth + gap, y, buttonWidth, 20)
				.tooltip(Tooltip.create(Component.translatable("incremental.menu.recordings.tooltip")))
				.build();

		this.addRenderableWidget(config);
		this.addRenderableWidget(recordings);
	}
}
