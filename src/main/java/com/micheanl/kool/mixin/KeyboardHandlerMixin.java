package com.micheanl.kool.mixin;

import com.micheanl.kool.integration.kool.KoolInputBridge;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("TAIL"))
	private void blazekool$onKeyPress(long handle, int action, KeyEvent event, CallbackInfo callbackInfo) {
		KoolInputBridge.handleKey(event.key(), event.scancode(), action, event.modifiers());
	}

	@Inject(method = "charTyped", at = @At("TAIL"))
	private void blazekool$onCharTyped(long handle, CharacterEvent event, CallbackInfo callbackInfo) {
		KoolInputBridge.handleCharTyped(event.codepoint());
	}
}
