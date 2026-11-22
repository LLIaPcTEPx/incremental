package ru.incremental.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import ru.incremental.tas.TasMotion;

/**
 * Captures the camera motion accumulated per tick while recording, so it can be replayed later.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Redirect(method = "turnPlayer", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
	private void incremental$captureTurn(LocalPlayer player, double dx, double dy) {
		TasMotion.onMouseTurn(dx, dy);
		player.turn(dx, dy);
	}
}
