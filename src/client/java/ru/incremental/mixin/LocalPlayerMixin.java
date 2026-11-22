package ru.incremental.mixin;

import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasController;

/**
 * Injects the TAS input right before the player's ai step: the frame is recorded or the next
 * recorded frame is applied to the vanilla input handler.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
	@Inject(method = "tick", at = @At("HEAD"))
	private void incremental$tasInput(CallbackInfo ci) {
		TasController.onPlayerInputTick();
	}
}
