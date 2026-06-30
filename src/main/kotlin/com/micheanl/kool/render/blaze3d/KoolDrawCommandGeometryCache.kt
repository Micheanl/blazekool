package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveGeometry
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveType
import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.micheanl.kool.api.geometry.BlazeKoolVertex
import com.micheanl.kool.api.geometry.blazeKoolColor
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableMat4f
import de.fabmax.kool.math.MutableVec4f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.DrawCommand
import de.fabmax.kool.pipeline.GpuType
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.BindGroupData.StorageTexture1dBindingData
import de.fabmax.kool.pipeline.BindGroupData.StorageTexture2dBindingData
import de.fabmax.kool.pipeline.BindGroupData.StorageTexture3dBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture1dBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture2dBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture2dArrayBindingData
import de.fabmax.kool.pipeline.BindGroupData.Texture3dBindingData
import de.fabmax.kool.pipeline.BindGroupData.TextureCubeArrayBindingData
import de.fabmax.kool.pipeline.BindGroupData.TextureCubeBindingData
import de.fabmax.kool.scene.InstanceLayouts
import de.fabmax.kool.scene.MeshInstanceList
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.scene.geometry.PrimitiveType
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.Float4Member
import de.fabmax.kool.util.LongHash
import de.fabmax.kool.util.MixedBuffer
import de.fabmax.kool.util.StructMember
import de.fabmax.kool.util.getByName
import net.minecraft.resources.Identifier
import java.util.IdentityHashMap

