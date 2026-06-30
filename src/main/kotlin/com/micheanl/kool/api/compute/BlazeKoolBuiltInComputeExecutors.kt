package com.micheanl.kool.api.compute

import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.pipeline.BufferedImageData1d
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.BufferedImageData3d
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.StorageAccessType
import de.fabmax.kool.pipeline.StorageTexture
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.isByte
import de.fabmax.kool.pipeline.isF16
import de.fabmax.kool.pipeline.isF32
import de.fabmax.kool.pipeline.isI32
import de.fabmax.kool.pipeline.isU32
import de.fabmax.kool.util.Buffer
import de.fabmax.kool.util.Float1Member
import de.fabmax.kool.util.Float2Member
import de.fabmax.kool.util.Float4Member
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.Int32Buffer
import de.fabmax.kool.util.MixedBuffer
import de.fabmax.kool.util.StructMember
import de.fabmax.kool.util.Uint8Buffer
import kotlin.math.floor

object BlazeKoolBuiltInComputeExecutors {
	private val bloomDownSample = BlazeKoolComputeExecutor { context -> dispatchDownSample(context) }
	private val bloomUpSample = BlazeKoolComputeExecutor { context -> dispatchUpSample(context) }
	private val common = BlazeKoolComputeExecutor { context -> dispatchCommon(context) }

	fun executorFor(context: BlazeKoolComputeContext): BlazeKoolComputeExecutor? {
		return when (context.task.shader.name) {
			"down-sample-shader" -> bloomDownSample
			"up-sample-shader" -> bloomUpSample
			else -> common.takeIf { canDispatchCommon(context) }
		}
	}

	private fun dispatchDownSample(context: BlazeKoolComputeContext) {
		val inputBinding = textureBinding(context, "sampleInput") ?: firstTexture(context) ?: return
		val input = inputBinding.texture
		val targetBinding = storageTextureBinding(context, "downSampled") ?: firstStorageTexture(context) ?: return
		val target = targetBinding.storageTexture ?: return
		val inputImage = image2d(context.readTexture(input, inputBinding.mipLevel))
		val targetImage = storageImage2d(target, targetBinding.mipLevel)
		val threshold = uniform4(context, "threshold", FloatArray(4))
		val inputTexelSize = uniform2(context, "inputTexelSize", floatArrayOf(1.0f / inputImage.width, 1.0f / inputImage.height))
		val source = ImageSampler(inputImage)
		val targetData = PixelImage(targetImage)
		var y = 0
		while (y < targetImage.height) {
			var x = 0
			while (x < targetImage.width) {
				val sampleU = (x + 0.5f) * inputTexelSize[0] * 2.0f
				val sampleV = (y + 0.5f) * inputTexelSize[1] * 2.0f
				val rx = inputTexelSize[0]
				val ry = inputTexelSize[1]
				val rx2 = rx * 2.0f
				val ry2 = ry * 2.0f
				val weighted = FloatArray(4)
				addWeighted(weighted, source.sample(sampleU, sampleV), 0.125f)
				addWeighted(weighted, source.sample(sampleU - rx, sampleV + ry), 0.125f)
				addWeighted(weighted, source.sample(sampleU + rx, sampleV + ry), 0.125f)
				addWeighted(weighted, source.sample(sampleU - rx, sampleV - ry), 0.125f)
				addWeighted(weighted, source.sample(sampleU + rx, sampleV - ry), 0.125f)
				addWeighted(weighted, source.sample(sampleU, sampleV + ry2), 0.0625f)
				addWeighted(weighted, source.sample(sampleU - rx2, sampleV), 0.0625f)
				addWeighted(weighted, source.sample(sampleU + rx2, sampleV), 0.0625f)
				addWeighted(weighted, source.sample(sampleU, sampleV - ry2), 0.0625f)
				addWeighted(weighted, source.sample(sampleU - rx2, sampleV + ry2), 0.03125f)
				addWeighted(weighted, source.sample(sampleU + rx2, sampleV + ry2), 0.03125f)
				addWeighted(weighted, source.sample(sampleU - rx2, sampleV - ry2), 0.03125f)
				addWeighted(weighted, source.sample(sampleU + rx2, sampleV - ry2), 0.03125f)
				if (threshold[3] > 0.0f) {
					val luminance = weighted[0] * threshold[0] + weighted[1] * threshold[1] + weighted[2] * threshold[2]
					val factor = smoothStep(threshold[3], threshold[3] * 2.0f, luminance)
					weighted[0] *= factor
					weighted[1] *= factor
					weighted[2] *= factor
				}
				if (weighted[0].isFinite() && weighted[1].isFinite() && weighted[2].isFinite()) {
					targetData.set(x, y, weighted)
				}
				x++
			}
			y++
		}
		context.writeStorageTexture(target, targetBinding.mipLevel, targetImage)
	}

