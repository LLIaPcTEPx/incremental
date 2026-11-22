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
	/**
	 * Rotation of the previous tick, saved before vanilla overwrites it.
	 *
	 * <p>{@code LivingEntity.tick} ends with {@code yRotO = getYRot()}, which is right for a player
	 * whose rotation only changes through mouse input between ticks. During playback the rotation is
	 * written at the start of the tick, so that assignment would erase the interpolation every tick
	 * and the camera would step instead of turning. Saving the value here and restoring it in the
	 * tail keeps the renderer's lerp alive, which is what makes the replay smooth.</p>
	 */
	private float incremental$previousYRot;
	private float incremental$previousXRot;

	@Inject(method = "tick", at = @At("HEAD"))
	private void incremental$tickStart(CallbackInfo ci) {
		LocalPlayer self = (LocalPlayer) (Object) this;
		this.incremental$previousYRot = self.yRotO;
		this.incremental$previousXRot = self.xRotO;
	}

	@Inject(method = "tick", at = @At("TAIL"))
	private void incremental$tickEnd(CallbackInfo ci) {
		if (ru.incremental.tas.TasSession.isPlaying()) {
			// Keep the previous angle for the renderer instead of the "current" one vanilla wrote.
			LocalPlayer self = (LocalPlayer) (Object) this;
			self.yRotO = this.incremental$previousYRot;
			self.xRotO = this.incremental$previousXRot;
		}
		TasController.onPlayerTickEnd();
	}
}
