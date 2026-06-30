package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.BlazeKool
import com.mojang.blaze3d.buffers.GpuBuffer as MinecraftGpuBuffer
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.pipeline.BufferedImageData1d
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.BufferedImageData3d
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.ImageData1d
import de.fabmax.kool.pipeline.ImageData2d
import de.fabmax.kool.pipeline.ImageData2dArray
import de.fabmax.kool.pipeline.ImageData3d
import de.fabmax.kool.pipeline.ImageDataCube
import de.fabmax.kool.pipeline.ImageDataCubeArray
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.backend.GpuTexture
import de.fabmax.kool.pipeline.isByte
import de.fabmax.kool.pipeline.isF16
import de.fabmax.kool.pipeline.isF32
import de.fabmax.kool.pipeline.isI32
import de.fabmax.kool.pipeline.isU32
import de.fabmax.kool.util.Buffer
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.Int32Buffer
import de.fabmax.kool.util.MixedBuffer
import de.fabmax.kool.util.Uint8Buffer
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.resources.Identifier
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicInteger

class BlazeKoolResourceManager {
	private val nextTextureId = AtomicInteger()
	private val uploadedTextures = IdentityHashMap<Texture<*>, BlazeKoolGpuTexture>()
	private var lastOffscreenWidth = 1
	private var lastOffscreenHeight = 1
	private var lastOffscreenLayers = 1

	fun textureLocation(texture: Texture<*>?): Identifier? {
		return (texture?.gpuTexture as? BlazeKoolGpuTexture)?.runtimeTexture?.location
	}

	fun uploadTexture(texture: Texture<*>) {
		val imageData = texture.uploadData ?: return
		val gpuTexture = uploadImage(texture, imageData)
		texture.gpuTexture = gpuTexture
		texture.uploadData = null
	}

	fun ensureTextureResource(texture: Texture<*>) {
		if (texture.uploadData != null) {
			uploadTexture(texture)
		} else if (texture.gpuTexture == null) {
			texture.gpuTexture = uploadImage(texture, generatedImageData(texture))
		}
	}

	fun downloadTextureData(texture: Texture<*>): ImageData? {
		val pending = texture.uploadData
		if (pending != null) {
			return copyImageData(pending)
		}
		val gpuTexture = texture.gpuTexture as? BlazeKoolGpuTexture ?: return null
		return copyImageData(gpuTexture.imageData)
	}

	fun updateBuffer(buffer: GpuBuffer) {
		val uploadData = buffer.uploadData ?: return
		val mirror = copyBuffer(uploadData)
		val byteData = toByteBuffer(uploadData)
		val byteSize = byteData.remaining()
		val current = buffer.gpuBuffer as? BlazeKoolGpuBuffer
		if (current == null || current.byteSize < byteSize || current.minecraftBuffer == null || current.minecraftBuffer?.isClosed == true) {
			current?.release()
			buffer.gpuBuffer = BlazeKoolGpuBuffer(
				byteSize = byteSize,
				mirrorData = mirror,
				minecraftBuffer = createMinecraftBuffer(buffer, byteData)
			)
		} else {
			current.byteSize = byteSize
			current.mirrorData = mirror
			current.minecraftBuffer?.let { minecraftBuffer ->
				RenderSystem.tryGetDevice()?.createCommandEncoder()?.writeToBuffer(minecraftBuffer.slice(0L, byteSize.toLong()), byteData)
			}
		}
		buffer.uploadData = null
	}

	fun downloadBuffer(buffer: GpuBuffer, resultBuffer: Buffer): Boolean {
		if (buffer.uploadData != null) {
			updateBuffer(buffer)
		}
		val gpuBuffer = buffer.gpuBuffer as? BlazeKoolGpuBuffer ?: return false
		val mirrorData = gpuBuffer.mirrorData ?: return false
		return copyBufferData(mirrorData, resultBuffer)
	}

