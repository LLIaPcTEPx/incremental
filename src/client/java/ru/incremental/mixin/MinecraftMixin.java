package ru.incremental.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasController;
import ru.incremental.tas.TasSession;

/**
 * Hooks the very beginning of the client tick. The playback frame is written here, before vanilla
 * reads the key bindings, so a recorded click is consumed in the same tick it belongs to.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void incremental$tickStart(CallbackInfo ci) {
		Minecraft client = (Minecraft) (Object) this;
		TasController.onClientTickStart();
		// Leaving the world (disconnect, quitting to title) resets everything.
		if (client.player == null && TasSession.isBusy()) {
			TasSession.worldUnloaded();
		}
	}

	@Inject(method = "setScreen", at = @At("TAIL"))
	private void incremental$screenChanged(Screen screen, CallbackInfo ci) {
		TasController.onScreenChanged((Minecraft) (Object) this, screen);
	}
}
