package com.micheanl.kool.render.blaze3d

import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.ClearColor
import de.fabmax.kool.pipeline.ClearColorFill
import de.fabmax.kool.pipeline.ComputePassImpl
import de.fabmax.kool.pipeline.ImageDataCube
import de.fabmax.kool.pipeline.OffscreenPass2d
import de.fabmax.kool.pipeline.OffscreenPass2dImpl
import de.fabmax.kool.pipeline.OffscreenPassCube
import de.fabmax.kool.pipeline.OffscreenPassCubeImpl
import de.fabmax.kool.pipeline.RenderPassDepthTextureAttachment
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.TextureCube
import de.fabmax.kool.pipeline.isByte
import de.fabmax.kool.pipeline.isF16
import de.fabmax.kool.pipeline.isF32
import de.fabmax.kool.pipeline.isI32
import de.fabmax.kool.pipeline.isU32
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.Int32Buffer
import de.fabmax.kool.util.Uint8Buffer
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
			attachment.texture.uploadLazy(clearTexture2d(width, height, attachment.texture.format, attachment.clearColor.clearColorOrBlack()))
			index++
		}
		val depthAttachment = parentPass.depthAttachment as? RenderPassDepthTextureAttachment<*>
		val depthTexture = depthAttachment?.texture as? Texture2d
		if (depthTexture != null) {
			depthTexture.uploadLazy(clearTexture2d(width, height, depthTexture.format, Color.WHITE))
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
			attachment.texture.uploadLazy(clearCubeTexture(width, height, attachment.texture.format, attachment.clearColor.clearColorOrBlack()))
			index++
		}
		val depthAttachment = parentPass.depthAttachment as? RenderPassDepthTextureAttachment<*>
		val depthTexture = depthAttachment?.texture as? TextureCube
		if (depthTexture != null) {
			depthTexture.uploadLazy(clearCubeTexture(width, height, depthTexture.format, Color.WHITE))
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

private fun clearTexture2d(width: Int, height: Int, format: TexFormat, color: Color): BufferedImageData2d {
	return BufferedImageData2d(clearBuffer(width * height, format, color), width, height, format)
}

private fun clearCubeTexture(width: Int, height: Int, format: TexFormat, color: Color): ImageDataCube {
	return ImageDataCube(
		negX = clearTexture2d(width, height, format, color),
		posX = clearTexture2d(width, height, format, color),
		negY = clearTexture2d(width, height, format, color),
		posY = clearTexture2d(width, height, format, color),
		negZ = clearTexture2d(width, height, format, color),
		posZ = clearTexture2d(width, height, format, color)
	)
}

private fun clearBuffer(pixelCount: Int, format: TexFormat, color: Color) = when {
	format.isByte -> {
		val buffer = Uint8Buffer(pixelCount * format.channels)
		var index = 0
		while (index < pixelCount) {
			buffer.put((color.r * 255.0f).toInt().coerceIn(0, 255).toByte())
			if (format.channels > 1) {
				buffer.put((color.g * 255.0f).toInt().coerceIn(0, 255).toByte())
			}
			if (format.channels > 2) {
				buffer.put((color.b * 255.0f).toInt().coerceIn(0, 255).toByte())
			}
			if (format.channels > 3) {
				buffer.put((color.a * 255.0f).toInt().coerceIn(0, 255).toByte())
			}
			index++
		}
		buffer
	}
	format.isF16 || format.isF32 -> {
		val buffer = Float32Buffer(pixelCount * format.channels)
		var index = 0
		while (index < pixelCount) {
			buffer.put(color.r)
			if (format.channels > 1) {
				buffer.put(color.g)
			}
			if (format.channels > 2) {
				buffer.put(color.b)
			}
			if (format.channels > 3) {
				buffer.put(color.a)
			}
			index++
		}
		buffer
	}
	format.isI32 || format.isU32 -> {
		val buffer = Int32Buffer(pixelCount * format.channels)
		var index = 0
		while (index < pixelCount) {
			buffer.put((color.r * 255.0f).toInt())
			if (format.channels > 1) {
				buffer.put((color.g * 255.0f).toInt())
			}
			if (format.channels > 2) {
				buffer.put((color.b * 255.0f).toInt())
			}
			if (format.channels > 3) {
				buffer.put((color.a * 255.0f).toInt())
			}
			index++
		}
		buffer
	}
	else -> Uint8Buffer(pixelCount * format.channels)
}

private fun ClearColor.clearColorOrBlack(): Color {
	return if (this is ClearColorFill) {
		clearColor
	} else {
		Color.BLACK
	}
}
