package ru.incremental.mixin;

import net.minecraft.client.Minecraft;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import ru.incremental.tas.TasController;

/**
 * Lets the client tick faster than 20 t/s while a recording is replayed.
 *
 * <p>Vanilla derives the client tick length from the client level's tick rate manager but never goes
 * below 50 ms per tick, so a replay above 20 t/s would make the server run ahead of the client and
 * rubber-band the player. The configured playback speed is applied here instead.</p>
 */
@Mixin(Minecraft.class)
public abstract class ClientTickRateMixin {
	@Inject(method = "getTickTargetMillis", at = @At("HEAD"), cancellable = true)
	private void incremental$overrideTickTarget(float defaultTickTargetMillis, CallbackInfoReturnable<Float> cir) {
		float target = TasController.clientTickTargetMillis();
		if (target > 0.0F) {
			// Leave a small safety margin so the server keeps up even when a tick overruns.
			cir.setReturnValue(Math.max(1.0F, target));
		}
	}
}
