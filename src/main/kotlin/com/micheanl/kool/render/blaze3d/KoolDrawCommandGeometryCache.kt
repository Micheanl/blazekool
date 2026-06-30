package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveGeometry
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveType
import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.micheanl.kool.api.geometry.BlazeKoolVertex
import com.micheanl.kool.api.geometry.blazeKoolColor
import de.fabmax.kool.math.Mat4f
import de.fabmax.kool.math.MutableVec3f
import de.fabmax.kool.pipeline.DepthCompareOp
import de.fabmax.kool.pipeline.DrawCommand
import de.fabmax.kool.pipeline.Texture2d
import de.fabmax.kool.pipeline.BindGroupData.Texture2dBindingData
import de.fabmax.kool.scene.geometry.IndexedVertexList
import de.fabmax.kool.scene.geometry.PrimitiveType
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.LongHash
import net.minecraft.resources.Identifier
import java.util.IdentityHashMap

class KoolDrawCommandGeometryCache(
	private val resources: BlazeKoolResourceManager
) {
	private val cache = IdentityHashMap<IndexedVertexList<*>, CachedGeometry>()

	fun getOrCreate(command: DrawCommand): BlazeKoolGeometry? {
		val geometry = command.vertexData
		if (geometry.numIndices <= 0) {
			return null
		}
		val primitiveType = geometry.primitiveType.toBlazeKoolPrimitiveType() ?: return null
		val texture = firstTexture(command)
		if (texture?.uploadData != null) {
			resources.uploadTexture(texture)
		}
		val state = renderState(command, primitiveType, texture)
		val matrixHash = matrixHash(command.modelMatF)
		val textureLocation = resources.textureLocation(texture)

		val cached = cache[geometry]
		if (
			cached != null &&
			geometry.modCount.isNotDirty(cached.modCount) &&
			cached.matrixHash == matrixHash &&
			cached.pipelineHash == command.pipeline.pipelineHash &&
			cached.renderState == state &&
			cached.textureLocation == textureLocation
		) {
			return cached.geometry
		}

		val converted = convert(command, state)
		cache[geometry] = CachedGeometry(
			modCount = geometry.modCount.count,
			matrixHash = matrixHash,
			pipelineHash = command.pipeline.pipelineHash,
			renderState = state,
			textureLocation = textureLocation,
			geometry = converted
		)
		return converted
	}

	fun clear() {
		cache.clear()
	}

	private fun convert(command: DrawCommand, state: BlazeKoolRenderState): BlazeKoolPrimitiveGeometry {
		val geometry = command.vertexData
		val vertices = ArrayList<BlazeKoolVertex>(geometry.numIndices)
		val transformedPosition = MutableVec3f()
		val transformedNormal = MutableVec3f()
		var index = 0
		while (index < geometry.numIndices) {
			val vertexIndex = geometry.indices[index]
			geometry.vertexIt.index = vertexIndex
			val position = geometry.vertexIt.position
			val normal = geometry.vertexIt.normal
			val color = geometry.vertexIt.color
			val texCoord = geometry.vertexIt.texCoord
			command.modelMatF.transform(position, 1.0f, transformedPosition)
			command.modelMatF.transform(normal, 0.0f, transformedNormal)
			val vertexColor = if (geometry.colorAttr != null) {
				blazeKoolColor(color.r, color.g, color.b, color.a)
			} else {
				blazeKoolColor(Color.WHITE.r, Color.WHITE.g, Color.WHITE.b, Color.WHITE.a)
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
		return BlazeKoolPrimitiveGeometry(vertices, state)
	}

	private fun renderState(command: DrawCommand, primitiveType: BlazeKoolPrimitiveType, texture: Texture2d?): BlazeKoolRenderState {
		val pipeline = command.pipeline
		return BlazeKoolRenderState(
			primitiveType = primitiveType,
			blendMode = pipeline.blendMode,
			cullMethod = pipeline.cullMethod,
			depthCompareOp = reversedDepthCompareOp(pipeline.depthCompareOp, pipeline.autoReverseDepthFunc),
			writeDepth = pipeline.isWriteDepth,
			lineWidth = pipeline.lineWidth,
			texture = resources.textureLocation(texture),
			textured = texture != null
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

	private fun firstTexture(command: DrawCommand): Texture2d? {
		val bindings = command.pipeline.capturedPipelineData.bufferedBindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			if (binding is Texture2dBindingData) {
				return binding.texture
			}
			index++
		}
		return null
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
		val matrixHash: LongHash,
		val pipelineHash: LongHash,
		val renderState: BlazeKoolRenderState,
		val textureLocation: Identifier?,
		val geometry: BlazeKoolPrimitiveGeometry
	)
}
