package com.micheanl.kool.api.compute

import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.pipeline.BufferedImageData1d
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.BufferedImageData3d
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.GpuType
import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.StorageAccessType
import de.fabmax.kool.pipeline.StorageTexture
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.isBool
import de.fabmax.kool.pipeline.isByte
import de.fabmax.kool.pipeline.isF16
import de.fabmax.kool.pipeline.isF32
import de.fabmax.kool.pipeline.isI32
import de.fabmax.kool.pipeline.isFloat
import de.fabmax.kool.pipeline.isInt
import de.fabmax.kool.pipeline.isU32
import de.fabmax.kool.pipeline.isUint
import de.fabmax.kool.util.Bool1Member
import de.fabmax.kool.util.Buffer
import de.fabmax.kool.util.Float1Member
import de.fabmax.kool.util.Float2Member
import de.fabmax.kool.util.Float4Member
import de.fabmax.kool.util.Float32Buffer
import de.fabmax.kool.util.Int1Member
import de.fabmax.kool.util.Int32Buffer
import de.fabmax.kool.util.MixedBuffer
import de.fabmax.kool.util.Struct
import de.fabmax.kool.util.StructArrayMember
import de.fabmax.kool.util.StructMember
import de.fabmax.kool.util.Uint8Buffer
import de.fabmax.kool.util.Uint1Member
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
			isFillOperation(context) ||
			isBlendOperation(context) ||
			isResampleOperation(context) ||
			isInvertOperation(context) ||
			isPrefixOperation(context) ||
			isReduceOperation(context) ||
			isScatterGatherOperation(context) ||
			isBufferClampOperation(context)
	}

	private fun dispatchCommon(context: BlazeKoolComputeContext) {
		if (isFillOperation(context)) {
			val filledTextures = fillWritableStorageTextures(context)
			val filledBuffers = fillWritableStorageBuffers(context)
			if (filledTextures || filledBuffers) {
				return
			}
		}
		if (isClearOperation(context)) {
			clearWritableStorageTextures(context)
			clearWritableStorageBuffers(context)
			return
		}
		if (isInvertOperation(context) && invertTexture(context)) {
			return
		}
		if (isInvertOperation(context) && transformStorageBuffers(context, BufferTransform.INVERT)) {
			return
		}
		if (isPrefixOperation(context) && prefixStorageBuffers(context)) {
			return
		}
		if (isReduceOperation(context) && reduceStorageBuffers(context)) {
			return
		}
		if (isScatterGatherOperation(context) && scatterGatherStorageBuffers(context)) {
			return
		}
		if (isBufferClampOperation(context) && clampStorageBuffers(context)) {
			return
		}
		if (isBlendOperation(context) && blendTextures(context)) {
			return
		}
		if (isBlendOperation(context) && combineStorageBuffers(context)) {
			return
		}
		if (isResampleOperation(context) && resampleTexture(context)) {
			return
		}
		if (isResampleOperation(context) && transformStorageBuffers(context, BufferTransform.SCALE)) {
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
		val sourceData = context.readStorageBuffer(source)
		val targetData = context.readStorageBuffer(target)
		val elementOffset = uniformInt(context, listOf("offset", "dstOffset", "targetOffset", "writeOffset"), 0)
		val sourceOffset = uniformInt(context, listOf("srcOffset", "sourceOffset", "readOffset"), 0)
		val elementCount = dispatchElementCount(context, targetBinding, targetData)
		val copied = copyStorageBufferRange(target.type, sourceData, targetData, sourceOffset, elementOffset, elementCount)
		context.writeStorageBuffer(target, copied)
		return true
	}

	private fun fillWritableStorageTextures(context: BlazeKoolComputeContext): Boolean {
		val bindings = context.pipelineBindings.filterIsInstance<BindGroupData.StorageTextureBindingData<*>>()
		val color = uniform4(
			context,
			listOf("value", "fillValue", "clearValue", "color", "fillColor", "uValue", "uColor"),
			FloatArray(4)
		)
		var didFill = false
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			val texture = binding.storageTexture
			if (texture != null && binding.layout.accessType != StorageAccessType.READ_ONLY) {
				val existing = image2d(context.readStorageTexture(texture, binding.mipLevel))
				val image = filledImage(existing, color)
				context.writeStorageTexture(texture, binding.mipLevel, image)
				didFill = true
			}
			index++
		}
		return didFill
	}

	private fun fillWritableStorageBuffers(context: BlazeKoolComputeContext): Boolean {
		val bindings = writableStorageBufferBindings(context)
		if (bindings.isEmpty()) {
			return false
		}
		val floatValue = uniformScalar(context, listOf("value", "fillValue", "clearValue", "scalar", "uValue"), 0.0f)
		val intValue = uniformInt(context, listOf("value", "fillValue", "clearValue", "scalar", "uValue"), floatValue.toInt())
		var didFill = false
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			val buffer = binding.storageBuffer
			if (buffer != null) {
				val existing = context.readStorageBuffer(buffer)
				val elementOffset = uniformInt(context, listOf("offset", "dstOffset", "targetOffset", "writeOffset"), 0)
				val elementCount = dispatchElementCount(context, binding, existing)
				val filled = fillStorageBuffer(buffer, existing, elementOffset, elementCount, floatValue, intValue)
				context.writeStorageBuffer(buffer, filled)
				didFill = true
			}
			index++
		}
		return didFill
	}

	private fun combineStorageBuffers(context: BlazeKoolComputeContext): Boolean {
		val targetBinding = writableStorageBufferBindings(context).firstOrNull() ?: return false
		val target = targetBinding.storageBuffer ?: return false
		val sourceBindings = readableStorageBufferBindings(context, targetBinding)
		val firstSource = sourceBindings.getOrNull(0)?.storageBuffer ?: target
		val secondSource = sourceBindings.getOrNull(1)?.storageBuffer
		val firstData = context.readStorageBuffer(firstSource)
		val secondData = secondSource?.let(context::readStorageBuffer)
		val targetData = context.readStorageBuffer(target)
		val elementOffset = uniformInt(context, listOf("offset", "dstOffset", "targetOffset", "writeOffset"), 0)
		val sourceOffset = uniformInt(context, listOf("srcOffset", "sourceOffset", "readOffset"), 0)
		val elementCount = dispatchElementCount(context, targetBinding, targetData)
		val alpha = uniformScalar(context, listOf("alpha", "uAlpha", "mix", "uMix", "blend", "uBlend"), 0.5f)
		val scalar = uniformScalar(context, listOf("scale", "uScale", "factor", "uFactor", "value", "uValue"), 1.0f)
		val result = combineBuffers(
			shaderName(context),
			target.type,
			firstData,
			secondData,
			targetData,
			sourceOffset,
			elementOffset,
			elementCount,
			alpha,
			scalar
		)
		context.writeStorageBuffer(target, result)
		return true
	}

	private fun transformStorageBuffers(context: BlazeKoolComputeContext, transform: BufferTransform): Boolean {
		val bindings = writableStorageBufferBindings(context)
		if (bindings.isEmpty()) {
			return false
		}
		val scalar = uniformScalar(context, listOf("scale", "uScale", "factor", "uFactor", "value", "uValue"), 1.0f)
		var didTransform = false
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			val buffer = binding.storageBuffer
			if (buffer != null) {
				val data = context.readStorageBuffer(buffer)
				val elementOffset = uniformInt(context, listOf("offset", "dstOffset", "targetOffset", "writeOffset"), 0)
				val elementCount = dispatchElementCount(context, binding, data)
				val result = transformBuffer(buffer.type, data, elementOffset, elementCount, transform, scalar)
				context.writeStorageBuffer(buffer, result)
				didTransform = true
			}
			index++
		}
		return didTransform
	}

	private fun prefixStorageBuffers(context: BlazeKoolComputeContext): Boolean {
		val targetBinding = writableStorageBufferBindings(context).firstOrNull() ?: return false
		val target = targetBinding.storageBuffer ?: return false
		val source = readableStorageBufferBindings(context, targetBinding).firstOrNull()?.storageBuffer ?: target
		val sourceData = context.readStorageBuffer(source)
		val targetData = context.readStorageBuffer(target)
		val elementOffset = uniformInt(context, listOf("offset", "dstOffset", "targetOffset", "writeOffset"), 0)
		val sourceOffset = uniformInt(context, listOf("srcOffset", "sourceOffset", "readOffset"), 0)
		val elementCount = dispatchElementCount(context, targetBinding, targetData)
		val result = prefixBuffer(target.type, sourceData, targetData, sourceOffset, elementOffset, elementCount)
		context.writeStorageBuffer(target, result)
		return true
	}

	private fun reduceStorageBuffers(context: BlazeKoolComputeContext): Boolean {
		val targetBinding = writableStorageBufferBindings(context).firstOrNull() ?: return false
		val target = targetBinding.storageBuffer ?: return false
		val source = readableStorageBufferBindings(context, targetBinding).firstOrNull()?.storageBuffer ?: target
		val sourceData = context.readStorageBuffer(source)
		val targetData = context.readStorageBuffer(target)
		val sourceOffset = uniformInt(context, listOf("srcOffset", "sourceOffset", "readOffset", "offset"), 0)
		val elementCount = dispatchElementCount(context, targetBinding, targetData)
		val result = reduceBuffer(shaderName(context), target.type, sourceData, targetData, sourceOffset, elementCount)
		context.writeStorageBuffer(target, result)
		return true
	}

	private fun scatterGatherStorageBuffers(context: BlazeKoolComputeContext): Boolean {
		val targetBinding = writableStorageBufferBindings(context).firstOrNull() ?: return false
		val target = targetBinding.storageBuffer ?: return false
		val sources = readableStorageBufferBindings(context, targetBinding)
		val valueSource = sources.firstOrNull { it.storageBuffer?.type != GpuType.Int1 && it.storageBuffer?.type != GpuType.Uint1 }
			?: sources.firstOrNull()
			?: return false
		val indexSource = sources.firstOrNull { it !== valueSource && (it.storageBuffer?.type == GpuType.Int1 || it.storageBuffer?.type == GpuType.Uint1) }
		val sourceBuffer = valueSource.storageBuffer ?: return false
		val indexBuffer = indexSource?.storageBuffer
		val sourceData = context.readStorageBuffer(sourceBuffer)
		val targetData = context.readStorageBuffer(target)
		val indexData = indexBuffer?.let(context::readStorageBuffer)
		val elementCount = dispatchElementCount(context, targetBinding, targetData)
		val result = scatterGatherBuffer(shaderName(context), sourceBuffer.type, sourceData, indexData, targetData, elementCount)
		context.writeStorageBuffer(target, result)
		return true
	}

	private fun clampStorageBuffers(context: BlazeKoolComputeContext): Boolean {
		val bindings = writableStorageBufferBindings(context)
		if (bindings.isEmpty()) {
			return false
		}
		val minValue = uniformScalar(context, listOf("minValue", "min", "uMin", "lower"), 0.0f)
		val maxValue = uniformScalar(context, listOf("maxValue", "max", "uMax", "upper"), 1.0f)
		var didClamp = false
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			val buffer = binding.storageBuffer
			if (buffer != null) {
				val data = context.readStorageBuffer(buffer)
				val elementOffset = uniformInt(context, listOf("offset", "dstOffset", "targetOffset", "writeOffset"), 0)
				val elementCount = dispatchElementCount(context, binding, data)
				val result = clampBuffer(buffer.type, data, elementOffset, elementCount, minValue, maxValue)
				context.writeStorageBuffer(buffer, result)
				didClamp = true
			}
			index++
		}
		return didClamp
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

	private fun copyStorageBufferRange(
		type: GpuType,
		source: Buffer,
		target: Buffer,
		sourceOffset: Int,
		targetOffset: Int,
		elementCount: Int
	): Buffer {
		val result = copyBuffer(target)
		when {
			source is Float32Buffer && result is Float32Buffer -> copyFloatBufferRange(type, source, result, sourceOffset, targetOffset, elementCount)
			source is Int32Buffer && result is Int32Buffer -> copyIntBufferRange(type, source, result, sourceOffset, targetOffset, elementCount)
			source is MixedBuffer && result is MixedBuffer -> copyMixedBufferRange(type, source, result, sourceOffset, targetOffset, elementCount)
			source is Uint8Buffer && result is Uint8Buffer -> copyByteBufferRange(source, result, sourceOffset, targetOffset, elementCount)
			else -> copyBufferInto(source, result)
		}
		result.position = result.capacity
		return result
	}

	private fun fillStorageBuffer(
		buffer: GpuBuffer,
		data: Buffer,
		elementOffset: Int,
		elementCount: Int,
		floatValue: Float,
		intValue: Int
	): Buffer {
		val result = copyBuffer(data)
		val type = buffer.type
		when {
			type.isFloat && result is Float32Buffer -> fillFloatBufferRange(type, result, elementOffset, elementCount, floatValue)
			(type.isInt || type.isUint || type.isBool) && result is Int32Buffer -> fillIntBufferRange(type, result, elementOffset, elementCount, intValue)
			type is GpuType.Struct && result is MixedBuffer -> fillStructBufferRange(type.struct.members, result, elementOffset, elementCount, floatValue, intValue)
			result is Uint8Buffer -> fillByteBufferRange(result, elementOffset, elementCount, intValue)
		}
		result.position = result.capacity
		return result
	}

	private fun combineBuffers(
		shaderName: String,
		type: GpuType,
		firstData: Buffer,
		secondData: Buffer?,
		targetData: Buffer,
		sourceOffset: Int,
		targetOffset: Int,
		elementCount: Int,
		alpha: Float,
		scalar: Float
	): Buffer {
		val result = copyBuffer(targetData)
		when {
			firstData is Float32Buffer && result is Float32Buffer -> combineFloatBuffers(shaderName, type, firstData, secondData as? Float32Buffer, result, sourceOffset, targetOffset, elementCount, alpha, scalar)
			firstData is Int32Buffer && result is Int32Buffer -> combineIntBuffers(shaderName, type, firstData, secondData as? Int32Buffer, result, sourceOffset, targetOffset, elementCount, alpha, scalar)
			firstData is MixedBuffer && result is MixedBuffer -> combineMixedBuffers(shaderName, type, firstData, secondData as? MixedBuffer, result, sourceOffset, targetOffset, elementCount, alpha, scalar)
		}
		result.position = result.capacity
		return result
	}

	private fun transformBuffer(
		type: GpuType,
		data: Buffer,
		elementOffset: Int,
		elementCount: Int,
		transform: BufferTransform,
		scalar: Float
	): Buffer {
		val result = copyBuffer(data)
		when {
			result is Float32Buffer -> transformFloatBuffer(type, result, elementOffset, elementCount, transform, scalar)
			result is Int32Buffer -> transformIntBuffer(type, result, elementOffset, elementCount, transform, scalar)
			result is MixedBuffer -> transformMixedBuffer(type, result, elementOffset, elementCount, transform, scalar)
		}
		result.position = result.capacity
		return result
	}

	private fun prefixBuffer(
		type: GpuType,
		sourceData: Buffer,
		targetData: Buffer,
		sourceOffset: Int,
		targetOffset: Int,
		elementCount: Int
	): Buffer {
		val result = copyBuffer(targetData)
		when {
			sourceData is Float32Buffer && result is Float32Buffer -> prefixFloatBuffer(type, sourceData, result, sourceOffset, targetOffset, elementCount)
			sourceData is Int32Buffer && result is Int32Buffer -> prefixIntBuffer(type, sourceData, result, sourceOffset, targetOffset, elementCount)
			sourceData is MixedBuffer && result is MixedBuffer -> prefixMixedBuffer(type, sourceData, result, sourceOffset, targetOffset, elementCount)
		}
		result.position = result.capacity
		return result
	}

	private fun reduceBuffer(
		shaderName: String,
		type: GpuType,
		sourceData: Buffer,
		targetData: Buffer,
		sourceOffset: Int,
		elementCount: Int
	): Buffer {
		val result = copyBuffer(targetData)
		when {
			sourceData is Float32Buffer && result is Float32Buffer -> reduceFloatBuffer(shaderName, type, sourceData, result, sourceOffset, elementCount)
			sourceData is Int32Buffer && result is Int32Buffer -> reduceIntBuffer(shaderName, type, sourceData, result, sourceOffset, elementCount)
			sourceData is MixedBuffer && result is MixedBuffer -> reduceMixedBuffer(shaderName, type, sourceData, result, sourceOffset, elementCount)
		}
		result.position = result.capacity
		return result
	}

	private fun scatterGatherBuffer(
		shaderName: String,
		type: GpuType,
		sourceData: Buffer,
		indexData: Buffer?,
		targetData: Buffer,
		elementCount: Int
	): Buffer {
		val result = copyBuffer(targetData)
		val isScatter = shaderName.contains("scatter")
		when {
			sourceData is Float32Buffer && result is Float32Buffer -> scatterGatherFloatBuffer(type, sourceData, indexData as? Int32Buffer, result, elementCount, isScatter)
			sourceData is Int32Buffer && result is Int32Buffer -> scatterGatherIntBuffer(type, sourceData, indexData as? Int32Buffer, result, elementCount, isScatter)
			sourceData is MixedBuffer && result is MixedBuffer -> scatterGatherMixedBuffer(type, sourceData, indexData as? Int32Buffer, result, elementCount, isScatter)
		}
		result.position = result.capacity
		return result
	}

	private fun clampBuffer(type: GpuType, data: Buffer, elementOffset: Int, elementCount: Int, minValue: Float, maxValue: Float): Buffer {
		val result = copyBuffer(data)
		when {
			result is Float32Buffer -> clampFloatBuffer(type, result, elementOffset, elementCount, minValue, maxValue)
			result is Int32Buffer -> clampIntBuffer(type, result, elementOffset, elementCount, minValue.toInt(), maxValue.toInt())
			result is MixedBuffer -> clampMixedBuffer(type, result, elementOffset, elementCount, minValue, maxValue)
		}
		result.position = result.capacity
		return result
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

	private fun writableStorageBufferBindings(context: BlazeKoolComputeContext): List<BindGroupData.StorageBufferBindingData> {
		return context.pipelineBindings.filterIsInstance<BindGroupData.StorageBufferBindingData>()
			.filter { it.storageBuffer != null && it.layout.accessType != StorageAccessType.READ_ONLY }
	}

	private fun readableStorageBufferBindings(
		context: BlazeKoolComputeContext,
		targetBinding: BindGroupData.StorageBufferBindingData
	): List<BindGroupData.StorageBufferBindingData> {
		return context.pipelineBindings.filterIsInstance<BindGroupData.StorageBufferBindingData>()
			.filter { it.storageBuffer != null && it !== targetBinding }
			.sortedByDescending { if (it.layout.accessType == StorageAccessType.READ_ONLY || readName(it.name)) 1 else 0 }
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

	private fun uniformInt(context: BlazeKoolComputeContext, names: List<String>, fallback: Int): Int {
		var index = 0
		while (index < names.size) {
			val value = uniformInt(context, names[index], Int.MIN_VALUE)
			if (value != Int.MIN_VALUE) {
				return value
			}
			index++
		}
		return fallback
	}

	private fun uniformInt(context: BlazeKoolComputeContext, name: String, fallback: Int): Int {
		val member = uniformMember(context, name) ?: return fallback
		val data = uniformBuffer(context, name)?.buffer?.buffer ?: return fallback
		return when (member) {
			is Int1Member<*> -> data.getInt32(member.byteOffset)
			is Uint1Member<*> -> data.getInt32(member.byteOffset)
			is Bool1Member<*> -> data.getInt32(member.byteOffset)
			is Float1Member<*> -> data.getFloat32(member.byteOffset).toInt()
			else -> fallback
		}
	}

	private fun uniform2(context: BlazeKoolComputeContext, name: String, fallback: FloatArray): FloatArray {
		val member = uniformMember(context, name) as? Float2Member<*> ?: return fallback
		val data = uniformBuffer(context, name)?.buffer?.buffer ?: return fallback
		return floatArrayOf(data.getFloat32(member.byteOffset), data.getFloat32(member.byteOffset + Float.SIZE_BYTES))
	}

	private fun uniform4(context: BlazeKoolComputeContext, names: List<String>, fallback: FloatArray): FloatArray {
		var index = 0
		while (index < names.size) {
			val value = uniform4(context, names[index], fallback)
			if (!value.contentEquals(fallback)) {
				return value
			}
			index++
		}
		return fallback
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

	private fun filledImage(image: BufferedImageData2d, color: FloatArray): BufferedImageData2d {
		val result = BufferedImageData2d(ImageData.createBuffer(image.format, image.width, image.height), image.width, image.height, image.format)
		val target = PixelImage(result)
		var y = 0
		while (y < result.height) {
			var x = 0
			while (x < result.width) {
				target.set(x, y, color)
				x++
			}
			y++
		}
		return result
	}

	private fun dispatchElementCount(context: BlazeKoolComputeContext, binding: BindGroupData.StorageBufferBindingData?, buffer: Buffer): Int {
		val explicit = uniformInt(context, listOf("count", "numElements", "elementCount", "size", "length"), -1)
		val bindingSize = binding?.layout?.size ?: -1
		val dispatchSize = dispatchInvocationCount(context)
		val fallback = if (bindingSize > 0) bindingSize else primitiveElementCapacity(binding?.storageBuffer?.type, buffer)
		return when {
			explicit > 0 -> explicit
			dispatchSize > 0 -> minOf(dispatchSize, fallback)
			else -> fallback
		}.coerceAtLeast(0)
	}

	private fun dispatchInvocationCount(context: BlazeKoolComputeContext): Int {
		val workGroup = context.pipeline.workGroupSize
		val groupsX = context.groupsX.coerceAtLeast(1)
		val groupsY = context.groupsY.coerceAtLeast(1)
		val groupsZ = context.groupsZ.coerceAtLeast(1)
		return groupsX * groupsY * groupsZ * workGroup.x.coerceAtLeast(1) * workGroup.y.coerceAtLeast(1) * workGroup.z.coerceAtLeast(1)
	}

	private fun primitiveElementCapacity(type: GpuType?, buffer: Buffer): Int {
		val stride = primitiveStride(type).coerceAtLeast(1)
		return if (type is GpuType.Struct && buffer is MixedBuffer) {
			buffer.capacity / type.struct.structSize.coerceAtLeast(1)
		} else {
			buffer.capacity / stride
		}
	}

	private fun primitiveStride(type: GpuType?): Int {
		return when (type) {
			GpuType.Float1, GpuType.Int1, GpuType.Uint1, GpuType.Bool1 -> 1
			GpuType.Float2, GpuType.Int2, GpuType.Uint2, GpuType.Bool2 -> 2
			GpuType.Float3, GpuType.Float4, GpuType.Int3, GpuType.Int4, GpuType.Uint3, GpuType.Uint4, GpuType.Bool3, GpuType.Bool4 -> 4
			GpuType.Mat2 -> 8
			GpuType.Mat3 -> 12
			GpuType.Mat4 -> 16
			is GpuType.Struct -> type.struct.structSize
			else -> 1
		}
	}

	private fun typedComponentCount(type: GpuType): Int {
		return when (type) {
			GpuType.Float1, GpuType.Int1, GpuType.Uint1, GpuType.Bool1 -> 1
			GpuType.Float2, GpuType.Int2, GpuType.Uint2, GpuType.Bool2 -> 2
			GpuType.Float3, GpuType.Int3, GpuType.Uint3, GpuType.Bool3 -> 3
			GpuType.Float4, GpuType.Int4, GpuType.Uint4, GpuType.Bool4 -> 4
			GpuType.Mat2 -> 4
			GpuType.Mat3 -> 9
			GpuType.Mat4 -> 16
			is GpuType.Struct -> 0
		}
	}

	private fun copyFloatBufferRange(type: GpuType, source: Float32Buffer, target: Float32Buffer, sourceOffset: Int, targetOffset: Int, elementCount: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, targetOffset, stride)
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			val targetBase = (targetOffset + element) * stride
			var component = 0
			while (component < stride) {
				target[targetBase + component] = source[sourceBase + component]
				component++
			}
			element++
		}
	}

	private fun copyIntBufferRange(type: GpuType, source: Int32Buffer, target: Int32Buffer, sourceOffset: Int, targetOffset: Int, elementCount: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, targetOffset, stride)
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			val targetBase = (targetOffset + element) * stride
			var component = 0
			while (component < stride) {
				target[targetBase + component] = source[sourceBase + component]
				component++
			}
			element++
		}
	}

	private fun copyMixedBufferRange(type: GpuType, source: MixedBuffer, target: MixedBuffer, sourceOffset: Int, targetOffset: Int, elementCount: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, targetOffset, stride)
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			val targetBase = (targetOffset + element) * stride
			var byteIndex = 0
			while (byteIndex < stride) {
				target.setInt8(targetBase + byteIndex, source.getInt8(sourceBase + byteIndex))
				byteIndex++
			}
			element++
		}
	}

	private fun copyByteBufferRange(source: Uint8Buffer, target: Uint8Buffer, sourceOffset: Int, targetOffset: Int, elementCount: Int) {
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, targetOffset, 1)
		var element = 0
		while (element < count) {
			target[targetOffset + element] = source[sourceOffset + element]
			element++
		}
	}

	private fun fillFloatBufferRange(type: GpuType, target: Float32Buffer, elementOffset: Int, elementCount: Int, value: Float) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, stride)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * stride
			var component = 0
			while (component < components && base + component < target.capacity) {
				target[base + component] = value
				component++
			}
			element++
		}
	}

	private fun fillIntBufferRange(type: GpuType, target: Int32Buffer, elementOffset: Int, elementCount: Int, value: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, stride)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * stride
			var component = 0
			while (component < components && base + component < target.capacity) {
				target[base + component] = value
				component++
			}
			element++
		}
	}

	private fun fillStructBufferRange(members: List<StructMember<*>>, target: MixedBuffer, elementOffset: Int, elementCount: Int, floatValue: Float, intValue: Int) {
		val structSize = structSize(members)
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, structSize)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * structSize
			members.forEach { member ->
				fillStructMember(member, target, base, floatValue, intValue)
			}
			element++
		}
	}

	private fun fillStructMember(member: StructMember<*>, target: MixedBuffer, structBase: Int, floatValue: Float, intValue: Int) {
		val memberType = member.type
		val count = if (member is StructArrayMember<*>) member.arraySize else 1
		val stride = if (member is StructArrayMember<*>) member.arrayStride else memberType.byteSize
		var arrayIndex = 0
		while (arrayIndex < count) {
			val base = structBase + member.byteOffset + arrayIndex * stride
			val nestedType = memberType as? GpuType.Struct
			when {
				memberType.isFloat -> writeMixedFloatComponents(target, base, memberType, floatValue)
				memberType.isInt || memberType.isUint || memberType.isBool -> writeMixedIntComponents(target, base, memberType, intValue)
				nestedType != null -> fillNestedStruct(nestedType.struct, target, base, floatValue, intValue)
			}
			arrayIndex++
		}
	}

	private fun fillNestedStruct(struct: Struct, target: MixedBuffer, structBase: Int, floatValue: Float, intValue: Int) {
		struct.members.forEach { member ->
			fillStructMember(member, target, structBase, floatValue, intValue)
		}
	}

	private fun combineFloatBuffers(
		shaderName: String,
		type: GpuType,
		first: Float32Buffer,
		second: Float32Buffer?,
		target: Float32Buffer,
		sourceOffset: Int,
		targetOffset: Int,
		elementCount: Int,
		alpha: Float,
		scalar: Float
	) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, first.capacity, target.capacity, sourceOffset, targetOffset, stride)
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			val targetBase = (targetOffset + element) * stride
			var component = 0
			while (component < components && sourceBase + component < first.capacity && targetBase + component < target.capacity) {
				val a = first[sourceBase + component]
				val b = second?.get((sourceOffset + element) * stride + component) ?: target[targetBase + component]
				target[targetBase + component] = combineFloatValue(shaderName, a, b, alpha, scalar)
				component++
			}
			element++
		}
	}

	private fun combineIntBuffers(
		shaderName: String,
		type: GpuType,
		first: Int32Buffer,
		second: Int32Buffer?,
		target: Int32Buffer,
		sourceOffset: Int,
		targetOffset: Int,
		elementCount: Int,
		alpha: Float,
		scalar: Float
	) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, first.capacity, target.capacity, sourceOffset, targetOffset, stride)
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			val targetBase = (targetOffset + element) * stride
			var component = 0
			while (component < components && sourceBase + component < first.capacity && targetBase + component < target.capacity) {
				val a = first[sourceBase + component]
				val b = second?.get((sourceOffset + element) * stride + component) ?: target[targetBase + component]
				target[targetBase + component] = combineIntValue(shaderName, a, b, alpha, scalar)
				component++
			}
			element++
		}
	}

	private fun combineMixedBuffers(
		shaderName: String,
		type: GpuType,
		first: MixedBuffer,
		second: MixedBuffer?,
		target: MixedBuffer,
		sourceOffset: Int,
		targetOffset: Int,
		elementCount: Int,
		alpha: Float,
		scalar: Float
	) {
		if (type !is GpuType.Struct) {
			return
		}
		val structSize = type.struct.structSize.coerceAtLeast(1)
		val count = rangeElementCount(elementCount, first.capacity, target.capacity, sourceOffset, targetOffset, structSize)
		var element = 0
		while (element < count) {
			val firstBase = (sourceOffset + element) * structSize
			val secondBase = (sourceOffset + element) * structSize
			val targetBase = (targetOffset + element) * structSize
			type.struct.members.forEach { member ->
				combineStructMember(shaderName, member, first, second, target, firstBase, secondBase, targetBase, alpha, scalar)
			}
			element++
		}
	}

	private fun transformFloatBuffer(type: GpuType, target: Float32Buffer, elementOffset: Int, elementCount: Int, transform: BufferTransform, scalar: Float) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, stride)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * stride
			var component = 0
			while (component < components && base + component < target.capacity) {
				target[base + component] = transformFloatValue(target[base + component], transform, scalar)
				component++
			}
			element++
		}
	}

	private fun transformIntBuffer(type: GpuType, target: Int32Buffer, elementOffset: Int, elementCount: Int, transform: BufferTransform, scalar: Float) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, stride)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * stride
			var component = 0
			while (component < components && base + component < target.capacity) {
				target[base + component] = transformIntValue(target[base + component], transform, scalar)
				component++
			}
			element++
		}
	}

	private fun transformMixedBuffer(type: GpuType, target: MixedBuffer, elementOffset: Int, elementCount: Int, transform: BufferTransform, scalar: Float) {
		if (type !is GpuType.Struct) {
			return
		}
		val structSize = type.struct.structSize.coerceAtLeast(1)
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, structSize)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * structSize
			type.struct.members.forEach { member ->
				transformStructMember(member, target, base, transform, scalar)
			}
			element++
		}
	}

	private fun prefixFloatBuffer(type: GpuType, source: Float32Buffer, target: Float32Buffer, sourceOffset: Int, targetOffset: Int, elementCount: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, targetOffset, stride)
		val sums = FloatArray(components)
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			val targetBase = (targetOffset + element) * stride
			var component = 0
			while (component < components && sourceBase + component < source.capacity && targetBase + component < target.capacity) {
				sums[component] += source[sourceBase + component]
				target[targetBase + component] = sums[component]
				component++
			}
			element++
		}
	}

	private fun prefixIntBuffer(type: GpuType, source: Int32Buffer, target: Int32Buffer, sourceOffset: Int, targetOffset: Int, elementCount: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, targetOffset, stride)
		val sums = IntArray(components)
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			val targetBase = (targetOffset + element) * stride
			var component = 0
			while (component < components && sourceBase + component < source.capacity && targetBase + component < target.capacity) {
				sums[component] += source[sourceBase + component]
				target[targetBase + component] = sums[component]
				component++
			}
			element++
		}
	}

	private fun prefixMixedBuffer(type: GpuType, source: MixedBuffer, target: MixedBuffer, sourceOffset: Int, targetOffset: Int, elementCount: Int) {
		if (type !is GpuType.Struct) {
			return
		}
		val structSize = type.struct.structSize.coerceAtLeast(1)
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, targetOffset, structSize)
		val floatSums = HashMap<String, FloatArray>()
		val intSums = HashMap<String, IntArray>()
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * structSize
			val targetBase = (targetOffset + element) * structSize
			type.struct.members.forEach { member ->
				prefixStructMember(member, source, target, sourceBase, targetBase, floatSums, intSums)
			}
			element++
		}
	}

	private fun reduceFloatBuffer(shaderName: String, type: GpuType, source: Float32Buffer, target: Float32Buffer, sourceOffset: Int, elementCount: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, 0, stride)
		val values = FloatArray(components) { reductionInitialFloat(shaderName) }
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			var component = 0
			while (component < components && sourceBase + component < source.capacity) {
				values[component] = reduceFloatValue(shaderName, values[component], source[sourceBase + component])
				component++
			}
			element++
		}
		var component = 0
		while (component < components && component < target.capacity) {
			target[component] = values[component]
			component++
		}
	}

	private fun reduceIntBuffer(shaderName: String, type: GpuType, source: Int32Buffer, target: Int32Buffer, sourceOffset: Int, elementCount: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, 0, stride)
		val values = IntArray(components) { reductionInitialInt(shaderName) }
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * stride
			var component = 0
			while (component < components && sourceBase + component < source.capacity) {
				values[component] = reduceIntValue(shaderName, values[component], source[sourceBase + component])
				component++
			}
			element++
		}
		var component = 0
		while (component < components && component < target.capacity) {
			target[component] = values[component]
			component++
		}
	}

	private fun reduceMixedBuffer(shaderName: String, type: GpuType, source: MixedBuffer, target: MixedBuffer, sourceOffset: Int, elementCount: Int) {
		if (type !is GpuType.Struct) {
			return
		}
		val structSize = type.struct.structSize.coerceAtLeast(1)
		val count = rangeElementCount(elementCount, source.capacity, target.capacity, sourceOffset, 0, structSize)
		val floatValues = HashMap<String, FloatArray>()
		val intValues = HashMap<String, IntArray>()
		var element = 0
		while (element < count) {
			val sourceBase = (sourceOffset + element) * structSize
			type.struct.members.forEach { member ->
				reduceStructMember(shaderName, member, source, sourceBase, floatValues, intValues)
			}
			element++
		}
		writeReducedStruct(type.struct.members, target, 0, floatValues, intValues)
	}

	private fun scatterGatherFloatBuffer(type: GpuType, source: Float32Buffer, indices: Int32Buffer?, target: Float32Buffer, elementCount: Int, isScatter: Boolean) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = minOf(elementCount, source.capacity / stride, target.capacity / stride)
		var element = 0
		while (element < count) {
			val mapped = indices?.get(element)?.coerceAtLeast(0) ?: element
			val sourceIndex = if (isScatter) element else mapped
			val targetIndex = if (isScatter) mapped else element
			if ((sourceIndex + 1) * stride <= source.capacity && (targetIndex + 1) * stride <= target.capacity) {
				copyFloatElement(source, target, sourceIndex, targetIndex, stride, components)
			}
			element++
		}
	}

	private fun scatterGatherIntBuffer(type: GpuType, source: Int32Buffer, indices: Int32Buffer?, target: Int32Buffer, elementCount: Int, isScatter: Boolean) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = minOf(elementCount, source.capacity / stride, target.capacity / stride)
		var element = 0
		while (element < count) {
			val mapped = indices?.get(element)?.coerceAtLeast(0) ?: element
			val sourceIndex = if (isScatter) element else mapped
			val targetIndex = if (isScatter) mapped else element
			if ((sourceIndex + 1) * stride <= source.capacity && (targetIndex + 1) * stride <= target.capacity) {
				copyIntElement(source, target, sourceIndex, targetIndex, stride, components)
			}
			element++
		}
	}

	private fun scatterGatherMixedBuffer(type: GpuType, source: MixedBuffer, indices: Int32Buffer?, target: MixedBuffer, elementCount: Int, isScatter: Boolean) {
		if (type !is GpuType.Struct) {
			return
		}
		val stride = type.struct.structSize.coerceAtLeast(1)
		val count = minOf(elementCount, source.capacity / stride, target.capacity / stride)
		var element = 0
		while (element < count) {
			val mapped = indices?.get(element)?.coerceAtLeast(0) ?: element
			val sourceIndex = if (isScatter) element else mapped
			val targetIndex = if (isScatter) mapped else element
			if ((sourceIndex + 1) * stride <= source.capacity && (targetIndex + 1) * stride <= target.capacity) {
				var byteIndex = 0
				while (byteIndex < stride) {
					target.setInt8(targetIndex * stride + byteIndex, source.getInt8(sourceIndex * stride + byteIndex))
					byteIndex++
				}
			}
			element++
		}
	}

	private fun clampFloatBuffer(type: GpuType, target: Float32Buffer, elementOffset: Int, elementCount: Int, minValue: Float, maxValue: Float) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, stride)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * stride
			var component = 0
			while (component < components && base + component < target.capacity) {
				target[base + component] = target[base + component].coerceIn(minValue, maxValue)
				component++
			}
			element++
		}
	}

	private fun clampIntBuffer(type: GpuType, target: Int32Buffer, elementOffset: Int, elementCount: Int, minValue: Int, maxValue: Int) {
		val stride = primitiveStride(type).coerceAtLeast(1)
		val components = typedComponentCount(type).takeIf { it > 0 } ?: stride
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, stride)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * stride
			var component = 0
			while (component < components && base + component < target.capacity) {
				target[base + component] = target[base + component].coerceIn(minValue, maxValue)
				component++
			}
			element++
		}
	}

	private fun clampMixedBuffer(type: GpuType, target: MixedBuffer, elementOffset: Int, elementCount: Int, minValue: Float, maxValue: Float) {
		if (type !is GpuType.Struct) {
			return
		}
		val structSize = type.struct.structSize.coerceAtLeast(1)
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, structSize)
		var element = 0
		while (element < count) {
			val base = (elementOffset + element) * structSize
			type.struct.members.forEach { member ->
				clampStructMember(member, target, base, minValue, maxValue)
			}
			element++
		}
	}

	private fun combineStructMember(
		shaderName: String,
		member: StructMember<*>,
		first: MixedBuffer,
		second: MixedBuffer?,
		target: MixedBuffer,
		firstStructBase: Int,
		secondStructBase: Int,
		targetStructBase: Int,
		alpha: Float,
		scalar: Float
	) {
		val memberType = member.type
		val count = if (member is StructArrayMember<*>) member.arraySize else 1
		val stride = if (member is StructArrayMember<*>) member.arrayStride else memberType.byteSize
		var arrayIndex = 0
		while (arrayIndex < count) {
			val firstBase = firstStructBase + member.byteOffset + arrayIndex * stride
			val secondBase = secondStructBase + member.byteOffset + arrayIndex * stride
			val targetBase = targetStructBase + member.byteOffset + arrayIndex * stride
			val nestedType = memberType as? GpuType.Struct
			when {
				memberType.isFloat -> {
					var component = 0
					val components = typedComponentCount(memberType)
					while (component < components) {
						val byteOffset = component * Float.SIZE_BYTES
						val a = mixedFloat(first, firstBase + byteOffset, 0.0f)
						val b = second?.let { mixedFloat(it, secondBase + byteOffset, mixedFloat(target, targetBase + byteOffset, 0.0f)) }
							?: mixedFloat(target, targetBase + byteOffset, 0.0f)
						setMixedFloat(target, targetBase + byteOffset, combineFloatValue(shaderName, a, b, alpha, scalar))
						component++
					}
				}
				memberType.isInt || memberType.isUint || memberType.isBool -> {
					var component = 0
					val components = typedComponentCount(memberType)
					while (component < components) {
						val byteOffset = component * Int.SIZE_BYTES
						val a = mixedInt(first, firstBase + byteOffset, 0)
						val b = second?.let { mixedInt(it, secondBase + byteOffset, mixedInt(target, targetBase + byteOffset, 0)) }
							?: mixedInt(target, targetBase + byteOffset, 0)
						setMixedInt(target, targetBase + byteOffset, combineIntValue(shaderName, a, b, alpha, scalar))
						component++
					}
				}
				nestedType != null -> {
					nestedType.struct.members.forEach { nested ->
						combineStructMember(shaderName, nested, first, second, target, firstBase, secondBase, targetBase, alpha, scalar)
					}
				}
			}
			arrayIndex++
		}
	}

	private fun transformStructMember(member: StructMember<*>, target: MixedBuffer, structBase: Int, transform: BufferTransform, scalar: Float) {
		val memberType = member.type
		val count = if (member is StructArrayMember<*>) member.arraySize else 1
		val stride = if (member is StructArrayMember<*>) member.arrayStride else memberType.byteSize
		var arrayIndex = 0
		while (arrayIndex < count) {
			val base = structBase + member.byteOffset + arrayIndex * stride
			val nestedType = memberType as? GpuType.Struct
			when {
				memberType.isFloat -> {
					var component = 0
					val components = typedComponentCount(memberType)
					while (component < components) {
						val byteOffset = component * Float.SIZE_BYTES
						setMixedFloat(target, base + byteOffset, transformFloatValue(mixedFloat(target, base + byteOffset, 0.0f), transform, scalar))
						component++
					}
				}
				memberType.isInt || memberType.isUint || memberType.isBool -> {
					var component = 0
					val components = typedComponentCount(memberType)
					while (component < components) {
						val byteOffset = component * Int.SIZE_BYTES
						setMixedInt(target, base + byteOffset, transformIntValue(mixedInt(target, base + byteOffset, 0), transform, scalar))
						component++
					}
				}
				nestedType != null -> {
					nestedType.struct.members.forEach { nested ->
						transformStructMember(nested, target, base, transform, scalar)
					}
				}
			}
			arrayIndex++
		}
	}

	private fun prefixStructMember(
		member: StructMember<*>,
		source: MixedBuffer,
		target: MixedBuffer,
		sourceStructBase: Int,
		targetStructBase: Int,
		floatSums: MutableMap<String, FloatArray>,
		intSums: MutableMap<String, IntArray>
	) {
		val memberType = member.type
		val count = if (member is StructArrayMember<*>) member.arraySize else 1
		val stride = if (member is StructArrayMember<*>) member.arrayStride else memberType.byteSize
		var arrayIndex = 0
		while (arrayIndex < count) {
			val sourceBase = sourceStructBase + member.byteOffset + arrayIndex * stride
			val targetBase = targetStructBase + member.byteOffset + arrayIndex * stride
			val key = "${member.name}:${member.byteOffset}:$arrayIndex"
			val nestedType = memberType as? GpuType.Struct
			when {
				memberType.isFloat -> {
					val components = typedComponentCount(memberType)
					val sums = floatSums.getOrPut(key) { FloatArray(components) }
					var component = 0
					while (component < components) {
						val byteOffset = component * Float.SIZE_BYTES
						sums[component] += mixedFloat(source, sourceBase + byteOffset, 0.0f)
						setMixedFloat(target, targetBase + byteOffset, sums[component])
						component++
					}
				}
				memberType.isInt || memberType.isUint || memberType.isBool -> {
					val components = typedComponentCount(memberType)
					val sums = intSums.getOrPut(key) { IntArray(components) }
					var component = 0
					while (component < components) {
						val byteOffset = component * Int.SIZE_BYTES
						sums[component] += mixedInt(source, sourceBase + byteOffset, 0)
						setMixedInt(target, targetBase + byteOffset, sums[component])
						component++
					}
				}
				nestedType != null -> {
					nestedType.struct.members.forEach { nested ->
						prefixStructMember(nested, source, target, sourceBase, targetBase, floatSums, intSums)
					}
				}
			}
			arrayIndex++
		}
	}

	private fun reduceStructMember(
		shaderName: String,
		member: StructMember<*>,
		source: MixedBuffer,
		sourceStructBase: Int,
		floatValues: MutableMap<String, FloatArray>,
		intValues: MutableMap<String, IntArray>
	) {
		val memberType = member.type
		val count = if (member is StructArrayMember<*>) member.arraySize else 1
		val stride = if (member is StructArrayMember<*>) member.arrayStride else memberType.byteSize
		var arrayIndex = 0
		while (arrayIndex < count) {
			val sourceBase = sourceStructBase + member.byteOffset + arrayIndex * stride
			val key = "${member.name}:${member.byteOffset}:$arrayIndex"
			val nestedType = memberType as? GpuType.Struct
			when {
				memberType.isFloat -> {
					val components = typedComponentCount(memberType)
					val values = floatValues.getOrPut(key) { FloatArray(components) { reductionInitialFloat(shaderName) } }
					var component = 0
					while (component < components) {
						val byteOffset = component * Float.SIZE_BYTES
						values[component] = reduceFloatValue(shaderName, values[component], mixedFloat(source, sourceBase + byteOffset, 0.0f))
						component++
					}
				}
				memberType.isInt || memberType.isUint || memberType.isBool -> {
					val components = typedComponentCount(memberType)
					val values = intValues.getOrPut(key) { IntArray(components) { reductionInitialInt(shaderName) } }
					var component = 0
					while (component < components) {
						val byteOffset = component * Int.SIZE_BYTES
						values[component] = reduceIntValue(shaderName, values[component], mixedInt(source, sourceBase + byteOffset, 0))
						component++
					}
				}
				nestedType != null -> {
					nestedType.struct.members.forEach { nested ->
						reduceStructMember(shaderName, nested, source, sourceBase, floatValues, intValues)
					}
				}
			}
			arrayIndex++
		}
	}

	private fun writeReducedStruct(
		members: List<StructMember<*>>,
		target: MixedBuffer,
		targetStructBase: Int,
		floatValues: Map<String, FloatArray>,
		intValues: Map<String, IntArray>
	) {
		members.forEach { member ->
			val memberType = member.type
			val count = if (member is StructArrayMember<*>) member.arraySize else 1
			val stride = if (member is StructArrayMember<*>) member.arrayStride else memberType.byteSize
			var arrayIndex = 0
			while (arrayIndex < count) {
				val targetBase = targetStructBase + member.byteOffset + arrayIndex * stride
				val key = "${member.name}:${member.byteOffset}:$arrayIndex"
				val nestedType = memberType as? GpuType.Struct
				when {
					memberType.isFloat -> {
						val values = floatValues[key]
						if (values != null) {
							var component = 0
							while (component < values.size) {
								setMixedFloat(target, targetBase + component * Float.SIZE_BYTES, values[component])
								component++
							}
						}
					}
					memberType.isInt || memberType.isUint || memberType.isBool -> {
						val values = intValues[key]
						if (values != null) {
							var component = 0
							while (component < values.size) {
								setMixedInt(target, targetBase + component * Int.SIZE_BYTES, values[component])
								component++
							}
						}
					}
					nestedType != null -> writeReducedStruct(nestedType.struct.members, target, targetBase, floatValues, intValues)
				}
				arrayIndex++
			}
		}
	}

	private fun clampStructMember(member: StructMember<*>, target: MixedBuffer, structBase: Int, minValue: Float, maxValue: Float) {
		val memberType = member.type
		val count = if (member is StructArrayMember<*>) member.arraySize else 1
		val stride = if (member is StructArrayMember<*>) member.arrayStride else memberType.byteSize
		var arrayIndex = 0
		while (arrayIndex < count) {
			val base = structBase + member.byteOffset + arrayIndex * stride
			val nestedType = memberType as? GpuType.Struct
			when {
				memberType.isFloat -> {
					var component = 0
					val components = typedComponentCount(memberType)
					while (component < components) {
						val byteOffset = component * Float.SIZE_BYTES
						setMixedFloat(target, base + byteOffset, mixedFloat(target, base + byteOffset, 0.0f).coerceIn(minValue, maxValue))
						component++
					}
				}
				memberType.isInt || memberType.isUint || memberType.isBool -> {
					var component = 0
					val components = typedComponentCount(memberType)
					while (component < components) {
						val byteOffset = component * Int.SIZE_BYTES
						setMixedInt(target, base + byteOffset, mixedInt(target, base + byteOffset, 0).coerceIn(minValue.toInt(), maxValue.toInt()))
						component++
					}
				}
				nestedType != null -> {
					nestedType.struct.members.forEach { nested ->
						clampStructMember(nested, target, base, minValue, maxValue)
					}
				}
			}
			arrayIndex++
		}
	}

	private fun writeMixedFloatComponents(target: MixedBuffer, base: Int, type: GpuType, value: Float) {
		var component = 0
		val components = typedComponentCount(type)
		while (component < components) {
			setMixedFloat(target, base + component * Float.SIZE_BYTES, value)
			component++
		}
	}

	private fun writeMixedIntComponents(target: MixedBuffer, base: Int, type: GpuType, value: Int) {
		var component = 0
		val components = typedComponentCount(type)
		while (component < components) {
			setMixedInt(target, base + component * Int.SIZE_BYTES, value)
			component++
		}
	}

	private fun copyFloatElement(source: Float32Buffer, target: Float32Buffer, sourceIndex: Int, targetIndex: Int, stride: Int, components: Int) {
		var component = 0
		while (component < components) {
			target[targetIndex * stride + component] = source[sourceIndex * stride + component]
			component++
		}
	}

	private fun copyIntElement(source: Int32Buffer, target: Int32Buffer, sourceIndex: Int, targetIndex: Int, stride: Int, components: Int) {
		var component = 0
		while (component < components) {
			target[targetIndex * stride + component] = source[sourceIndex * stride + component]
			component++
		}
	}

	private fun rangeElementCount(requested: Int, sourceCapacity: Int, targetCapacity: Int, sourceOffset: Int, targetOffset: Int, stride: Int): Int {
		val safeStride = stride.coerceAtLeast(1)
		val sourceStart = sourceOffset.coerceAtLeast(0) * safeStride
		val targetStart = targetOffset.coerceAtLeast(0) * safeStride
		val sourceAvailable = ((sourceCapacity - sourceStart).coerceAtLeast(0)) / safeStride
		val targetAvailable = ((targetCapacity - targetStart).coerceAtLeast(0)) / safeStride
		val available = minOf(sourceAvailable, targetAvailable)
		return if (requested > 0) minOf(requested, available) else available
	}

	private fun structSize(members: List<StructMember<*>>): Int {
		return members.firstOrNull()?.parent?.structSize?.coerceAtLeast(1) ?: 1
	}

	private fun mixedFloat(buffer: MixedBuffer, byteIndex: Int, fallback: Float): Float {
		return if (byteIndex >= 0 && byteIndex + Float.SIZE_BYTES <= buffer.capacity) buffer.getFloat32(byteIndex) else fallback
	}

	private fun mixedInt(buffer: MixedBuffer, byteIndex: Int, fallback: Int): Int {
		return if (byteIndex >= 0 && byteIndex + Int.SIZE_BYTES <= buffer.capacity) buffer.getInt32(byteIndex) else fallback
	}

	private fun setMixedFloat(buffer: MixedBuffer, byteIndex: Int, value: Float) {
		if (byteIndex >= 0 && byteIndex + Float.SIZE_BYTES <= buffer.capacity) {
			buffer.setFloat32(byteIndex, value)
		}
	}

	private fun setMixedInt(buffer: MixedBuffer, byteIndex: Int, value: Int) {
		if (byteIndex >= 0 && byteIndex + Int.SIZE_BYTES <= buffer.capacity) {
			buffer.setInt32(byteIndex, value)
		}
	}

	private fun fillByteBufferRange(target: Uint8Buffer, elementOffset: Int, elementCount: Int, value: Int) {
		val count = rangeElementCount(elementCount, target.capacity, target.capacity, elementOffset, elementOffset, 1)
		var index = 0
		while (index < count) {
			target[elementOffset + index] = value.coerceIn(0, 255).toUByte()
			index++
		}
	}

	private fun combineFloatValue(shaderName: String, first: Float, second: Float, alpha: Float, scalar: Float): Float {
		return when {
			shaderName.contains("mul") || shaderName.contains("multiply") -> first * second * scalar
			shaderName.contains("min") -> minOf(first, second)
			shaderName.contains("max") -> maxOf(first, second)
			shaderName.contains("sub") -> (first - second) * scalar
			shaderName.contains("add") || shaderName.contains("sum") || shaderName.contains("compose") -> (first + second * alpha) * scalar
			else -> (first * (1.0f - alpha) + second * alpha) * scalar
		}
	}

	private fun combineIntValue(shaderName: String, first: Int, second: Int, alpha: Float, scalar: Float): Int {
		return when {
			shaderName.contains("mul") || shaderName.contains("multiply") -> (first * second * scalar).toInt()
			shaderName.contains("min") -> minOf(first, second)
			shaderName.contains("max") -> maxOf(first, second)
			shaderName.contains("sub") -> ((first - second) * scalar).toInt()
			shaderName.contains("add") || shaderName.contains("sum") || shaderName.contains("compose") -> ((first + second * alpha) * scalar).toInt()
			else -> (first * (1.0f - alpha) + second * alpha).toInt()
		}
	}

	private fun transformFloatValue(value: Float, transform: BufferTransform, scalar: Float): Float {
		return when (transform) {
			BufferTransform.SCALE -> value * scalar
			BufferTransform.INVERT -> 1.0f - value
		}
	}

	private fun transformIntValue(value: Int, transform: BufferTransform, scalar: Float): Int {
		return when (transform) {
			BufferTransform.SCALE -> (value * scalar).toInt()
			BufferTransform.INVERT -> -value
		}
	}

	private fun reductionInitialFloat(shaderName: String): Float {
		return when {
			shaderName.contains("max") -> -Float.MAX_VALUE
			shaderName.contains("min") -> Float.MAX_VALUE
			shaderName.contains("mul") || shaderName.contains("product") -> 1.0f
			else -> 0.0f
		}
	}

	private fun reductionInitialInt(shaderName: String): Int {
		return when {
			shaderName.contains("max") -> Int.MIN_VALUE
			shaderName.contains("min") -> Int.MAX_VALUE
			shaderName.contains("mul") || shaderName.contains("product") -> 1
			else -> 0
		}
	}

	private fun reduceFloatValue(shaderName: String, current: Float, next: Float): Float {
		return when {
			shaderName.contains("max") -> maxOf(current, next)
			shaderName.contains("min") -> minOf(current, next)
			shaderName.contains("mul") || shaderName.contains("product") -> current * next
			else -> current + next
		}
	}

	private fun reduceIntValue(shaderName: String, current: Int, next: Int): Int {
		return when {
			shaderName.contains("max") -> maxOf(current, next)
			shaderName.contains("min") -> minOf(current, next)
			shaderName.contains("mul") || shaderName.contains("product") -> current * next
			else -> current + next
		}
	}

	private enum class BufferTransform {
		SCALE,
		INVERT
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
		return lower.contains("clear") || lower.contains("zero") || lower.contains("reset")
	}

	private fun isFillOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("fill") ||
			lower.contains("constant") ||
			context.pipelineBindings.any { writeName(it.name) } && hasUniform(context, listOf("value", "fillValue", "clearValue", "color", "fillColor"))
	}

	private fun isBlendOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("blend") ||
			lower.contains("mix") ||
			lower.contains("compose") ||
			lower.contains("composite") ||
			lower.contains("add") ||
			lower.contains("multiply") ||
			lower.contains("mul") ||
			lower.contains("sub") ||
			lower.contains("sum")
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

	private fun isPrefixOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("prefix") ||
			lower.contains("scan") ||
			lower.contains("cumulative") ||
			lower.contains("inclusive")
	}

	private fun isReduceOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("reduce") ||
			lower.contains("reduction") ||
			lower.contains("sum") && context.pipelineBindings.filterIsInstance<BindGroupData.StorageBufferBindingData>().size > 1 ||
			lower.contains("product") ||
			lower.contains("min") ||
			lower.contains("max")
	}

	private fun isScatterGatherOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("scatter") ||
			lower.contains("gather") ||
			lower.contains("compact") ||
			lower.contains("reorder") ||
			context.pipelineBindings.any { indexName(it.name) }
	}

	private fun isBufferClampOperation(context: BlazeKoolComputeContext): Boolean {
		val lower = shaderName(context)
		return lower.contains("clamp") ||
			lower.contains("saturate") ||
			lower.contains("threshold")
	}

	private fun writeName(name: String): Boolean {
		val lower = name.lowercase()
		return lower.contains("write") || lower.contains("dst") || lower.contains("target") || lower.contains("output")
	}

	private fun indexName(name: String): Boolean {
		val lower = name.lowercase()
		return lower.contains("index") || lower.contains("indices") || lower.contains("lookup") || lower.contains("map")
	}

	private fun hasUniform(context: BlazeKoolComputeContext, names: List<String>): Boolean {
		var index = 0
		while (index < names.size) {
			if (uniformMember(context, names[index]) != null) {
				return true
			}
			index++
		}
		return false
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