	fun markOffscreenResize(width: Int, height: Int, layers: Int) {
		lastOffscreenWidth = width.coerceAtLeast(1)
		lastOffscreenHeight = height.coerceAtLeast(1)
		lastOffscreenLayers = layers.coerceAtLeast(1)
	}

	fun clear() {
		val iterator = uploadedTextures.values.iterator()
		while (iterator.hasNext()) {
			iterator.next().release()
		}
		uploadedTextures.clear()
	}

	private fun uploadImage(texture: Texture<*>, imageData: ImageData): GpuTexture {
		val existing = uploadedTextures.remove(texture)
		existing?.release()
		val storedImageData = copyImageData(imageData)
		val runtimeTexture = uploadRuntimeTexture(texture, storedImageData)
		return BlazeKoolGpuTexture(
			width = imageWidth(storedImageData),
			height = imageHeight(storedImageData),
			depth = imageDepth(storedImageData),
			imageData = storedImageData,
			runtimeTexture = runtimeTexture,
			releaseAction = ::releaseTexture
		).also { uploadedTextures[texture] = it }
	}

	private fun generatedImageData(texture: Texture<*>): ImageData {
		val width = texture.width.takeIf { it > 0 } ?: lastOffscreenWidth
		val height = texture.height.takeIf { it > 0 } ?: lastOffscreenHeight
		val depth = texture.depth.takeIf { it > 0 } ?: lastOffscreenLayers
		return when {
			depth > 1 -> BufferedImageData3d(clearBuffer(width * height * depth, texture.format, Color.BLACK), width, height, depth, texture.format)
			height > 1 -> BufferedImageData2d(clearBuffer(width * height, texture.format, Color.BLACK), width, height, texture.format)
			else -> BufferedImageData1d(clearBuffer(width, texture.format, Color.BLACK), width, texture.format)
		}
	}

	private fun uploadRuntimeTexture(texture: Texture<*>, imageData: ImageData): BlazeKoolRuntimeTexture {
		val image2d = firstRenderableImage(imageData) ?: generatedPlaceholder(imageData)
		return uploadBufferedImage2d(texture, image2d)
	}

	private fun firstRenderableImage(imageData: ImageData): BufferedImageData2d? {
		return when (imageData) {
			is BufferedImageData1d -> BufferedImageData2d(copyBuffer(imageData.data), imageData.width, 1, imageData.format)
			is BufferedImageData2d -> imageData
			is BufferedImageData3d -> extractLayer(imageData, 0)
			is ImageData2dArray -> imageData.images.firstOrNull()?.let { firstRenderableImage(it) }
			is ImageDataCube -> firstRenderableImage(imageData.negZ)
			is ImageDataCubeArray -> imageData.cubes.firstOrNull()?.let { firstRenderableImage(it) }
			else -> null
		}
	}

	private fun generatedPlaceholder(imageData: ImageData): BufferedImageData2d {
		val width = imageWidth(imageData).takeIf { it > 0 } ?: lastOffscreenWidth
		val height = imageHeight(imageData).takeIf { it > 0 } ?: lastOffscreenHeight
		return expandImage(BufferedImageData2d.singleColor(Color.WHITE), width, height)
	}

	private fun uploadBufferedImage2d(texture: Texture<*>, imageData: BufferedImageData2d): BlazeKoolRuntimeTexture {
		val nativeImage = toNativeImage(imageData, imageData.width, imageData.height)
		val location = BlazeKool.id("runtime/kool_texture_${nextTextureId.incrementAndGet()}")
		val dynamicTexture = DynamicTexture({ texture.name }, nativeImage)
		Minecraft.getInstance().textureManager.register(location, dynamicTexture)
		return BlazeKoolRuntimeTexture(location, imageData.width, imageData.height, 1)
	}