	private fun dispatchUpSample(context: BlazeKoolComputeContext) {
		val inputBinding = textureBinding(context, "sampleInput") ?: firstTexture(context) ?: return
		val input = inputBinding.texture
		val targetBinding = storageTextureBinding(context, "upSampled") ?: firstStorageTexture(context) ?: return
		val target = targetBinding.storageTexture ?: return
		val downSampledBinding = storageTextureBinding(context, "downSampled")
		val downSampled = downSampledBinding?.storageTexture ?: target
		val downSampledMipLevel = downSampledBinding?.mipLevel ?: targetBinding.mipLevel
		val inputImage = image2d(context.readTexture(input, inputBinding.mipLevel))
		val baseImage = image2d(context.readStorageTexture(downSampled, downSampledMipLevel))
		val targetImage = storageImage2d(target, targetBinding.mipLevel)
		val inputTexelSize = uniform2(context, "inputTexelSize", floatArrayOf(1.0f / inputImage.width, 1.0f / inputImage.height))
		val radius = uniform1(context, "radius", 2.0f)
		val outputScale = uniform1(context, "outputScale", 1.0f)
		val source = ImageSampler(inputImage)
		val base = ImageSampler(baseImage)
		val targetData = PixelImage(targetImage)
		var y = 0
		while (y < targetImage.height) {
			var x = 0
			while (x < targetImage.width) {
				val sampleU = (x + 0.5f) * inputTexelSize[0] * 0.5f
				val sampleV = (y + 0.5f) * inputTexelSize[1] * 0.5f
				val rx = inputTexelSize[0] * radius
				val ry = inputTexelSize[1] * radius
				val filtered = base.texel(
					x.coerceIn(0, baseImage.width - 1),
					y.coerceIn(0, baseImage.height - 1)
				)
				addWeighted(filtered, source.sample(sampleU, sampleV), 0.25f)
				addWeighted(filtered, source.sample(sampleU, sampleV + ry), 0.125f)
				addWeighted(filtered, source.sample(sampleU - rx, sampleV), 0.125f)
				addWeighted(filtered, source.sample(sampleU + rx, sampleV), 0.125f)
				addWeighted(filtered, source.sample(sampleU, sampleV - ry), 0.125f)
				addWeighted(filtered, source.sample(sampleU - rx, sampleV + ry), 0.0625f)
				addWeighted(filtered, source.sample(sampleU + rx, sampleV + ry), 0.0625f)
				addWeighted(filtered, source.sample(sampleU - rx, sampleV - ry), 0.0625f)
				addWeighted(filtered, source.sample(sampleU + rx, sampleV - ry), 0.0625f)
				filtered[0] *= outputScale
				filtered[1] *= outputScale
				filtered[2] *= outputScale
				targetData.set(x, y, filtered)
				x++
			}
			y++
		}
		context.writeStorageTexture(target, targetBinding.mipLevel, targetImage)
	}

	private fun canDispatchCommon(context: BlazeKoolComputeContext): Boolean {
		return isCopyOperation(context) ||
			isClearOperation(context) ||
			isBlendOperation(context) ||
			isResampleOperation(context) ||
			isInvertOperation(context)
	}

	private fun dispatchCommon(context: BlazeKoolComputeContext) {
		if (isClearOperation(context)) {
			clearWritableStorageTextures(context)
			clearWritableStorageBuffers(context)
			return
		}
		if (isInvertOperation(context) && invertTexture(context)) {
			return
		}
		if (isBlendOperation(context) && blendTextures(context)) {
			return
		}
		if (isResampleOperation(context) && resampleTexture(context)) {
			return
		}
		if (isCopyOperation(context)) {
			if (copyTextureToStorage(context)) {
				return
			}
			if (copyStorageTextureToStorage(context)) {
				return
			}
			if (copyStorageBuffers(context)) {
				return
			}
		}
	}

