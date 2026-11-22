package ru.incremental.mixin;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasController;

/**
 * End of the player tick: vanilla has consumed this tick's input, so this is the exact state that
 * has to be written into the recording.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "tick", at = @At("TAIL"))
	private void incremental$tickEnd(CallbackInfo ci) {
		TasController.onPlayerTickEnd();
	}
}