	private fun toNativeImage(imageData: BufferedImageData2d, width: Int, height: Int): NativeImage {
		val image = NativeImage(width, height, false)
		val data = imageData.data
		val sourceWidth = imageData.width
		val sourceHeight = imageData.height
		var y = 0
		while (y < height) {
			var x = 0
			while (x < width) {
				val sourceX = x.coerceAtMost(sourceWidth - 1)
				val sourceY = y.coerceAtMost(sourceHeight - 1)
				image.setPixel(x, y, colorAt(data, imageData.format, sourceX, sourceY, sourceWidth))
				x++
			}
			y++
		}
		return image
	}

	private fun colorAt(data: Buffer, format: TexFormat, x: Int, y: Int, width: Int): Int {
		val base = (y * width + x) * format.channels
		return when (data) {
			is Uint8Buffer -> byteColorAt(data, format, base)
			is Float32Buffer -> floatColorAt(data, format, base)
			is Int32Buffer -> intColorAt(data, format, base)
			is MixedBuffer -> mixedColorAt(data, format, base)
			else -> -1
		}
	}

	private fun byteColorAt(data: Uint8Buffer, format: TexFormat, base: Int): Int {
		val red = data[base].toInt()
		val green = if (format.channels > 1) data[base + 1].toInt() else red
		val blue = if (format.channels > 2) data[base + 2].toInt() else red
		val alpha = if (format.channels > 3) data[base + 3].toInt() else 255
		return argb(alpha, red, green, blue)
	}

	private fun floatColorAt(data: Float32Buffer, format: TexFormat, base: Int): Int {
		val red = floatChannel(data[base])
		val green = if (format.channels > 1) floatChannel(data[base + 1]) else red
		val blue = if (format.channels > 2) floatChannel(data[base + 2]) else red
		val alpha = if (format.channels > 3) floatChannel(data[base + 3]) else 255
		return argb(alpha, red, green, blue)
	}

	private fun intColorAt(data: Int32Buffer, format: TexFormat, base: Int): Int {
		val red = intChannel(data[base])
		val green = if (format.channels > 1) intChannel(data[base + 1]) else red
		val blue = if (format.channels > 2) intChannel(data[base + 2]) else red
		val alpha = if (format.channels > 3) intChannel(data[base + 3]) else 255
		return argb(alpha, red, green, blue)
	}

	private fun mixedColorAt(data: MixedBuffer, format: TexFormat, base: Int): Int {
		val red = data.getUint8(base).toInt()
		val green = if (format.channels > 1) data.getUint8(base + 1).toInt() else red
		val blue = if (format.channels > 2) data.getUint8(base + 2).toInt() else red
		val alpha = if (format.channels > 3) data.getUint8(base + 3).toInt() else 255
		return argb(alpha, red, green, blue)
	}

	private fun extractLayer(imageData: BufferedImageData3d, layer: Int): BufferedImageData2d {
		val layerSize = imageData.width * imageData.height * imageData.format.channels
		val start = layer.coerceIn(0, imageData.depth - 1) * layerSize
		return BufferedImageData2d(copyBufferRange(imageData.data, start, layerSize), imageData.width, imageData.height, imageData.format)
	}

	private fun expandImage(imageData: BufferedImageData2d, width: Int, height: Int): BufferedImageData2d {
		if (imageData.width == width && imageData.height == height) {
			return imageData
		}
		val expanded = Uint8Buffer(width * height * imageData.format.channels)
		var y = 0
		while (y < height) {
			var x = 0
			while (x < width) {
				copyPixel(imageData.data as Uint8Buffer, expanded, imageData.format, 0)
				x++
			}
			y++
		}
		return BufferedImageData2d(expanded, width, height, imageData.format)
	}

	private fun copyPixel(source: Uint8Buffer, target: Uint8Buffer, format: TexFormat, sourceBase: Int) {
		var channel = 0
		while (channel < format.channels) {
			target.put(source[sourceBase + channel])
			channel++
		}
	}

