package ru.incremental.mixin;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasController;

/**
 * The player tick starts here, before vanilla reads the input in {@code aiStep}. This is where a
 * frame is recorded, or where the next recorded frame is pushed into the input handler.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void incremental$tasInput(CallbackInfo ci) {
		TasController.onPlayerInputTick();
	}
}
