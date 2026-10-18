package io.github.donttemptfate.fatesflipper.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code /ff token <token>} never goes into the chat's up-arrow history (audit F-S2), so the token
 * can't be recalled or shown on stream later.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHistoryMixin {
	@Inject(method = "addRecentChat", at = @At("HEAD"), cancellable = true)
	private void fatesflipper$skipToken(String message, CallbackInfo ci) {
		if (io.github.donttemptfate.fatesflipper.core.ServerAddress.isTokenCommand(message)) ci.cancel();
	}
}