	private fun copyImageData(imageData: ImageData): ImageData {
		return when (imageData) {
			is BufferedImageData1d -> BufferedImageData1d(copyBuffer(imageData.data), imageData.width, imageData.format, imageData.id)
			is BufferedImageData2d -> BufferedImageData2d(copyBuffer(imageData.data), imageData.width, imageData.height, imageData.format, imageData.id)
			is BufferedImageData3d -> BufferedImageData3d(copyBuffer(imageData.data), imageData.width, imageData.height, imageData.depth, imageData.format, imageData.id)
			is ImageData1d -> imageData
			is ImageData2d -> imageData
			is ImageData3d -> imageData
			is ImageData2dArray -> ImageData2dArray(imageData.images.map { copyImageData2d(it) }, imageData.id)
			is ImageDataCube -> copyImageDataCube(imageData)
			is ImageDataCubeArray -> ImageDataCubeArray(imageData.cubes.map { copyImageDataCube(it) }, imageData.id)
		}
	}

	private fun copyImageData2d(imageData: ImageData2d): ImageData2d {
		return when (imageData) {
			is BufferedImageData2d -> BufferedImageData2d(copyBuffer(imageData.data), imageData.width, imageData.height, imageData.format, imageData.id)
			else -> imageData
		}
	}

	private fun copyImageDataCube(imageData: ImageDataCube): ImageDataCube {
		return ImageDataCube(
			negX = copyImageData2d(imageData.negX),
			posX = copyImageData2d(imageData.posX),
			negY = copyImageData2d(imageData.negY),
			posY = copyImageData2d(imageData.posY),
			negZ = copyImageData2d(imageData.negZ),
			posZ = copyImageData2d(imageData.posZ),
			id = imageData.id
		)
	}

	private fun imageWidth(imageData: ImageData): Int {
		return when (imageData) {
			is ImageData1d -> imageData.width
			is ImageData2d -> imageData.width
			is ImageData3d -> imageData.width
			is ImageDataCube -> imageData.width
			is ImageDataCubeArray -> imageData.width
		}
	}

	private fun imageHeight(imageData: ImageData): Int {
		return when (imageData) {
			is ImageData1d -> 1
			is ImageData2d -> imageData.height
			is ImageData3d -> imageData.height
			is ImageDataCube -> imageData.height
			is ImageDataCubeArray -> imageData.height
		}
	}

	private fun imageDepth(imageData: ImageData): Int {
		return when (imageData) {
			is ImageData1d -> 1
			is ImageData2d -> 1
			is ImageData3d -> imageData.depth
			is ImageDataCube -> 6
			is ImageDataCubeArray -> imageData.slices * 6
		}
	}

	private fun createMinecraftBuffer(buffer: GpuBuffer, data: ByteBuffer): MinecraftGpuBuffer? {
		return RenderSystem.tryGetDevice()?.createBuffer({ buffer.name }, minecraftUsage(buffer), data)
	}

