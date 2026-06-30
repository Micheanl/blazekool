package com.micheanl.kool.mixin;

import com.micheanl.kool.integration.kool.KoolInputBridge;
import com.mojang.blaze3d.platform.Window;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public abstract class WindowMixin {
	@Inject(method = "onEnter", at = @At("TAIL"))
	private void blazekool$onCursorEnter(long handle, boolean entered, CallbackInfo callbackInfo) {
		if (!entered) {
			KoolInputBridge.handleMouseExit();
		}
	}
}