	private fun copyTextureToStorage(context: BlazeKoolComputeContext): Boolean {
		val sourceBinding = firstTexture(context) ?: return false
		val source = sourceBinding.texture
		val targetBinding = firstWritableStorageTexture(context) ?: return false
		val target = targetBinding.storageTexture ?: return false
		val imageData = image2d(context.readTexture(source, sourceBinding.mipLevel))
		context.writeStorageTexture(target, targetBinding.mipLevel, imageData)
		return true
	}

	private fun copyStorageTextureToStorage(context: BlazeKoolComputeContext): Boolean {
		val targetBinding = firstWritableStorageTexture(context) ?: return false
		val target = targetBinding.storageTexture ?: return false
		val sourceBinding = context.pipelineBindings.filterIsInstance<BindGroupData.StorageTextureBindingData<*>>()
			.firstOrNull { it.storageTexture != null && it !== targetBinding && it.layout.accessType == StorageAccessType.READ_ONLY }
			?: context.pipelineBindings.filterIsInstance<BindGroupData.StorageTextureBindingData<*>>()
				.firstOrNull { it.storageTexture != null && it !== targetBinding }
			?: return false
		val source = sourceBinding.storageTexture ?: return false
		val imageData = image2d(context.readStorageTexture(source, sourceBinding.mipLevel))
		context.writeStorageTexture(target, targetBinding.mipLevel, imageData)
		return true
	}

	private fun copyStorageBuffers(context: BlazeKoolComputeContext): Boolean {
		val sourceBinding = context.pipelineBindings.filterIsInstance<BindGroupData.StorageBufferBindingData>()
			.firstOrNull { it.layout.accessType == StorageAccessType.READ_ONLY || readName(it.name) }
		val targetBinding = context.pipelineBindings.filterIsInstance<BindGroupData.StorageBufferBindingData>()
			.firstOrNull { it.storageBuffer != null && it !== sourceBinding && it.layout.accessType != StorageAccessType.READ_ONLY }
		val source = sourceBinding?.storageBuffer ?: context.storageBuffers.firstOrNull() ?: return false
		val target = targetBinding?.storageBuffer ?: context.storageBuffers.firstOrNull { it !== source } ?: return false
		context.writeStorageBuffer(target, context.readStorageBuffer(source))
		return true
	}

