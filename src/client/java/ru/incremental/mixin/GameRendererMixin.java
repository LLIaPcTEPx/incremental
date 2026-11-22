package ru.incremental.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.server.IntegratedServer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasController;
import ru.incremental.tas.TasStepKeys;

/**
 * One poll of the TAS hotkeys per rendered frame.
 *
 * <p>Rendering keeps happening while the world is frozen or a menu is open, which is exactly when
 * the hotkeys have to work — the client tick alone is not enough because a frozen server does not
 * produce game ticks for the keys to be read from.</p>
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Inject(method = "render", at = @At("HEAD"))
	private void incremental$pollHotkeys(DeltaTracker deltaTracker, boolean renderLevel, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return;
		}
		TasStepKeys.poll(client);
		IntegratedServer server = client.getSingleplayerServer();
		if (server != null && server.isPublished()) {
			// Publishing to LAN makes the world shared, where freezing would affect other players.
			return;
		}
		TasController.onRenderFrame(client);
	}
}