	private fun clearBuffer(pixelCount: Int, format: TexFormat, color: Color): Buffer {
		return when {
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
	}

	private fun minecraftUsage(buffer: GpuBuffer): Int {
		var usage = MinecraftGpuBuffer.USAGE_COPY_DST or MinecraftGpuBuffer.USAGE_COPY_SRC
		if (buffer.usage.isVertex || buffer.usage.isInstance) {
			usage = usage or MinecraftGpuBuffer.USAGE_VERTEX
		}
		if (buffer.usage.isIndex) {
			usage = usage or MinecraftGpuBuffer.USAGE_INDEX
		}
		return usage
	}

	private fun copyBuffer(buffer: Buffer): Buffer {
		return when (buffer) {
			is Uint8Buffer -> {
				val copy = Uint8Buffer(buffer.limit)
				var index = 0
				while (index < buffer.limit) {
					copy.put(buffer[index])
					index++
				}
				copy
			}
			is Float32Buffer -> {
				val copy = Float32Buffer(buffer.limit)
				var index = 0
				while (index < buffer.limit) {
					copy.put(buffer[index])
					index++
				}
				copy
			}
			is Int32Buffer -> {
				val copy = Int32Buffer(buffer.limit)
				var index = 0
				while (index < buffer.limit) {
					copy.put(buffer[index])
					index++
				}
				copy
			}
			is MixedBuffer -> {
				val copy = MixedBuffer(buffer.limit)
				var index = 0
				while (index < buffer.limit) {
					copy.putInt8(buffer.getInt8(index))
					index++
				}
				copy
			}
			else -> buffer
		}
	}

	private fun copyBufferRange(buffer: Buffer, start: Int, length: Int): Buffer {
		return when (buffer) {
			is Uint8Buffer -> {
				val copy = Uint8Buffer(length)
				var index = 0
				while (index < length) {
					copy.put(buffer[start + index])
					index++
				}
				copy
			}
			is Float32Buffer -> {
				val copy = Float32Buffer(length)
				var index = 0
				while (index < length) {
					copy.put(buffer[start + index])
					index++
				}
				copy
			}
			is Int32Buffer -> {
				val copy = Int32Buffer(length)
				var index = 0
				while (index < length) {
					copy.put(buffer[start + index])
					index++
				}
				copy
			}
			is MixedBuffer -> {
				val copy = MixedBuffer(length)
				var index = 0
				while (index < length) {
					copy.putInt8(buffer.getInt8(start + index))
					index++
				}
				copy
			}
			else -> buffer
		}
	}

	private fun copyBufferData(source: Buffer, target: Buffer): Boolean {
		return when {
			source is Uint8Buffer && target is Uint8Buffer -> {
				val count = minOf(source.limit, target.capacity)
				var index = 0
				while (index < count) {
					target[index] = source[index]
					index++
				}
				target.position = count
				true
			}
			source is Float32Buffer && target is Float32Buffer -> {
				val count = minOf(source.limit, target.capacity)
				var index = 0
				while (index < count) {
					target[index] = source[index]
					index++
				}
				target.position = count
				true
			}
			source is Int32Buffer && target is Int32Buffer -> {
				val count = minOf(source.limit, target.capacity)
				var index = 0
				while (index < count) {
					target[index] = source[index]
					index++
				}
				target.position = count
				true
			}
			source is MixedBuffer && target is MixedBuffer -> {
				val count = minOf(source.limit, target.capacity)
				var index = 0
				while (index < count) {
					target.setInt8(index, source.getInt8(index))
					index++
				}
				target.position = count
				true
			}
			else -> false
		}
	}

	private fun toByteBuffer(buffer: Buffer): ByteBuffer {
		val bytes = ByteBuffer.allocateDirect(byteSize(buffer)).order(ByteOrder.nativeOrder())
		when (buffer) {
			is Uint8Buffer -> {
				var index = 0
				while (index < buffer.limit) {
					bytes.put(buffer[index].toByte())
					index++
				}
			}
			is Float32Buffer -> {
				var index = 0
				while (index < buffer.limit) {
					bytes.putFloat(buffer[index])
					index++
				}
			}
			is Int32Buffer -> {
				var index = 0
				while (index < buffer.limit) {
					bytes.putInt(buffer[index])
					index++
				}
			}
			is MixedBuffer -> {
				var index = 0
				while (index < buffer.limit) {
					bytes.put(buffer.getInt8(index))
					index++
				}
			}
		}
		bytes.flip()
		return bytes
	}

	private fun byteSize(buffer: Buffer): Int {
		return when (buffer) {
			is Uint8Buffer -> buffer.limit
			is Float32Buffer -> buffer.limit * Float.SIZE_BYTES
			is Int32Buffer -> buffer.limit * Int.SIZE_BYTES
			is MixedBuffer -> buffer.limit
			else -> buffer.limit
		}
	}

	private fun releaseTexture(texture: BlazeKoolRuntimeTexture) {
		Minecraft.getInstance().textureManager.release(texture.location)
	}

	private fun floatChannel(value: Float): Int = (value.coerceIn(0.0f, 1.0f) * 255.0f).toInt()

	private fun intChannel(value: Int): Int = value.coerceIn(0, 255)

	private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
		return alpha shl 24 or (red shl 16) or (green shl 8) or blue
	}
}