	private fun clearWritableStorageTextures(context: BlazeKoolComputeContext) {
		val bindings = context.pipelineBindings.filterIsInstance<BindGroupData.StorageTextureBindingData<*>>()
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			val texture = binding.storageTexture
			if (texture != null && binding.layout.accessType != StorageAccessType.READ_ONLY) {
				val existing = image2d(context.readStorageTexture(texture, binding.mipLevel))
				context.writeStorageTexture(texture, binding.mipLevel, clearedImage(existing))
			}
			index++
		}
	}

	private fun clearWritableStorageBuffers(context: BlazeKoolComputeContext) {
		val bindings = context.pipelineBindings.filterIsInstance<BindGroupData.StorageBufferBindingData>()
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			val buffer = binding.storageBuffer
			if (buffer != null && binding.layout.accessType != StorageAccessType.READ_ONLY) {
				context.writeStorageBuffer(buffer, clearedBuffer(context.readStorageBuffer(buffer)))
			}
			index++
		}
	}

	private fun blendTextures(context: BlazeKoolComputeContext): Boolean {
		val targetBinding = firstWritableStorageTexture(context) ?: return false
		val target = targetBinding.storageTexture ?: return false
		val sampledInputs = textureBindings(context).map { image2d(context.readTexture(it.texture, it.mipLevel)) }
		val storageInputs = readableStorageTextureBindings(context, targetBinding).mapNotNull { binding ->
			binding.storageTexture?.let { image2d(context.readStorageTexture(it, binding.mipLevel)) }
		}
		val inputs = sampledInputs + storageInputs
		if (inputs.isEmpty()) {
			return false
		}
		val targetImage = storageImage2d(target, targetBinding.mipLevel)
		val targetPixels = PixelImage(targetImage)
		val samplers = inputs.map { ImageSampler(it) }
		val lower = shaderName(context)
		val alpha = uniformScalar(context, listOf("alpha", "uAlpha", "mix", "uMix", "blend", "uBlend"), 0.5f)
		val outputScale = uniformScalar(context, listOf("outputScale", "uOutputScale", "strength", "uStrength"), 1.0f)
		var y = 0
		while (y < targetImage.height) {
			var x = 0
			while (x < targetImage.width) {
				val u = (x + 0.5f) / targetImage.width
				val v = (y + 0.5f) / targetImage.height
				val value = blendSamples(lower, samplers, u, v, alpha, outputScale)
				targetPixels.set(x, y, value)
				x++
			}
			y++
		}
		context.writeStorageTexture(target, targetBinding.mipLevel, targetImage)
		return true
	}

	private fun resampleTexture(context: BlazeKoolComputeContext): Boolean {
		val sourceBinding = firstTexture(context)
		val targetBinding = firstWritableStorageTexture(context) ?: return false
		val target = targetBinding.storageTexture ?: return false
		val sourceImage = if (sourceBinding != null) {
			image2d(context.readTexture(sourceBinding.texture, sourceBinding.mipLevel))
		} else {
			val storageSource = readableStorageTextureBindings(context, targetBinding).firstOrNull() ?: return false
			image2d(context.readStorageTexture(storageSource.storageTexture ?: return false, storageSource.mipLevel))
		}
		val targetImage = storageImage2d(target, targetBinding.mipLevel)
		val source = ImageSampler(sourceImage)
		val targetPixels = PixelImage(targetImage)
		var y = 0
		while (y < targetImage.height) {
			var x = 0
			while (x < targetImage.width) {
				targetPixels.set(x, y, source.sample((x + 0.5f) / targetImage.width, (y + 0.5f) / targetImage.height))
				x++
			}
			y++
		}
		context.writeStorageTexture(target, targetBinding.mipLevel, targetImage)
		return true
	}

	private fun invertTexture(context: BlazeKoolComputeContext): Boolean {
		val sourceBinding = firstTexture(context)
		val targetBinding = firstWritableStorageTexture(context) ?: return false
		val target = targetBinding.storageTexture ?: return false
		val sourceImage = if (sourceBinding != null) {
			image2d(context.readTexture(sourceBinding.texture, sourceBinding.mipLevel))
		} else {
			val storageSource = readableStorageTextureBindings(context, targetBinding).firstOrNull() ?: return false
			image2d(context.readStorageTexture(storageSource.storageTexture ?: return false, storageSource.mipLevel))
		}
		val targetImage = storageImage2d(target, targetBinding.mipLevel)
		val source = ImageSampler(sourceImage)
		val targetPixels = PixelImage(targetImage)
		var y = 0
		while (y < targetImage.height) {
			var x = 0
			while (x < targetImage.width) {
				val value = source.sample((x + 0.5f) / targetImage.width, (y + 0.5f) / targetImage.height)
				value[0] = 1.0f - value[0]
				value[1] = 1.0f - value[1]
				value[2] = 1.0f - value[2]
				targetPixels.set(x, y, value)
				x++
			}
			y++
		}
		context.writeStorageTexture(target, targetBinding.mipLevel, targetImage)
		return true
	}

	private fun textureBinding(context: BlazeKoolComputeContext, name: String): SampledTextureBinding? {
		var index = 0
		while (index < context.pipelineBindings.size) {
			val binding = context.pipelineBindings[index]
			if (binding.name == name && binding is BindGroupData.TextureBindingData<*>) {
				val texture = binding.textureValue()
				if (texture != null) {
					return SampledTextureBinding(texture, binding.sampler?.baseMipLevel ?: 0)
				}
			}
			index++
		}
		return null
	}

	private fun storageTextureBinding(context: BlazeKoolComputeContext, name: String): BindGroupData.StorageTextureBindingData<out StorageTexture>? {
		var index = 0
		while (index < context.pipelineBindings.size) {
			val binding = context.pipelineBindings[index]
			if (binding.name == name && binding is BindGroupData.StorageTextureBindingData<*>) {
				return binding
			}
			index++
		}
		return null
	}

	private fun firstTexture(context: BlazeKoolComputeContext): SampledTextureBinding? {
		var index = 0
		while (index < context.pipelineBindings.size) {
			val binding = context.pipelineBindings[index]
			if (binding is BindGroupData.TextureBindingData<*>) {
				val texture = binding.textureValue()
				if (texture != null) {
					return SampledTextureBinding(texture, binding.sampler?.baseMipLevel ?: 0)
				}
			}
			index++
		}
		return null
	}

	private fun textureBindings(context: BlazeKoolComputeContext): List<SampledTextureBinding> {
		val result = ArrayList<SampledTextureBinding>()
		var index = 0
		while (index < context.pipelineBindings.size) {
			val binding = context.pipelineBindings[index]
			if (binding is BindGroupData.TextureBindingData<*>) {
				val texture = binding.textureValue()
				if (texture != null) {
					result += SampledTextureBinding(texture, binding.sampler?.baseMipLevel ?: 0)
				}
			}
			index++
		}
		return result
	}

	private data class SampledTextureBinding(
		val texture: Texture<*>,
		val mipLevel: Int
	)

	private fun BindGroupData.TextureBindingData<*>.textureValue(): Texture<*>? {
		return when (this) {
			is BindGroupData.Texture1dBindingData -> texture
			is BindGroupData.Texture2dBindingData -> texture
			is BindGroupData.Texture3dBindingData -> texture
			is BindGroupData.TextureCubeBindingData -> texture
			is BindGroupData.Texture2dArrayBindingData -> texture
			is BindGroupData.TextureCubeArrayBindingData -> texture
			else -> null
		}
	}

	private fun firstStorageTexture(context: BlazeKoolComputeContext): BindGroupData.StorageTextureBindingData<out StorageTexture>? {
		var index = 0
		while (index < context.pipelineBindings.size) {
			val binding = context.pipelineBindings[index]
			if (binding is BindGroupData.StorageTextureBindingData<*> && binding.storageTexture != null) {
				return binding
			}
			index++
		}
		return null
	}

	private fun firstWritableStorageTexture(context: BlazeKoolComputeContext): BindGroupData.StorageTextureBindingData<out StorageTexture>? {
		var index = 0
		while (index < context.pipelineBindings.size) {
			val binding = context.pipelineBindings[index]
			if (
				binding is BindGroupData.StorageTextureBindingData<*> &&
				binding.storageTexture != null &&
				binding.layout.accessType != StorageAccessType.READ_ONLY
			) {
				return binding
			}
			index++
		}
		return null
	}

	private fun readableStorageTextureBindings(
		context: BlazeKoolComputeContext,
		targetBinding: BindGroupData.StorageTextureBindingData<out StorageTexture>
	): List<BindGroupData.StorageTextureBindingData<out StorageTexture>> {
		return context.pipelineBindings.filterIsInstance<BindGroupData.StorageTextureBindingData<*>>()
			.filter { it.storageTexture != null && it !== targetBinding }
			.sortedByDescending { if (it.layout.accessType == StorageAccessType.READ_ONLY) 1 else 0 }
	}

	private fun uniform1(context: BlazeKoolComputeContext, name: String, fallback: Float): Float {
		val member = uniformMember(context, name) as? Float1Member<*> ?: return fallback
		return uniformBuffer(context, name)?.buffer?.buffer?.getFloat32(member.byteOffset) ?: fallback
	}

	private fun uniformScalar(context: BlazeKoolComputeContext, names: List<String>, fallback: Float): Float {
		var index = 0
		while (index < names.size) {
			val value = uniform1(context, names[index], Float.NaN)
			if (!value.isNaN()) {
				return value
			}
			index++
		}
		return fallback
	}

	private fun uniform2(context: BlazeKoolComputeContext, name: String, fallback: FloatArray): FloatArray {
		val member = uniformMember(context, name) as? Float2Member<*> ?: return fallback
		val data = uniformBuffer(context, name)?.buffer?.buffer ?: return fallback
		return floatArrayOf(data.getFloat32(member.byteOffset), data.getFloat32(member.byteOffset + Float.SIZE_BYTES))
	}

	private fun uniform4(context: BlazeKoolComputeContext, name: String, fallback: FloatArray): FloatArray {
		val member = uniformMember(context, name) as? Float4Member<*> ?: return fallback
		val data = uniformBuffer(context, name)?.buffer?.buffer ?: return fallback
		return floatArrayOf(
			data.getFloat32(member.byteOffset),
			data.getFloat32(member.byteOffset + Float.SIZE_BYTES),
			data.getFloat32(member.byteOffset + Float.SIZE_BYTES * 2),
			data.getFloat32(member.byteOffset + Float.SIZE_BYTES * 3)
		)
	}

	private fun uniformMember(context: BlazeKoolComputeContext, name: String): StructMember<*>? {
		val uniform = uniformBuffer(context, name) ?: return null
		return uniform.buffer.struct.members.firstOrNull { it.name == name }
	}

	private fun uniformBuffer(context: BlazeKoolComputeContext, memberName: String): BindGroupData.UniformBufferBindingData<*>? {
		var index = 0
		while (index < context.pipelineBindings.size) {
			val binding = context.pipelineBindings[index]
			if (binding is BindGroupData.UniformBufferBindingData<*> && binding.buffer.struct.members.any { it.name == memberName }) {
				return binding
			}
			index++
		}
		return null
	}

	private fun image2d(imageData: ImageData, mipLevel: Int = 0): BufferedImageData2d {
		val image = when (imageData) {
			is BufferedImageData1d -> BufferedImageData2d(copyBuffer(imageData.data), imageData.width, 1, imageData.format)
			is BufferedImageData2d -> BufferedImageData2d(copyBuffer(imageData.data), imageData.width, imageData.height, imageData.format)
			is BufferedImageData3d -> layerImage(imageData, 0)
			else -> BufferedImageData2d(ImageData.createBuffer(imageData.format, imageDataWidth(imageData), imageDataHeight(imageData)), imageDataWidth(imageData), imageDataHeight(imageData), imageData.format)
		}
		return mipImage(image, mipLevel)
	}

	private fun storageImage2d(texture: StorageTexture, mipLevel: Int): BufferedImageData2d {
		val width = (texture.asTexture.width shr mipLevel).coerceAtLeast(1)
		val height = (texture.asTexture.height shr mipLevel).coerceAtLeast(1)
		return BufferedImageData2d(ImageData.createBuffer(texture.asTexture.format, width, height), width, height, texture.asTexture.format)
	}

	private fun layerImage(imageData: BufferedImageData3d, layer: Int): BufferedImageData2d {
		val layerSize = imageData.width * imageData.height * imageData.format.channels
		val start = layer.coerceIn(0, imageData.depth - 1) * layerSize
		return BufferedImageData2d(copyBufferRange(imageData.data, start, layerSize), imageData.width, imageData.height, imageData.format)
	}

	private fun mipImage(imageData: BufferedImageData2d, mipLevel: Int): BufferedImageData2d {
		if (mipLevel <= 0) {
			return imageData
		}
		val width = (imageData.width shr mipLevel).coerceAtLeast(1)
		val height = (imageData.height shr mipLevel).coerceAtLeast(1)
		val source = ImageSampler(imageData)
		val result = BufferedImageData2d(ImageData.createBuffer(imageData.format, width, height), width, height, imageData.format)
		val target = PixelImage(result)
		var y = 0
		while (y < height) {
			var x = 0
			while (x < width) {
				target.set(x, y, source.sample((x + 0.5f) / width, (y + 0.5f) / height))
				x++
			}
			y++
		}
		return result
	}

	private fun clearedImage(image: BufferedImageData2d): BufferedImageData2d {
		return BufferedImageData2d(ImageData.createBuffer(image.format, image.width, image.height), image.width, image.height, image.format)
	}

	private fun clearedBuffer(buffer: Buffer): Buffer {
		val result = newBuffer(buffer)
		result.position = result.capacity
		return result
	}

	private fun copyBuffer(buffer: Buffer): Buffer {
		val result = newBuffer(buffer)
		copyBufferInto(buffer, result)
		return result
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

	private fun newBuffer(buffer: Buffer): Buffer {
		return when (buffer) {
			is Uint8Buffer -> Uint8Buffer(buffer.capacity)
			is Float32Buffer -> Float32Buffer(buffer.capacity)
			is Int32Buffer -> Int32Buffer(buffer.capacity)
			is MixedBuffer -> MixedBuffer(buffer.capacity)
			else -> buffer
		}
	}

	private fun copyBufferInto(source: Buffer, target: Buffer) {
		when {
			source is Uint8Buffer && target is Uint8Buffer -> {
				var index = 0
				while (index < source.capacity && index < target.capacity) {
					target.put(source[index])
					index++
				}
			}
			source is Float32Buffer && target is Float32Buffer -> {
				var index = 0
				while (index < source.capacity && index < target.capacity) {
					target.put(source[index])
					index++
				}
			}
			source is Int32Buffer && target is Int32Buffer -> {
				var index = 0
				while (index < source.capacity && index < target.capacity) {
					target.put(source[index])
					index++
				}
			}
			source is MixedBuffer && target is MixedBuffer -> {
				var index = 0
				while (index < source.capacity && index < target.capacity) {
					target.putInt8(source.getInt8(index))
					index++
				}
			}
		}
		target.position = target.capacity
	}

	private fun addWeighted(target: FloatArray, source: FloatArray, weight: Float) {
		target[0] += source[0] * weight
		target[1] += source[1] * weight
		target[2] += source[2] * weight
		target[3] += source[3] * weight
	}

	private fun smoothStep(edge0: Float, edge1: Float, value: Float): Float {
		if (edge0 == edge1) {
			return if (value >= edge1) 1.0f else 0.0f
		}
		val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0.0f, 1.0f)
		return t * t * (3.0f - 2.0f * t)
	}

	private fun blendSamples(shaderName: String, samplers: List<ImageSampler>, u: Float, v: Float, alpha: Float, outputScale: Float): FloatArray {
		val first = samplers[0].sample(u, v)
		if (samplers.size == 1) {
			first[0] *= outputScale
			first[1] *= outputScale
			first[2] *= outputScale
			return first
		}
		var index = 1
		while (index < samplers.size) {
			val next = samplers[index].sample(u, v)
			when {
				shaderName.contains("add") || shaderName.contains("compose") -> {
					first[0] += next[0] * alpha
					first[1] += next[1] * alpha
					first[2] += next[2] * alpha
					first[3] = maxOf(first[3], next[3])
				}
				shaderName.contains("mul") || shaderName.contains("multiply") -> {
					first[0] *= next[0]
					first[1] *= next[1]
					first[2] *= next[2]
					first[3] *= next[3]
				}
				shaderName.contains("min") -> {
					first[0] = minOf(first[0], next[0])
					first[1] = minOf(first[1], next[1])
					first[2] = minOf(first[2], next[2])
					first[3] = minOf(first[3], next[3])
				}
				shaderName.contains("max") -> {
					first[0] = maxOf(first[0], next[0])
					first[1] = maxOf(first[1], next[1])
					first[2] = maxOf(first[2], next[2])
					first[3] = maxOf(first[3], next[3])
				}
				else -> {
					first[0] = first[0] * (1.0f - alpha) + next[0] * alpha
					first[1] = first[1] * (1.0f - alpha) + next[1] * alpha
					first[2] = first[2] * (1.0f - alpha) + next[2] * alpha
					first[3] = first[3] * (1.0f - alpha) + next[3] * alpha
				}
			}
			index++
		}
		first[0] *= outputScale
		first[1] *= outputScale
		first[2] *= outputScale
		return first
	}

	private fun readName(name: String): Boolean {
		val lower = name.lowercase()
		return lower.contains("read") || lower.contains("src") || lower.contains("source") || lower.contains("input")
	}

	private fun isCopyOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		if (lower.contains("copy") || lower.contains("blit")) {
			return true
		}
		return context.pipelineBindings.any { readName(it.name) } && context.pipelineBindings.any { writeName(it.name) }
	}

	private fun isClearOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("clear") || lower.contains("fill")
	}

	private fun isBlendOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("blend") ||
			lower.contains("mix") ||
			lower.contains("compose") ||
			lower.contains("composite") ||
			lower.contains("add") ||
			lower.contains("multiply")
	}

	private fun isResampleOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("resample") ||
			lower.contains("resize") ||
			lower.contains("scale") ||
			lower.contains("mipmap") ||
			lower.contains("mip")
	}

	private fun isInvertOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("invert") || lower.contains("negative")
	}

	private fun writeName(name: String): Boolean {
		val lower = name.lowercase()
		return lower.contains("write") || lower.contains("dst") || lower.contains("target") || lower.contains("output")
	}

	private fun shaderName(context: BlazeKoolComputeContext): String {
		return context.task.shader.name.lowercase()
	}

	private fun imageDataWidth(imageData: ImageData): Int {
		return when (imageData) {
			is BufferedImageData1d -> imageData.width
			is BufferedImageData2d -> imageData.width
			is BufferedImageData3d -> imageData.width
			else -> 1
		}
	}

	private fun imageDataHeight(imageData: ImageData): Int {
		return when (imageData) {
			is BufferedImageData2d -> imageData.height
			is BufferedImageData3d -> imageData.height
			else -> 1
		}
	}

	private class ImageSampler(private val image: BufferedImageData2d) {
		private val pixels = PixelImage(image)

		fun texel(x: Int, y: Int): FloatArray {
			return pixels.get(x.coerceIn(0, image.width - 1), y.coerceIn(0, image.height - 1))
		}

		fun sample(u: Float, v: Float): FloatArray {
			val x = floor(u.coerceIn(0.0f, 1.0f) * (image.width - 1)).toInt()
			val y = floor(v.coerceIn(0.0f, 1.0f) * (image.height - 1)).toInt()
			return texel(x, y)
		}
	}

	private class PixelImage(private val image: BufferedImageData2d) {
		fun get(x: Int, y: Int): FloatArray {
			val base = (y * image.width + x) * image.format.channels
			val data = image.data
			return when (data) {
				is Uint8Buffer -> floatArrayOf(
					byteChannel(data, base, 0, image.format),
					byteChannel(data, base, 1, image.format),
					byteChannel(data, base, 2, image.format),
					byteChannel(data, base, 3, image.format)
				)
				is Float32Buffer -> floatArrayOf(
					floatChannel(data, base, 0, image.format),
					floatChannel(data, base, 1, image.format),
					floatChannel(data, base, 2, image.format),
					floatChannel(data, base, 3, image.format)
				)
				is Int32Buffer -> floatArrayOf(
					intChannel(data, base, 0, image.format),
					intChannel(data, base, 1, image.format),
					intChannel(data, base, 2, image.format),
					intChannel(data, base, 3, image.format)
				)
				else -> floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
			}
		}

		fun set(x: Int, y: Int, value: FloatArray) {
			val base = (y * image.width + x) * image.format.channels
			val data = image.data
			when (data) {
				is Uint8Buffer -> writeBytes(data, base, image.format, value)
				is Float32Buffer -> writeFloats(data, base, image.format, value)
				is Int32Buffer -> writeInts(data, base, image.format, value)
			}
		}

		private fun byteChannel(data: Uint8Buffer, base: Int, channel: Int, format: TexFormat): Float {
			val actual = channel.coerceAtMost(format.channels - 1)
			return if (channel < format.channels) data[base + actual].toInt() / 255.0f else 1.0f
		}

		private fun floatChannel(data: Float32Buffer, base: Int, channel: Int, format: TexFormat): Float {
			val actual = channel.coerceAtMost(format.channels - 1)
			return if (channel < format.channels) data[base + actual] else 1.0f
		}

		private fun intChannel(data: Int32Buffer, base: Int, channel: Int, format: TexFormat): Float {
			val actual = channel.coerceAtMost(format.channels - 1)
			return if (channel < format.channels) data[base + actual].toFloat() else 1.0f
		}

		private fun writeBytes(data: Uint8Buffer, base: Int, format: TexFormat, value: FloatArray) {
			var channel = 0
			while (channel < format.channels) {
				data[base + channel] = (value[channel].coerceIn(0.0f, 1.0f) * 255.0f).toInt().toUByte()
				channel++
			}
		}

		private fun writeFloats(data: Float32Buffer, base: Int, format: TexFormat, value: FloatArray) {
			var channel = 0
			while (channel < format.channels) {
				data[base + channel] = value[channel]
				channel++
			}
		}

		private fun writeInts(data: Int32Buffer, base: Int, format: TexFormat, value: FloatArray) {
			var channel = 0
			while (channel < format.channels) {
				data[base + channel] = value[channel].toInt()
				channel++
			}
		}
	}
}
