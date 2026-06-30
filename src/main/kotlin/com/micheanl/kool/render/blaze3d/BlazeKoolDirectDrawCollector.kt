package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveType
import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.mojang.blaze3d.IndexType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.AddressMode
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.textures.GpuSampler
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.DrawCommand
import de.fabmax.kool.pipeline.GpuType
import de.fabmax.kool.pipeline.SamplerSettings
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.BindGroupData.StorageTexture1dBindingData
import de.fabmax.kool.pipeline.BindGroupData.StorageTexture2dBindingData
import de.fabmax.kool.pipeline.BindGroupData.StorageTexture3dBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture1dBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture2dArrayBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture2dBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture3dBindingData
import de.fabmax.kool.pipeline.BindGroupData.TextureCubeArrayBindingData
import de.fabmax.kool.pipeline.BindGroupData.TextureCubeBindingData
import de.fabmax.kool.pipeline.FilterMethod
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.geometry.PrimitiveType
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Float4Member
import de.fabmax.kool.util.LongHash
import de.fabmax.kool.util.MixedBuffer
import de.fabmax.kool.util.StructMember
import de.fabmax.kool.util.getByName
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BlazeKoolDirectDrawCollector(
	private val resources: BlazeKoolResourceManager
) {
	private val cache = HashMap<DrawCacheKey, BlazeKoolDirectDrawCommand>()
	private val liveKeys = HashSet<DrawCacheKey>()

	fun beginFrame() {
		liveKeys.clear()
	}

	fun collect(command: DrawCommand): BlazeKoolDirectDrawCommand? {
		if (!command.isActive || command.vertexData.numIndices <= 0) {
			return null
		}
		val primitiveType = command.vertexData.primitiveType.toDirectBlazeKoolPrimitiveType() ?: return null
		val metadata = (command.pipeline.shaderCode as? BlazeKoolShaderMetadataSource)?.metadata ?: return null
		val shaderPipeline = metadata.shaderPipeline ?: return null
		val firstTexture = firstTexture(command, metadata)
		firstTexture.texture?.let(resources::ensureTextureResource)
		val state = renderState(command, primitiveType, firstTexture, shaderPipeline)
		val key = drawCacheKey(command, state, firstTexture)
		liveKeys += key
		val cached = cache[key]
		if (cached != null) {
			cached.replaceBindings(
				uniformBindings(command.pipeline.capturedPipelineData.bufferedBindings),
				textureBindings(firstTexture)
			)
			return cached
		}
		val bindings = command.pipeline.capturedPipelineData.bufferedBindings
		val bindGroupLayout = bindGroupLayout()
		val directPipeline = BlazeKoolDirectPipelines.pipeline(state, bindGroupLayout)
		val commandData = buildCommand(command, state, directPipeline, bindings, bindGroupLayout, firstTexture)
		cache[key] = commandData
		return commandData
	}

	fun endFrame() {
		val iterator = cache.entries.iterator()
		while (iterator.hasNext()) {
			val entry = iterator.next()
			if (entry.key !in liveKeys) {
				entry.value.close()
				iterator.remove()
			}
		}
		liveKeys.clear()
	}

	fun clear() {
		val iterator = cache.values.iterator()
		while (iterator.hasNext()) {
			iterator.next().close()
		}
		cache.clear()
		liveKeys.clear()
	}

	private fun buildCommand(
		command: DrawCommand,
		state: BlazeKoolRenderState,
		pipeline: RenderPipeline,
		bindings: List<BindGroupData.BindingData>,
		bindGroupLayout: BindGroupLayout,
		firstTexture: TextureBinding
	): BlazeKoolDirectDrawCommand {
		val vertexBufferData = vertexBuffer(command)
		val indexCount = directIndexCount(command)
		val indexBufferData = indexBuffer(indexCount)
		val device = RenderSystem.getDevice()
		val vertexBuffer = device.createBuffer(
			{ "BlazeKool Direct Vertices" },
			GpuBuffer.USAGE_VERTEX or GpuBuffer.USAGE_COPY_DST,
			vertexBufferData
		)
		val indexBuffer = device.createBuffer(
			{ "BlazeKool Direct Indices" },
			GpuBuffer.USAGE_INDEX or GpuBuffer.USAGE_COPY_DST,
			indexBufferData
		)
		return BlazeKoolDirectDrawCommand(
			renderState = state,
			pipeline = pipeline,
			vertexBuffer = vertexBuffer,
			indexBuffer = indexBuffer,
			indexType = IndexType.INT,
			indexCount = indexCount,
			uniformBindings = uniformBindings(bindings),
			textureBindings = textureBindings(firstTexture),
			bindGroupLayout = bindGroupLayout
		)
	}

	private fun vertexBuffer(command: DrawCommand): ByteBuffer {
		val geometry = command.vertexData
		val instanceCount = command.instanceData?.numInstances?.coerceAtLeast(1) ?: 1
		val data = ByteBuffer.allocateDirect(directVertexCount(command) * instanceCount * DIRECT_VERTEX_SIZE).order(ByteOrder.nativeOrder())
		val modelMatrix = MutableMat4f()
		val transformedPosition = MutableVec3f()
		val transformedNormal = MutableVec3f()
		val materialColor = materialColor(command)
		val instanceAccessor = InstanceDataAccessor.create(command.instanceData)
		var instanceIndex = 0
		while (instanceIndex < instanceCount) {
			val transform = instanceAccessor?.modelMatrix(command.modelMatF, instanceIndex, modelMatrix) ?: command.modelMatF
			val instanceColor = instanceAccessor?.color(instanceIndex)
			appendVertices(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal)
			instanceIndex++
		}
		data.flip()
		return data
	}

	private fun appendVertices(
		command: DrawCommand,
		transform: Mat4f,
		instanceColor: MutableVec4f?,
		materialColor: MutableVec4f,
		data: ByteBuffer,
		transformedPosition: MutableVec3f,
		transformedNormal: MutableVec3f
	) {
		val geometry = command.vertexData
		when (geometry.primitiveType) {
			PrimitiveType.TRIANGLE_STRIP -> appendTriangleStripVertices(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal)
			else -> {
				var index = 0
				while (index < geometry.numIndices) {
					appendIndexedVertex(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal, geometry.indices[index])
					index++
				}
			}
		}
	}

	private fun appendTriangleStripVertices(
		command: DrawCommand,
		transform: Mat4f,
		instanceColor: MutableVec4f?,
		materialColor: MutableVec4f,
		data: ByteBuffer,
		transformedPosition: MutableVec3f,
		transformedNormal: MutableVec3f
	) {
		val geometry = command.vertexData
		var index = 2
		while (index < geometry.numIndices) {
			val first = geometry.indices[index - 2]
			val second = geometry.indices[index - 1]
			val third = geometry.indices[index]
			if (index and 1 == 0) {
				appendIndexedVertex(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal, first)
				appendIndexedVertex(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal, second)
				appendIndexedVertex(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal, third)
			} else {
				appendIndexedVertex(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal, second)
				appendIndexedVertex(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal, first)
				appendIndexedVertex(command, transform, instanceColor, materialColor, data, transformedPosition, transformedNormal, third)
			}
			index++
		}
	}

	private fun appendIndexedVertex(
		command: DrawCommand,
		transform: Mat4f,
		instanceColor: MutableVec4f?,
		materialColor: MutableVec4f,
		data: ByteBuffer,
		transformedPosition: MutableVec3f,
		transformedNormal: MutableVec3f,
		vertexIndex: Int
	) {
		val geometry = command.vertexData
		geometry.vertexIt.index = vertexIndex
		val position = geometry.vertexIt.position
		val normal = geometry.vertexIt.normal
		val color = geometry.vertexIt.color
		val texCoord = geometry.vertexIt.texCoord
		transform.transform(position, 1.0f, transformedPosition)
		transform.transform(normal, 0.0f, transformedNormal)
		writeVertex(
			data = data,
			x = transformedPosition.x,
			y = transformedPosition.y,
			z = transformedPosition.z,
			u = texCoord.x,
			v = texCoord.y,
			color = vertexColor(color.r, color.g, color.b, color.a, instanceColor, materialColor),
			nx = transformedNormal.x,
			ny = transformedNormal.y,
			nz = transformedNormal.z
		)
	}

	private fun writeVertex(data: ByteBuffer, x: Float, y: Float, z: Float, u: Float, v: Float, color: Int, nx: Float, ny: Float, nz: Float) {
		data.putFloat(x)
		data.putFloat(y)
		data.putFloat(z)
		data.putFloat(u)
		data.putFloat(v)
		data.putInt(argbToAbgr(color))
		data.put(normalByte(nx))
		data.put(normalByte(ny))
		data.put(normalByte(nz))
		data.put(0)
	}

	private fun indexBuffer(indexCount: Int): ByteBuffer {
		val data = ByteBuffer.allocateDirect(indexCount * Int.SIZE_BYTES).order(ByteOrder.nativeOrder())
		var index = 0
		while (index < indexCount) {
			data.putInt(index)
			index++
		}
		data.flip()
		return data
	}

	private fun directVertexCount(command: DrawCommand): Int {
		val geometry = command.vertexData
		return if (geometry.primitiveType == PrimitiveType.TRIANGLE_STRIP) {
			(geometry.numIndices - 2).coerceAtLeast(0) * 3
		} else {
			geometry.numIndices
		}
	}

	private fun directIndexCount(command: DrawCommand): Int {
		val instanceCount = command.instanceData?.numInstances?.coerceAtLeast(1) ?: 1
		return directVertexCount(command) * instanceCount
	}

	private fun uniformBindings(bindings: List<BindGroupData.BindingData>): List<BlazeKoolDirectDrawCommand.UniformBinding> {
		val uniforms = ArrayList<BlazeKoolDirectDrawCommand.UniformBinding>()
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding is BindGroupData.UniformBufferBindingData<*>) {
				val buffer = resources.uniformBuffer(binding.name, binding.buffer)
				if (buffer != null) {
					uniforms += BlazeKoolDirectDrawCommand.UniformBinding(binding.name, buffer.slice())
				}
			}
			index++
		}
		return uniforms
	}

	private fun textureBindings(firstTexture: TextureBinding): List<BlazeKoolDirectDrawCommand.TextureBinding> {
		val textures = ArrayList<BlazeKoolDirectDrawCommand.TextureBinding>()
		val texture = firstTexture.texture
		if (texture != null) {
			resources.ensureTextureResource(texture)
			val textureView = resources.textureView(texture, firstTexture.slice)
			if (textureView != null) {
				textures += BlazeKoolDirectDrawCommand.TextureBinding("Sampler0", textureView, sampler(firstTexture.sampler))
			}
		}
		return textures
	}

	private fun bindGroupLayout(): BindGroupLayout {
		return BindGroupLayout.builder()
			.withSampler("Sampler0")
			.build()
	}

	private fun renderState(
		command: DrawCommand,
		primitiveType: BlazeKoolPrimitiveType,
		textureBinding: TextureBinding,
		shaderPipeline: BlazeKoolShaderPipeline
	): BlazeKoolRenderState {
		val pipeline = command.pipeline
		return BlazeKoolRenderState(
			primitiveType = primitiveType,
			blendMode = pipeline.blendMode,
			cullMethod = pipeline.cullMethod,
			depthCompareOp = reversedDepthCompareOp(pipeline.depthCompareOp, pipeline.autoReverseDepthFunc),
			writeDepth = pipeline.isWriteDepth,
			lineWidth = pipeline.lineWidth,
			texture = resources.textureLocation(textureBinding.texture, textureBinding.slice),
			textured = textureBinding.texture != null,
			shaderPipeline = shaderPipeline
		)
	}

	private fun reversedDepthCompareOp(compareOp: DepthCompareOp, autoReverseDepthFunc: Boolean): DepthCompareOp {
		if (!autoReverseDepthFunc) {
			return compareOp
		}
		return when (compareOp) {
			DepthCompareOp.LESS -> DepthCompareOp.GREATER
			DepthCompareOp.LESS_EQUAL -> DepthCompareOp.GREATER_EQUAL
			DepthCompareOp.GREATER -> DepthCompareOp.LESS
			DepthCompareOp.GREATER_EQUAL -> DepthCompareOp.LESS_EQUAL
			else -> compareOp
		}
	}

	private fun firstTexture(command: DrawCommand, metadata: BlazeKoolShaderMetadata): TextureBinding {
		val bindings = command.pipeline.capturedPipelineData.bufferedBindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			val textureBinding = textureBinding(binding, metadata)
			if (textureBinding != null) {
				return textureBinding
			}
			index++
		}
		return TextureBinding(null, BlazeKoolTextureSlice.DEFAULT, SamplerSettings())
	}

	private fun textureBinding(binding: BindGroupData.BindingData, metadata: BlazeKoolShaderMetadata = BlazeKoolShaderMetadata.EMPTY): TextureBinding? {
		return when (binding) {
			is Texture1dBindingData -> binding.texture?.let { TextureBinding(it, BlazeKoolTextureSlice.DEFAULT, binding.sampler ?: SamplerSettings()) }
			is Texture2dBindingData -> binding.texture?.let { TextureBinding(it, BlazeKoolTextureSlice.DEFAULT, binding.sampler ?: SamplerSettings()) }
			is Texture3dBindingData -> binding.texture?.let { TextureBinding(it, BlazeKoolTextureSlice.DEFAULT, binding.sampler ?: SamplerSettings()) }
			is TextureCubeBindingData -> binding.texture?.let { TextureBinding(it, BlazeKoolTextureSlice(face = CUBE_DEFAULT_FACE), binding.sampler ?: SamplerSettings()) }
			is Texture2dArrayBindingData -> binding.texture?.let {
				TextureBinding(it, BlazeKoolTextureSlice(layer = metadata.textureArrayLayers[binding.name] ?: 0), binding.sampler ?: SamplerSettings())
			}
			is TextureCubeArrayBindingData -> binding.texture?.let {
				TextureBinding(it, BlazeKoolTextureSlice(layer = metadata.textureArrayLayers[binding.name] ?: 0, face = CUBE_DEFAULT_FACE), binding.sampler ?: SamplerSettings())
			}
			is StorageTexture1dBindingData -> binding.storageTexture?.asTexture?.let {
				TextureBinding(it, BlazeKoolTextureSlice(mipLevel = binding.mipLevel), SamplerSettings())
			}
			is StorageTexture2dBindingData -> binding.storageTexture?.asTexture?.let {
				TextureBinding(it, BlazeKoolTextureSlice(mipLevel = binding.mipLevel), SamplerSettings())
			}
			is StorageTexture3dBindingData -> binding.storageTexture?.asTexture?.let {
				TextureBinding(it, BlazeKoolTextureSlice(mipLevel = binding.mipLevel), SamplerSettings())
			}
			else -> null
		}
	}

	private fun sampler(settings: SamplerSettings): GpuSampler {
		return RenderSystem.getSamplerCache().getSampler(
			minecraftAddressMode(settings.addressModeU),
			minecraftAddressMode(settings.addressModeV),
			minecraftFilterMode(settings.minFilter),
			minecraftFilterMode(settings.magFilter),
			settings.numMipLevels > 1
		)
	}

	private fun minecraftAddressMode(mode: de.fabmax.kool.pipeline.AddressMode): AddressMode {
		return when (mode) {
			de.fabmax.kool.pipeline.AddressMode.REPEAT -> AddressMode.REPEAT
			de.fabmax.kool.pipeline.AddressMode.MIRRORED_REPEAT -> AddressMode.REPEAT
			de.fabmax.kool.pipeline.AddressMode.CLAMP_TO_EDGE -> AddressMode.CLAMP_TO_EDGE
		}
	}

	private fun minecraftFilterMode(mode: FilterMethod): FilterMode {
		return when (mode) {
			FilterMethod.NEAREST -> FilterMode.NEAREST
			FilterMethod.LINEAR -> FilterMode.LINEAR
		}
	}

	private fun materialColor(command: DrawCommand): MutableVec4f {
		val result = MutableVec4f(Color.WHITE.r, Color.WHITE.g, Color.WHITE.b, Color.WHITE.a)
		val bindings = command.pipeline.capturedPipelineData.bufferedBindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding is BindGroupData.UniformBufferBindingData<*> && readMaterialColor(binding, result)) {
				return result
			}
			index++
		}
		return result
	}

	private fun readMaterialColor(binding: BindGroupData.UniformBufferBindingData<*>, result: MutableVec4f): Boolean {
		val members = binding.buffer.struct.members
		var priority = MATERIAL_COLOR_NAMES.size
		var memberIndex = 0
		var selected: Float4Member<*>? = null
		while (memberIndex < members.size) {
			val member = members[memberIndex]
			if (member is Float4Member<*>) {
				val nextPriority = materialColorPriority(member.name)
				if (nextPriority >= 0 && nextPriority < priority) {
					priority = nextPriority
					selected = member
				}
			}
			memberIndex++
		}
		val member = selected ?: return false
		val offset = member.byteOffset
		val data = binding.buffer.buffer
		result.set(
			data.getFloat32(offset),
			data.getFloat32(offset + Float.SIZE_BYTES),
			data.getFloat32(offset + Float.SIZE_BYTES * 2),
			data.getFloat32(offset + Float.SIZE_BYTES * 3)
		)
		return true
	}

	private fun materialColorPriority(name: String): Int {
		val normalized = name.lowercase()
		var index = 0
		while (index < MATERIAL_COLOR_NAMES.size) {
			if (normalized == MATERIAL_COLOR_NAMES[index]) {
				return index
			}
			index++
		}
		return -1
	}

	private fun vertexColor(red: Float, green: Float, blue: Float, alpha: Float, instanceColor: MutableVec4f?, materialColor: MutableVec4f): Int {
		return if (instanceColor == null) {
			color(red * materialColor.x, green * materialColor.y, blue * materialColor.z, alpha * materialColor.w)
		} else {
			color(
				red * instanceColor.x * materialColor.x,
				green * instanceColor.y * materialColor.y,
				blue * instanceColor.z * materialColor.z,
				alpha * instanceColor.w * materialColor.w
			)
		}
	}

	private fun color(red: Float, green: Float, blue: Float, alpha: Float): Int {
		val a = colorChannel(alpha)
		val r = colorChannel(red)
		val g = colorChannel(green)
		val b = colorChannel(blue)
		return a shl 24 or (r shl 16) or (g shl 8) or b
	}

	private fun colorChannel(value: Float): Int = (value.coerceIn(0.0f, 1.0f) * 255.0f).toInt().coerceIn(0, 255)

	private fun argbToAbgr(color: Int): Int {
		val alpha = color and -0x1000000
		val red = color and 0x00ff0000
		val green = color and 0x0000ff00
		val blue = color and 0x000000ff
		return alpha or (blue shl 16) or green or (red ushr 16)
	}

	private fun normalByte(value: Float): Byte {
		return (value.coerceIn(-1.0f, 1.0f) * 127.0f).toInt().coerceIn(-128, 127).toByte()
	}

	private fun drawCacheKey(command: DrawCommand, state: BlazeKoolRenderState, textureBinding: TextureBinding): DrawCacheKey {
		val geometry = command.vertexData
		val instances = command.instanceData
		return DrawCacheKey(
			geometry = geometry,
			geometryModCount = geometry.modCount.count,
			instanceModCount = instances?.modCount?.count ?: NO_INSTANCE_MOD_COUNT,
			instanceCount = instances?.numInstances ?: 1,
			instanceLayoutHash = instances?.layout?.hash ?: NO_INSTANCE_LAYOUT_HASH,
			matrixHash = matrixHash(command.modelMatF),
			pipelineHash = command.pipeline.pipelineHash,
			state = state,
			textureSlice = textureBinding.slice,
			materialColorHash = colorHash(materialColor(command))
		)
	}

	private fun matrixHash(matrix: Mat4f): LongHash = LongHash {
		this += matrix.m00
		this += matrix.m01
		this += matrix.m02
		this += matrix.m03
		this += matrix.m10
		this += matrix.m11
		this += matrix.m12
		this += matrix.m13
		this += matrix.m20
		this += matrix.m21
		this += matrix.m22
		this += matrix.m23
		this += matrix.m30
		this += matrix.m31
		this += matrix.m32
		this += matrix.m33
	}

	private fun colorHash(color: MutableVec4f): LongHash = LongHash {
		this += color.x
		this += color.y
		this += color.z
		this += color.w
	}

	private fun PrimitiveType.toDirectBlazeKoolPrimitiveType(): BlazeKoolPrimitiveType? {
		return when (this) {
			PrimitiveType.TRIANGLES -> BlazeKoolPrimitiveType.TRIANGLES
			PrimitiveType.TRIANGLE_STRIP -> BlazeKoolPrimitiveType.TRIANGLES
			PrimitiveType.LINES -> BlazeKoolPrimitiveType.LINES
			PrimitiveType.POINTS -> BlazeKoolPrimitiveType.POINTS
		}
	}

	private data class DrawCacheKey(
		val geometry: Any,
		val geometryModCount: Int,
		val instanceModCount: Int,
		val instanceCount: Int,
		val instanceLayoutHash: LongHash,
		val matrixHash: LongHash,
		val pipelineHash: LongHash,
		val state: BlazeKoolRenderState,
		val textureSlice: BlazeKoolTextureSlice,
		val materialColorHash: LongHash
	)

	private class TextureBinding(
		val texture: Texture<*>?,
		val slice: BlazeKoolTextureSlice,
		val sampler: SamplerSettings
	)

	private class InstanceDataAccessor(
		private val instances: MeshInstanceList<*>,
		private val modelMatrixMember: StructMember<*>?,
		private val colorMember: StructMember<*>?
	) {
		private val data: MixedBuffer = instances.instanceData.buffer
		private val strideBytes: Int = instances.instanceData.strideBytes
		private val instanceMatrix = MutableMat4f()
		private val instanceColor = MutableVec4f()

		fun modelMatrix(meshMatrix: Mat4f, instanceIndex: Int, result: MutableMat4f): Mat4f {
			if (modelMatrixMember == null) {
				return meshMatrix
			}
			readMatrix(instanceIndex, modelMatrixMember.byteOffset, instanceMatrix)
			return meshMatrix.mul(instanceMatrix, result)
		}

		fun color(instanceIndex: Int): MutableVec4f? {
			if (colorMember == null) {
				return null
			}
			val offset = instanceOffset(instanceIndex) + colorMember.byteOffset
			return instanceColor.set(
				data.getFloat32(offset),
				data.getFloat32(offset + Float.SIZE_BYTES),
				data.getFloat32(offset + Float.SIZE_BYTES * 2),
				data.getFloat32(offset + Float.SIZE_BYTES * 3)
			)
		}

		private fun readMatrix(instanceIndex: Int, memberOffset: Int, result: MutableMat4f): MutableMat4f {
			val offset = instanceOffset(instanceIndex) + memberOffset
			return result.set(
				data.getFloat32(offset), data.getFloat32(offset + 16), data.getFloat32(offset + 32), data.getFloat32(offset + 48),
				data.getFloat32(offset + 4), data.getFloat32(offset + 20), data.getFloat32(offset + 36), data.getFloat32(offset + 52),
				data.getFloat32(offset + 8), data.getFloat32(offset + 24), data.getFloat32(offset + 40), data.getFloat32(offset + 56),
				data.getFloat32(offset + 12), data.getFloat32(offset + 28), data.getFloat32(offset + 44), data.getFloat32(offset + 60)
			)
		}

		private fun instanceOffset(instanceIndex: Int): Int = instanceIndex * strideBytes

		companion object {
			fun create(instances: MeshInstanceList<*>?): InstanceDataAccessor? {
				if (instances == null || instances.numInstances <= 0) {
					return null
				}
				val modelMatrixMember = instances.layout.getByName(InstanceLayouts.ModelMat.modelMat.name, GpuType.Mat4)
				val colorMember = instances.layout.getByName(InstanceLayouts.Color.color.name, GpuType.Float4)
				return InstanceDataAccessor(instances, modelMatrixMember, colorMember)
			}
		}
	}

	private companion object {
		const val DIRECT_VERTEX_SIZE: Int = 36
		const val NO_INSTANCE_MOD_COUNT: Int = -1
		const val CUBE_DEFAULT_FACE: Int = 4
		val NO_INSTANCE_LAYOUT_HASH: LongHash = LongHash { this += 0 }
		val MATERIAL_COLOR_NAMES: List<String> = listOf(
			"ubasecolor",
			"basecolor",
			"ualbedo",
			"albedo",
			"ucolor",
			"color",
			"utint",
			"tint"
		)
	}
}
