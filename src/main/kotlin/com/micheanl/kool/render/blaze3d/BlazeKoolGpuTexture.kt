package com.micheanl.kool.render.blaze3d

import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.backend.GpuTexture
import java.util.concurrent.atomic.AtomicBoolean

class BlazeKoolGpuTexture(
	override val width: Int,
	override val height: Int,
	override val depth: Int,
	val imageData: ImageData,
	val runtimeTexture: BlazeKoolRuntimeTexture,
	private val releaseAction: (BlazeKoolRuntimeTexture) -> Unit
) : GpuTexture {
	private val released = AtomicBoolean(false)

	override val isReleased: Boolean
		get() = released.get()

	override fun release() {
		if (released.compareAndSet(false, true)) {
			releaseAction(runtimeTexture)
		}
	}
}
