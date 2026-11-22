package ru.incremental.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
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
 * <p>The vanilla menu places its own grid at a quarter of the screen height; the two buttons are put
 * directly underneath the last vanilla button, so they never overlap no matter the GUI scale or the
 * window size.</p>
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

		int bottom = 0;
		for (Object child : this.children()) {
			if (child instanceof AbstractWidget widget) {
				bottom = Math.max(bottom, widget.getY() + widget.getHeight());
			}
		}
		int y = Math.min(bottom + 8, this.height - 28);

		int buttonWidth = 98;
		int gap = 8;
		int x = this.width / 2 - (buttonWidth * 2 + gap) / 2;

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
