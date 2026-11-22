package ru.incremental.mixin;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasController;
import ru.incremental.tas.TasSession;

/**
 * While a recording is replayed the camera belongs to the recording, so physical mouse movement must
 * not rotate the player.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
	private void incremental$blockManualTurn(double mousea, CallbackInfo ci) {
		if (TasSession.isPlaying()) {
			ci.cancel();
		}
	}
}
