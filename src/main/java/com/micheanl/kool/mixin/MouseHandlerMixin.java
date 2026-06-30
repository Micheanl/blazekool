package com.micheanl.kool.mixin;

import com.micheanl.kool.integration.kool.KoolInputBridge;
import com.micheanl.kool.integration.kool.KoolWindowBridge;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.nio.file.Path;
import java.util.List;

@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
	@Inject(method = "onMove", at = @At("TAIL"))
	private void blazekool$onMove(long handle, double x, double y, CallbackInfo callbackInfo) {
		KoolInputBridge.handleMouseMove(x, y, 1.0f);
	}

	@Inject(method = "onButton", at = @At("TAIL"))
	private void blazekool$onButton(long handle, MouseButtonInfo buttonInfo, int action, CallbackInfo callbackInfo) {
		KoolInputBridge.handleMouseButton(buttonInfo.button(), action);
	}

	@Inject(method = "onScroll", at = @At("TAIL"))
	private void blazekool$onScroll(long handle, double xOffset, double yOffset, CallbackInfo callbackInfo) {
		KoolInputBridge.handleMouseScroll(xOffset, yOffset);
	}

	@Inject(method = "onDrop", at = @At("TAIL"))
	private void blazekool$onDrop(long handle, List<Path> files, int failedCount, CallbackInfo callbackInfo) {
		KoolWindowBridge.handleFileDrop(files);
	}
}
