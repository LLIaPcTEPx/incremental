package ru.incremental.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import ru.incremental.tas.TasMotion;
import ru.incremental.tas.TasSession;

/**
 * Captures the camera motion accumulated per tick while recording, and ignores the physical mouse
 * while a recording is being replayed (playback drives the camera instead).
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void incremental$captureTurn(LocalPlayer player, double dx, double dy) {
		TasMotion.onMouseTurn(dx, dy);
		if (!TasSession.isPlaying()) {
			player.turn(dx, dy);
		}
	}
}
