package com.micheanl.kool.render.blaze3d

import de.fabmax.kool.pipeline.ClearColor
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.pipeline.ComputePassImpl
import de.fabmax.kool.pipeline.OffscreenPass2d
import de.fabmax.kool.pipeline.OffscreenPass2dImpl
import de.fabmax.kool.pipeline.OffscreenPassCube
import de.fabmax.kool.pipeline.OffscreenPassCubeImpl
import de.fabmax.kool.pipeline.RenderPassDepthTextureAttachment
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureCube
import de.fabmax.kool.util.Color
import java.util.concurrent.atomic.AtomicBoolean

class BlazeKoolOffscreenPass2dImpl(
	private val parentPass: OffscreenPass2d,
	private val resources: BlazeKoolResourceManager
) : OffscreenPass2dImpl {
	private val released = AtomicBoolean(false)

	override val isReleased: Boolean
		get() = released.get()

	override fun applySize(width: Int, height: Int) {
		resources.markOffscreenResize(width, height, 1)
		var index = 0
		while (index < parentPass.colorAttachments.size) {
			val attachment = parentPass.colorAttachments[index]
			resources.clearTexture(attachment.texture, width, height, 1, attachment.clearColor.clearColorOrBlack())
			index++
		}
		val depthAttachment = parentPass.depthAttachment as? RenderPassDepthTextureAttachment<*>
		val depthTexture = depthAttachment?.texture as? Texture2d
		if (depthTexture != null) {
			resources.clearTexture(depthTexture, width, height, 1, Color.WHITE)
		}
	}

	override fun release() {
		released.set(true)
	}
}

class BlazeKoolOffscreenPassCubeImpl(
	private val parentPass: OffscreenPassCube,
	private val resources: BlazeKoolResourceManager
) : OffscreenPassCubeImpl {
	private val released = AtomicBoolean(false)

	override val isReleased: Boolean
		get() = released.get()

	override fun applySize(width: Int, height: Int) {
		resources.markOffscreenResize(width, height, 6)
		var index = 0
		while (index < parentPass.colorAttachments.size) {
			val attachment = parentPass.colorAttachments[index]
			resources.clearTexture(attachment.texture, width, height, 6, attachment.clearColor.clearColorOrBlack())
			index++
		}
		val depthAttachment = parentPass.depthAttachment as? RenderPassDepthTextureAttachment<*>
		val depthTexture = depthAttachment?.texture as? TextureCube
		if (depthTexture != null) {
			resources.clearTexture(depthTexture, width, height, 6, Color.WHITE)
		}
	}

	override fun release() {
		released.set(true)
	}
}

class BlazeKoolComputePassImpl : ComputePassImpl {
	private val released = AtomicBoolean(false)
	private val dispatches = ArrayList<BlazeKoolComputeDispatch>(16)

	override val isReleased: Boolean
		get() = released.get()

	fun replaceDispatches(nextDispatches: List<BlazeKoolComputeDispatch>) {
		dispatches.clear()
		dispatches.addAll(nextDispatches)
	}

	override fun release() {
		if (released.compareAndSet(false, true)) {
			dispatches.clear()
		}
	}
}

data class BlazeKoolComputeDispatch(
	val name: String,
	val groupsX: Int,
	val groupsY: Int,
	val groupsZ: Int
)

private fun ClearColor.clearColorOrBlack(): Color {
	return if (this is ClearColorFill) {
		clearColor
	} else {
		Color.BLACK
	}
}
