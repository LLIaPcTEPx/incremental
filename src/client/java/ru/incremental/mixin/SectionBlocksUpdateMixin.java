package ru.incremental.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import ru.incremental.tas.TasBlocks;

/**
 * Captures the block states a section update is about to replace, while a take is recording.
 *
 * <p>The client is the right place to observe this: the server sends one packet per changed block
 * (single update or a section batch) whatever caused it — the player, an explosion, a piston or a
 * fluid. Reading the state before the packet is applied is exactly the "state before the first
 * change" that a replay restores.</p>
 */
@Mixin(ClientPacketListener.class)
public abstract class SectionBlocksUpdateMixin {
	@Inject(method = "handleChunkBlocksUpdate", at = @At("HEAD"))
	private void incremental$captureBeforeSectionUpdate(ClientboundSectionBlocksUpdatePacket packet, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (client.level == null || !TasBlocks.isCapturing()) {
			return;
		}
		packet.runUpdates((pos, state) -> {
			BlockState before = client.level.getBlockState((BlockPos) pos);
			if (before != state) {
				TasBlocks.note((BlockPos) pos, before);
			}
		});
	}
}
