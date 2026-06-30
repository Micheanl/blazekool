package com.micheanl.kool.render.blaze3d

import com.mojang.blaze3d.buffers.GpuBuffer
import de.fabmax.kool.pipeline.GpuBufferImpl
import de.fabmax.kool.util.Buffer
import java.util.concurrent.atomic.AtomicBoolean

class BlazeKoolGpuBuffer(
	var byteSize: Int,
	var mirrorData: Buffer?,
	var minecraftBuffer: GpuBuffer?
) : GpuBufferImpl {
	private val released = AtomicBoolean(false)

	override val isReleased: Boolean
		get() = released.get()

	override fun release() {
		if (released.compareAndSet(false, true)) {
			minecraftBuffer?.close()
			minecraftBuffer = null
			mirrorData = null
			byteSize = 0
		}
	}
}