class KoolDrawCommandGeometryCache(
	private val resources: BlazeKoolResourceManager
) {
	private val cache = IdentityHashMap<IndexedVertexList<*>, MutableList<CachedGeometry>>()

	fun getOrCreate(command: DrawCommand): BlazeKoolGeometry? {
		val geometry = command.vertexData
		if (geometry.numIndices <= 0) {
			return null
		}
		val primitiveType = geometry.primitiveType.toBlazeKoolPrimitiveType() ?: return null
		val textureBinding = firstTexture(command)
		if (textureBinding.texture != null) {
			resources.ensureTextureResource(textureBinding.texture)
		}
		val state = renderState(command, primitiveType, textureBinding)
		val matrixHash = matrixHash(command.modelMatF)
		val textureLocation = resources.textureLocation(textureBinding.texture, textureBinding.slice)
		val instances = command.instanceData
		val instanceModCount = instances?.modCount?.count ?: NO_INSTANCE_MOD_COUNT
		val instanceCount = instances?.numInstances ?: 1
		val instanceLayoutHash = instances?.layout?.hash ?: NO_INSTANCE_LAYOUT_HASH
		val materialColor = materialColor(command)
		val materialColorHash = colorHash(materialColor)

		val cachedList = cache[geometry]
		val cached = cachedList?.firstOrNull { cachedGeometry ->
			geometry.modCount.isNotDirty(cachedGeometry.modCount) &&
				cachedGeometry.instanceModCount == instanceModCount &&
				cachedGeometry.instanceCount == instanceCount &&
				cachedGeometry.instanceLayoutHash == instanceLayoutHash &&
				cachedGeometry.materialColorHash == materialColorHash &&
				cachedGeometry.matrixHash == matrixHash &&
				cachedGeometry.pipelineHash == command.pipeline.pipelineHash &&
				cachedGeometry.renderState == state &&
				cachedGeometry.textureLocation == textureLocation &&
				cachedGeometry.textureSlice == textureBinding.slice
		}
		if (
			cached != null
		) {
			return cached.geometry
		}

		val converted = convert(command, state, materialColor)
		val cachedGeometry = CachedGeometry(
			modCount = geometry.modCount.count,
			instanceModCount = instanceModCount,
			instanceCount = instanceCount,
			instanceLayoutHash = instanceLayoutHash,
			materialColorHash = materialColorHash,
			matrixHash = matrixHash,
			pipelineHash = command.pipeline.pipelineHash,
			renderState = state,
			textureLocation = textureLocation,
			textureSlice = textureBinding.slice,
			geometry = converted
		)
		if (cachedList == null) {
			cache[geometry] = arrayListOf(cachedGeometry)
		} else {
			cachedList.removeAll { existing ->
				existing.pipelineHash == cachedGeometry.pipelineHash &&
					existing.instanceLayoutHash == cachedGeometry.instanceLayoutHash &&
					existing.textureLocation == cachedGeometry.textureLocation &&
					existing.textureSlice == cachedGeometry.textureSlice &&
					existing.renderState == cachedGeometry.renderState
			}
			cachedList += cachedGeometry
		}
		return converted
	}

	fun clear() {
		cache.clear()
	}

	private fun convert(command: DrawCommand, state: BlazeKoolRenderState, materialColor: MutableVec4f): BlazeKoolPrimitiveGeometry {
		val geometry = command.vertexData
		val instances = command.instanceData
		val instanceCount = instances?.numInstances?.coerceAtLeast(1) ?: 1
		val vertices = ArrayList<BlazeKoolVertex>(geometry.numIndices * instanceCount)
		val instanceAccessor = InstanceDataAccessor.create(instances)
		val modelMatrix = MutableMat4f()
		val transformedPosition = MutableVec3f()
		val transformedNormal = MutableVec3f()
		var instanceIndex = 0
		while (instanceIndex < instanceCount) {
			val transform = instanceAccessor?.modelMatrix(command.modelMatF, instanceIndex, modelMatrix) ?: command.modelMatF
			val instanceColor = instanceAccessor?.color(instanceIndex)
			appendVertices(command, transform, instanceColor, materialColor, vertices, transformedPosition, transformedNormal)
			instanceIndex++
		}
		return BlazeKoolPrimitiveGeometry(vertices, state)
	}

	private fun appendVertices(
		command: DrawCommand,
		transform: Mat4f,
		instanceColor: MutableVec4f?,
		materialColor: MutableVec4f,
		vertices: MutableList<BlazeKoolVertex>,
		transformedPosition: MutableVec3f,
		transformedNormal: MutableVec3f
	) {
		val geometry = command.vertexData
		var index = 0
		while (index < geometry.numIndices) {
			val vertexIndex = geometry.indices[index]
			geometry.vertexIt.index = vertexIndex
			val position = geometry.vertexIt.position
			val normal = geometry.vertexIt.normal
			val color = geometry.vertexIt.color
			val texCoord = geometry.vertexIt.texCoord
			transform.transform(position, 1.0f, transformedPosition)
			transform.transform(normal, 0.0f, transformedNormal)
			val vertexColor = if (geometry.colorAttr != null) {
				vertexColor(color.r, color.g, color.b, color.a, instanceColor, materialColor)
			} else {
				vertexColor(Color.WHITE.r, Color.WHITE.g, Color.WHITE.b, Color.WHITE.a, instanceColor, materialColor)
			}
			vertices += BlazeKoolVertex(
				x = transformedPosition.x,
				y = transformedPosition.y,
				z = transformedPosition.z,
				color = vertexColor,
				nx = transformedNormal.x,
				ny = transformedNormal.y,
				nz = transformedNormal.z,
				u = texCoord.x,
				v = texCoord.y
			)
			index++
		}
	}

	private fun vertexColor(red: Float, green: Float, blue: Float, alpha: Float, instanceColor: MutableVec4f?, materialColor: MutableVec4f): Int {
		return if (instanceColor == null) {
			blazeKoolColor(red * materialColor.x, green * materialColor.y, blue * materialColor.z, alpha * materialColor.w)
		} else {
			blazeKoolColor(
				red * instanceColor.x * materialColor.x,
				green * instanceColor.y * materialColor.y,
				blue * instanceColor.z * materialColor.z,
				alpha * instanceColor.w * materialColor.w
			)
		}
	}

	private fun renderState(command: DrawCommand, primitiveType: BlazeKoolPrimitiveType, textureBinding: TextureBinding): BlazeKoolRenderState {
		val pipeline = command.pipeline
		val metadata = (command.pipeline.shaderCode as? BlazeKoolShaderMetadataSource)?.metadata ?: BlazeKoolShaderMetadata.EMPTY
		return BlazeKoolRenderState(
			primitiveType = primitiveType,
			blendMode = pipeline.blendMode,
			cullMethod = pipeline.cullMethod,
			depthCompareOp = reversedDepthCompareOp(pipeline.depthCompareOp, pipeline.autoReverseDepthFunc),
			writeDepth = pipeline.isWriteDepth,
			lineWidth = pipeline.lineWidth,
			texture = resources.textureLocation(textureBinding.texture, textureBinding.slice),
			textured = textureBinding.texture != null,
			shaderPipeline = metadata.shaderPipeline
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

	private fun firstTexture(command: DrawCommand): TextureBinding {
		val bindings = command.pipeline.capturedPipelineData.bufferedBindings
		val metadata = (command.pipeline.shaderCode as? BlazeKoolShaderMetadataSource)?.metadata ?: BlazeKoolShaderMetadata.EMPTY
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			when (binding) {
				is Texture1dBindingData -> binding.texture?.let { return TextureBinding(it, BlazeKoolTextureSlice.DEFAULT) }
				is Texture2dBindingData -> binding.texture?.let { return TextureBinding(it, BlazeKoolTextureSlice.DEFAULT) }
				is Texture3dBindingData -> binding.texture?.let { return TextureBinding(it, BlazeKoolTextureSlice.DEFAULT) }
				is TextureCubeBindingData -> binding.texture?.let { return TextureBinding(it, BlazeKoolTextureSlice(face = CUBE_DEFAULT_FACE)) }
				is Texture2dArrayBindingData -> binding.texture?.let {
					return TextureBinding(it, BlazeKoolTextureSlice(layer = metadata.textureArrayLayers[binding.name] ?: 0))
				}
				is TextureCubeArrayBindingData -> binding.texture?.let {
					return TextureBinding(it, BlazeKoolTextureSlice(layer = metadata.textureArrayLayers[binding.name] ?: 0, face = CUBE_DEFAULT_FACE))
				}
				is StorageTexture1dBindingData -> binding.storageTexture?.asTexture?.let {
					return TextureBinding(it, BlazeKoolTextureSlice(mipLevel = binding.mipLevel))
				}
				is StorageTexture2dBindingData -> binding.storageTexture?.asTexture?.let {
					return TextureBinding(it, BlazeKoolTextureSlice(mipLevel = binding.mipLevel))
				}
				is StorageTexture3dBindingData -> binding.storageTexture?.asTexture?.let {
					return TextureBinding(it, BlazeKoolTextureSlice(mipLevel = binding.mipLevel))
				}
				else -> Unit
			}
			index++
		}
		return TextureBinding(null, BlazeKoolTextureSlice.DEFAULT)
	}

	private fun materialColor(command: DrawCommand): MutableVec4f {
		val result = MutableVec4f(Color.WHITE.r, Color.WHITE.g, Color.WHITE.b, Color.WHITE.a)
		val bindings = command.pipeline.capturedPipelineData.bufferedBindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding is BindGroupData.UniformBufferBindingData<*>) {
				if (readMaterialColor(binding, result)) {
					return result
				}
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

	private fun PrimitiveType.toBlazeKoolPrimitiveType(): BlazeKoolPrimitiveType? {
		return when (this) {
			PrimitiveType.TRIANGLES -> BlazeKoolPrimitiveType.TRIANGLES
			PrimitiveType.TRIANGLE_STRIP -> BlazeKoolPrimitiveType.TRIANGLE_STRIP
			PrimitiveType.LINES -> BlazeKoolPrimitiveType.LINES
			PrimitiveType.POINTS -> BlazeKoolPrimitiveType.POINTS
		}
	}

	private class CachedGeometry(
		val modCount: Int,
		val instanceModCount: Int,
		val instanceCount: Int,
		val instanceLayoutHash: LongHash,
		val materialColorHash: LongHash,
		val matrixHash: LongHash,
		val pipelineHash: LongHash,
		val renderState: BlazeKoolRenderState,
		val textureLocation: Identifier?,
		val textureSlice: BlazeKoolTextureSlice,
		val geometry: BlazeKoolPrimitiveGeometry
	)

	private data class TextureBinding(
		val texture: Texture<*>?,
		val slice: BlazeKoolTextureSlice
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
