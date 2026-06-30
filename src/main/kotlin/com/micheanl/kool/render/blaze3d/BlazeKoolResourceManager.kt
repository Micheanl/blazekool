package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.BlazeKool
import com.mojang.blaze3d.buffers.GpuBuffer as MinecraftGpuBuffer
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import de.fabmax.kool.pipeline.BufferedImageData1d
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.BufferedImageData3d
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.GpuType
import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.ImageData1d
import de.fabmax.kool.pipeline.ImageData2d
import de.fabmax.kool.pipeline.ImageData2dArray
import de.fabmax.kool.pipeline.ImageData3d
import de.fabmax.kool.pipeline.ImageDataCube
import de.fabmax.kool.pipeline.ImageDataCubeArray
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.Texture1d
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.Texture2dArray
import de.fabmax.kool.pipeline.Texture3d
import de.fabmax.kool.pipeline.TextureCube
import de.fabmax.kool.pipeline.TextureCubeArray
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
import de.fabmax.kool.util.StructBuffer
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
	private val runtimeSlices = IdentityHashMap<Texture<*>, MutableMap<BlazeKoolTextureSlice, BlazeKoolRuntimeTexture>>()
	private val storedSlices = IdentityHashMap<Texture<*>, MutableMap<BlazeKoolTextureSlice, ImageData>>()
	private var lastOffscreenWidth = 1
	private var lastOffscreenHeight = 1
	private var lastOffscreenLayers = 1

	fun textureLocation(texture: Texture<*>?): Identifier? {
		return textureLocation(texture, BlazeKoolTextureSlice.DEFAULT)
	}

	fun textureView(texture: Texture<*>?, slice: BlazeKoolTextureSlice): GpuTextureView? {
		val location = textureLocation(texture, slice) ?: return null
		return Minecraft.getInstance().textureManager.getTexture(location).textureView
	}

	fun textureLocation(texture: Texture<*>?, slice: BlazeKoolTextureSlice): Identifier? {
		if (texture == null) {
			return null
		}
		val gpuTexture = texture.gpuTexture as? BlazeKoolGpuTexture ?: return null
		return if (slice == BlazeKoolTextureSlice.DEFAULT) {
			gpuTexture.runtimeTexture.location
		} else {
			runtimeTexture(texture, slice, gpuTexture.imageData).location
		}
	}

	fun uploadTexture(texture: Texture<*>) {
		val imageData = texture.uploadData ?: return
		replaceTextureData(texture, imageData)
	}

	fun ensureTextureResource(texture: Texture<*>) {
		if (texture.uploadData != null) {
			uploadTexture(texture)
			return
		}
		val gpuTexture = texture.gpuTexture as? BlazeKoolGpuTexture
		if (
			gpuTexture == null ||
			gpuTexture.width != texture.width.coerceAtLeast(1) ||
			gpuTexture.height != texture.height.coerceAtLeast(1) ||
			gpuTexture.depth != texture.depth.coerceAtLeast(1)
		) {
			replaceTextureData(texture, generatedImageData(texture))
		}
	}

	fun replaceTextureData(texture: Texture<*>, imageData: ImageData) {
		val compatibleImageData = compatibleImageData(texture, imageData)
		texture.gpuTexture = uploadImage(texture, compatibleImageData)
		texture.uploadData = null
	}

	fun replaceTextureSliceData(texture: Texture<*>, slice: BlazeKoolTextureSlice, imageData: ImageData) {
		if (slice == BlazeKoolTextureSlice.DEFAULT) {
			replaceTextureData(texture, imageData)
			return
		}
		ensureTextureResource(texture)
		val image2d = firstRenderableImage(imageData) ?: generatedPlaceholder(imageData)
		val storedImage = BufferedImageData2d(copyBuffer(image2d.data), image2d.width, image2d.height, image2d.format)
		storedSlices.getOrPut(texture) { HashMap() }[slice] = storedImage
		releaseRuntimeSlice(texture, slice)
	}

	fun clearTexture(texture: Texture<*>, width: Int, height: Int, depth: Int, color: Color) {
		replaceTextureData(texture, clearImageData(texture, width, height, depth, color))
	}

	fun copyTextureData(source: Texture<*>, target: Texture<*>) {
		val imageData = downloadTextureData(source) ?: generatedImageData(target)
		replaceTextureData(target, imageData)
	}

	fun downloadTextureData(texture: Texture<*>): ImageData? {
		val pending = texture.uploadData
		if (pending != null) {
			return copyImageData(pending)
		}
		val gpuTexture = texture.gpuTexture as? BlazeKoolGpuTexture
		return if (gpuTexture != null) {
			copyImageData(gpuTexture.imageData)
		} else {
			copyImageData(generatedImageData(texture))
		}
	}

	fun fallbackTextureData(texture: Texture<*>): ImageData {
		return copyImageData(generatedImageData(texture))
	}

	fun readTextureData(texture: Texture<*>): ImageData {
		return downloadTextureData(texture) ?: fallbackTextureData(texture)
	}

	fun readTextureData(texture: Texture<*>, slice: BlazeKoolTextureSlice): ImageData {
		val stored = storedSlices[texture]?.get(slice)
		if (stored != null) {
			return copyImageData(stored)
		}
		val imageData = readTextureData(texture)
		val image2d = renderableImage(imageData, slice)
		return if (image2d != null) {
			copyImageData(mipImage(image2d, slice.mipLevel))
		} else {
			fallbackTextureData(texture)
		}
	}

	fun readTextureImage2d(texture: Texture<*>, slice: BlazeKoolTextureSlice = BlazeKoolTextureSlice.DEFAULT): BufferedImageData2d {
		val imageData = readTextureData(texture, slice)
		return renderableImage(imageData, BlazeKoolTextureSlice.DEFAULT)?.let { copyImageData(it) as BufferedImageData2d } ?: generatedPlaceholder(imageData)
	}

	fun updateBuffer(buffer: GpuBuffer) {
		val uploadData = buffer.uploadData ?: return
		replaceBufferData(buffer, uploadData)
	}

	fun replaceBufferData(buffer: GpuBuffer, data: Buffer) {
		writeBufferData(buffer, data)
		buffer.uploadData = null
	}

	fun readBufferData(buffer: GpuBuffer): Buffer {
		if (buffer.uploadData != null) {
			updateBuffer(buffer)
		}
		val gpuBuffer = buffer.gpuBuffer as? BlazeKoolGpuBuffer
		val mirrorData = gpuBuffer?.mirrorData ?: generatedBufferData(buffer)
		return copyBuffer(mirrorData)
	}

	fun uniformBuffer(name: String, data: StructBuffer<*>): MinecraftGpuBuffer? {
		val byteData = toByteBuffer(data.buffer)
		return RenderSystem.tryGetDevice()?.createBuffer(
			{ name },
			MinecraftGpuBuffer.USAGE_UNIFORM or MinecraftGpuBuffer.USAGE_COPY_DST,
			byteData
		)
	}

	private fun writeBufferData(buffer: GpuBuffer, data: Buffer) {
		val mirror = copyBuffer(data)
		val byteData = toByteBuffer(data)
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
	}

	fun downloadBuffer(buffer: GpuBuffer, resultBuffer: Buffer): Boolean {
		if (buffer.uploadData != null) {
			updateBuffer(buffer)
		}
		val gpuBuffer = buffer.gpuBuffer as? BlazeKoolGpuBuffer
		val mirrorData = gpuBuffer?.mirrorData ?: generatedBufferData(buffer)
		return copyBufferData(mirrorData, resultBuffer)
	}

	fun clearBufferData(buffer: Buffer) {
		when (buffer) {
			is Uint8Buffer -> {
				var index = 0
				while (index < buffer.capacity) {
					buffer[index] = 0u
					index++
				}
				buffer.position = buffer.capacity
			}
			is Float32Buffer -> {
				var index = 0
				while (index < buffer.capacity) {
					buffer[index] = 0.0f
					index++
				}
				buffer.position = buffer.capacity
			}
			is Int32Buffer -> {
				var index = 0
				while (index < buffer.capacity) {
					buffer[index] = 0
					index++
				}
				buffer.position = buffer.capacity
			}
			is MixedBuffer -> {
				var index = 0
				while (index < buffer.capacity) {
					buffer.setInt8(index, 0)
					index++
				}
				buffer.position = buffer.capacity
			}
		}
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
		val sliceIterator = runtimeSlices.values.iterator()
		while (sliceIterator.hasNext()) {
			val slices = sliceIterator.next()
			val textureIterator = slices.values.iterator()
			while (textureIterator.hasNext()) {
				releaseTexture(textureIterator.next())
			}
			slices.clear()
		}
		uploadedTextures.clear()
		runtimeSlices.clear()
		storedSlices.clear()
	}

	private fun uploadImage(texture: Texture<*>, imageData: ImageData): GpuTexture {
		val existing = uploadedTextures.remove(texture)
		existing?.release()
		releaseRuntimeSlices(texture)
		storedSlices.remove(texture)
		val storedImageData = copyImageData(imageData)
		val runtimeTexture = uploadRuntimeTexture(texture, storedImageData, BlazeKoolTextureSlice.DEFAULT)
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
		return clearImageData(texture, width, height, depth, Color.BLACK)
	}

	private fun runtimeTexture(texture: Texture<*>, slice: BlazeKoolTextureSlice, imageData: ImageData): BlazeKoolRuntimeTexture {
		val slices = runtimeSlices.getOrPut(texture) { HashMap() }
		return slices.getOrPut(slice) {
			uploadRuntimeTexture(texture, imageData, slice)
		}
	}

	private fun uploadRuntimeTexture(texture: Texture<*>, imageData: ImageData, slice: BlazeKoolTextureSlice): BlazeKoolRuntimeTexture {
		val stored = storedSlices[texture]?.get(slice)
		if (stored != null) {
			val storedImage = renderableImage(stored, BlazeKoolTextureSlice.DEFAULT) ?: generatedPlaceholder(stored)
			return uploadBufferedImage2d(texture, storedImage)
		}
		val image2d = renderableImage(imageData, slice) ?: generatedPlaceholder(imageData)
		return uploadBufferedImage2d(texture, mipImage(image2d, slice.mipLevel))
	}

	private fun firstRenderableImage(imageData: ImageData): BufferedImageData2d? {
		return renderableImage(imageData, BlazeKoolTextureSlice.DEFAULT)
	}

	private fun renderableImage(imageData: ImageData, slice: BlazeKoolTextureSlice): BufferedImageData2d? {
		return when (imageData) {
			is BufferedImageData1d -> BufferedImageData2d(copyBuffer(imageData.data), imageData.width, 1, imageData.format)
			is BufferedImageData2d -> imageData
			is BufferedImageData3d -> extractLayer(imageData, slice.layer)
			is ImageData2dArray -> imageData.images.getOrNull(slice.layer)?.let { renderableImage(it, BlazeKoolTextureSlice.DEFAULT) }
				?: imageData.images.firstOrNull()?.let { renderableImage(it, BlazeKoolTextureSlice.DEFAULT) }
			is ImageDataCube -> cubeFaceImage(imageData, slice.face)
			is ImageDataCubeArray -> {
				val cube = imageData.cubes.getOrNull(slice.layer) ?: imageData.cubes.firstOrNull()
				cube?.let { cubeFaceImage(it, slice.face) }
			}
			else -> null
		}
	}

	private fun cubeFaceImage(imageData: ImageDataCube, face: Int): BufferedImageData2d? {
		return when (face.coerceIn(0, 5)) {
			0 -> renderableImage(imageData.negX, BlazeKoolTextureSlice.DEFAULT)
			1 -> renderableImage(imageData.posX, BlazeKoolTextureSlice.DEFAULT)
			2 -> renderableImage(imageData.negY, BlazeKoolTextureSlice.DEFAULT)
			3 -> renderableImage(imageData.posY, BlazeKoolTextureSlice.DEFAULT)
			4 -> renderableImage(imageData.negZ, BlazeKoolTextureSlice.DEFAULT)
			else -> renderableImage(imageData.posZ, BlazeKoolTextureSlice.DEFAULT)
		}
	}

	private fun generatedPlaceholder(imageData: ImageData): BufferedImageData2d {
		val width = imageWidth(imageData).takeIf { it > 0 } ?: lastOffscreenWidth
		val height = imageHeight(imageData).takeIf { it > 0 } ?: lastOffscreenHeight
		return expandImage(BufferedImageData2d.singleColor(Color.WHITE), width, height)
	}

	private fun mipImage(imageData: BufferedImageData2d, mipLevel: Int): BufferedImageData2d {
		if (mipLevel <= 0) {
			return imageData
		}
		val width = (imageData.width shr mipLevel).coerceAtLeast(1)
		val height = (imageData.height shr mipLevel).coerceAtLeast(1)
		if (width == imageData.width && height == imageData.height) {
			return imageData
		}
		val target = ImageData.createBuffer(imageData.format, width, height)
		var y = 0
		while (y < height) {
			var x = 0
			while (x < width) {
				copyMipPixel(imageData, target, x, y, width, height)
				x++
			}
			y++
		}
		return BufferedImageData2d(target, width, height, imageData.format)
	}

	private fun copyMipPixel(source: BufferedImageData2d, target: Buffer, x: Int, y: Int, width: Int, height: Int) {
		val sourceX = ((x + 0.5f) * source.width / width).toInt().coerceIn(0, source.width - 1)
		val sourceY = ((y + 0.5f) * source.height / height).toInt().coerceIn(0, source.height - 1)
		val sourceBase = (sourceY * source.width + sourceX) * source.format.channels
		val sourceData = source.data
		when {
			sourceData is Uint8Buffer && target is Uint8Buffer -> copyPixel(sourceData, target, source.format, sourceBase)
			sourceData is Float32Buffer && target is Float32Buffer -> copyFloatPixel(sourceData, target, source.format, sourceBase)
			sourceData is Int32Buffer && target is Int32Buffer -> copyIntPixel(sourceData, target, source.format, sourceBase)
			sourceData is MixedBuffer && target is MixedBuffer -> copyMixedPixel(sourceData, target, source.format, sourceBase)
		}
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

	private fun copyFloatPixel(source: Float32Buffer, target: Float32Buffer, format: TexFormat, sourceBase: Int) {
		var channel = 0
		while (channel < format.channels) {
			target.put(source[sourceBase + channel])
			channel++
		}
	}

	private fun copyIntPixel(source: Int32Buffer, target: Int32Buffer, format: TexFormat, sourceBase: Int) {
		var channel = 0
		while (channel < format.channels) {
			target.put(source[sourceBase + channel])
			channel++
		}
	}

	private fun copyMixedPixel(source: MixedBuffer, target: MixedBuffer, format: TexFormat, sourceBase: Int) {
		var channel = 0
		while (channel < format.channels) {
			target.putInt8(source.getInt8(sourceBase + channel))
			channel++
		}
	}

	private fun copyImageData(imageData: ImageData): ImageData {
		return when (imageData) {
			is BufferedImageData1d -> BufferedImageData1d(copyBuffer(imageData.data), imageData.width, imageData.format, imageData.id)
			is BufferedImageData2d -> BufferedImageData2d(copyBuffer(imageData.data), imageData.width, imageData.height, imageData.format, imageData.id)
			is BufferedImageData3d -> BufferedImageData3d(copyBuffer(imageData.data), imageData.width, imageData.height, imageData.depth, imageData.format, imageData.id)
			is ImageData2dArray -> ImageData2dArray(imageData.images.map { copyImageData2d(it) }, imageData.id)
			is ImageData1d -> imageData
			is ImageData2d -> imageData
			is ImageData3d -> imageData
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

	private fun compatibleImageData(texture: Texture<*>, imageData: ImageData): ImageData {
		if (imageData.format != texture.format) {
			return generatedImageData(texture)
		}
		return when (texture) {
			is Texture1d -> compatibleImageData1d(texture, imageData)
			is Texture2d -> compatibleImageData2d(texture, imageData)
			is Texture2dArray -> compatibleImageData3d(texture, imageData)
			is Texture3d -> compatibleImageData3d(texture, imageData)
			is TextureCube -> compatibleImageDataCube(texture, imageData)
			is TextureCubeArray -> compatibleImageDataCubeArray(texture, imageData)
			else -> copyImageData(imageData)
		}
	}

	private fun compatibleImageData1d(texture: Texture<*>, imageData: ImageData): ImageData {
		return when (imageData) {
			is ImageData1d -> copyImageData(imageData)
			is ImageData2d -> firstRenderableImage(imageData)?.let { collapseTo1d(it) } ?: generatedImageData(texture)
			else -> generatedImageData(texture)
		}
	}

	private fun compatibleImageData2d(texture: Texture<*>, imageData: ImageData): ImageData {
		return when (imageData) {
			is ImageData2d -> copyImageData(imageData)
			else -> firstRenderableImage(imageData)?.let { copyImageData(it) } ?: generatedImageData(texture)
		}
	}

	private fun compatibleImageData3d(texture: Texture<*>, imageData: ImageData): ImageData {
		return if (imageData is ImageData3d) {
			copyImageData(imageData)
		} else {
			generatedImageData(texture)
		}
	}

	private fun compatibleImageDataCube(texture: Texture<*>, imageData: ImageData): ImageData {
		return when (imageData) {
			is ImageDataCube -> copyImageData(imageData)
			is ImageData2d -> {
				val face = copyImageData2d(imageData)
				ImageDataCube(face, copyImageData2d(face), copyImageData2d(face), copyImageData2d(face), copyImageData2d(face), copyImageData2d(face))
			}
			else -> generatedImageData(texture)
		}
	}

	private fun compatibleImageDataCubeArray(texture: Texture<*>, imageData: ImageData): ImageData {
		return when (imageData) {
			is ImageDataCubeArray -> copyImageData(imageData)
			is ImageDataCube -> ImageDataCubeArray(listOf(copyImageDataCube(imageData)))
			else -> generatedImageData(texture)
		}
	}

	private fun clearImageData(texture: Texture<*>, width: Int, height: Int, depth: Int, color: Color): ImageData {
		val safeWidth = width.coerceAtLeast(1)
		val safeHeight = height.coerceAtLeast(1)
		val safeDepth = depth.coerceAtLeast(1)
		return when (texture) {
			is Texture1d -> BufferedImageData1d(clearBuffer(safeWidth, texture.format, color), safeWidth, texture.format)
			is Texture2d -> BufferedImageData2d(clearBuffer(safeWidth * safeHeight, texture.format, color), safeWidth, safeHeight, texture.format)
			is Texture2dArray -> BufferedImageData3d(clearBuffer(safeWidth * safeHeight * safeDepth, texture.format, color), safeWidth, safeHeight, safeDepth, texture.format)
			is Texture3d -> BufferedImageData3d(clearBuffer(safeWidth * safeHeight * safeDepth, texture.format, color), safeWidth, safeHeight, safeDepth, texture.format)
			is TextureCube -> clearCubeImageData(safeWidth, safeHeight, texture.format, color)
			is TextureCubeArray -> ImageDataCubeArray(List((safeDepth / 6).coerceAtLeast(1)) { clearCubeImageData(safeWidth, safeHeight, texture.format, color) })
			else -> BufferedImageData2d(clearBuffer(safeWidth * safeHeight, texture.format, color), safeWidth, safeHeight, texture.format)
		}
	}

	private fun clearCubeImageData(width: Int, height: Int, format: TexFormat, color: Color): ImageDataCube {
		return ImageDataCube(
			negX = BufferedImageData2d(clearBuffer(width * height, format, color), width, height, format),
			posX = BufferedImageData2d(clearBuffer(width * height, format, color), width, height, format),
			negY = BufferedImageData2d(clearBuffer(width * height, format, color), width, height, format),
			posY = BufferedImageData2d(clearBuffer(width * height, format, color), width, height, format),
			negZ = BufferedImageData2d(clearBuffer(width * height, format, color), width, height, format),
			posZ = BufferedImageData2d(clearBuffer(width * height, format, color), width, height, format)
		)
	}

	private fun collapseTo1d(imageData: BufferedImageData2d): BufferedImageData1d {
		val source = imageData.data
		val length = imageData.width * imageData.format.channels
		return BufferedImageData1d(copyBufferRange(source, 0, length), imageData.width, imageData.format)
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

	private fun generatedBufferData(buffer: GpuBuffer): Buffer {
		val elementCount = buffer.size.coerceAtLeast(0)
		return when (buffer.type) {
			GpuType.Float1 -> Float32Buffer(elementCount)
			GpuType.Float2 -> Float32Buffer(elementCount * 2)
			GpuType.Float3 -> Float32Buffer(elementCount * 4)
			GpuType.Float4 -> Float32Buffer(elementCount * 4)
			GpuType.Mat2 -> Float32Buffer(elementCount * 8)
			GpuType.Mat3 -> Float32Buffer(elementCount * 12)
			GpuType.Mat4 -> Float32Buffer(elementCount * 16)
			GpuType.Int1 -> Int32Buffer(elementCount)
			GpuType.Int2 -> Int32Buffer(elementCount * 2)
			GpuType.Int3 -> Int32Buffer(elementCount * 4)
			GpuType.Int4 -> Int32Buffer(elementCount * 4)
			GpuType.Uint1 -> Int32Buffer(elementCount)
			GpuType.Uint2 -> Int32Buffer(elementCount * 2)
			GpuType.Uint3 -> Int32Buffer(elementCount * 4)
			GpuType.Uint4 -> Int32Buffer(elementCount * 4)
			GpuType.Bool1 -> Int32Buffer(elementCount)
			GpuType.Bool2 -> Int32Buffer(elementCount * 2)
			GpuType.Bool3 -> Int32Buffer(elementCount * 4)
			GpuType.Bool4 -> Int32Buffer(elementCount * 4)
			is GpuType.Struct -> MixedBuffer(elementCount * buffer.type.byteSize)
		}
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

	private fun releaseRuntimeSlices(texture: Texture<*>) {
		val slices = runtimeSlices.remove(texture) ?: return
		val iterator = slices.values.iterator()
		while (iterator.hasNext()) {
			releaseTexture(iterator.next())
		}
		slices.clear()
	}

	private fun releaseRuntimeSlice(texture: Texture<*>, slice: BlazeKoolTextureSlice) {
		val slices = runtimeSlices[texture] ?: return
		val runtimeTexture = slices.remove(slice)
		if (runtimeTexture != null) {
			releaseTexture(runtimeTexture)
		}
		if (slices.isEmpty()) {
			runtimeSlices.remove(texture)
		}
	}

	private fun floatChannel(value: Float): Int = (value.coerceIn(0.0f, 1.0f) * 255.0f).toInt()

	private fun intChannel(value: Int): Int = value.coerceIn(0, 255)

	private fun argb(alpha: Int, red: Int, green: Int, blue: Int): Int {
		return alpha shl 24 or (red shl 16) or (green shl 8) or blue
	}
}

data class BlazeKoolTextureSlice(
	val mipLevel: Int = 0,
	val layer: Int = 0,
	val face: Int = 4
) {
	companion object {
		val DEFAULT: BlazeKoolTextureSlice = BlazeKoolTextureSlice()
	}
}
