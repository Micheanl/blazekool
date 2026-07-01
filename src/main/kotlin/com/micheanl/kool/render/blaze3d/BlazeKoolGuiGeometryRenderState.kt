package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveGeometry
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveType
import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.micheanl.kool.api.geometry.BlazeKoolVertex
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.navigation.ScreenRectangle
import net.minecraft.client.gui.render.TextureSetup
import net.minecraft.client.renderer.state.gui.GuiElementRenderState
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

class BlazeKoolGuiGeometryRenderState(
	private val geometry: BlazeKoolPrimitiveGeometry
) : GuiElementRenderState {
	private val geometryBounds: ScreenRectangle? = computeBounds(geometry.vertices)

	override fun buildVertices(vertexConsumer: VertexConsumer) {
		val state = geometry.state
		val vertices = geometry.vertices
		var index = 0
		val size = vertices.size
		while (index < size) {
			val vertex = vertices[index]
			val consumer = vertexConsumer.addVertex(vertex.x, vertex.y, vertex.z)
				.setColor(vertex.color)
			if (state.textured || state.shaderPipeline != null) {
				consumer.setUv(vertex.u, vertex.v)
			}
			if (state.shaderPipeline == null && (state.primitiveType == BlazeKoolPrimitiveType.LINES || state.primitiveType == BlazeKoolPrimitiveType.POINTS)) {
				consumer.setLineWidth(state.lineWidth)
			}
			if (state.shaderPipeline != null || state.primitiveType != BlazeKoolPrimitiveType.POINTS) {
				consumer.setNormal(vertex.nx, vertex.ny, vertex.nz)
			}
			index++
		}
	}

	override fun pipeline(): RenderPipeline {
		return BlazeKoolRenderTypes.pipeline(geometry.state)
	}

	override fun textureSetup(): TextureSetup {
		val state = geometry.state
		val texture = state.texture
		if (state.textured && texture != null) {
			val runtimeTexture = Minecraft.getInstance().textureManager.getTexture(texture)
			return TextureSetup.singleTexture(
				runtimeTexture.textureView,
				runtimeTexture.sampler
			)
		}
		if (state.shaderPipeline != null) {
			return TextureSetup.singleTexture(
				BlazeKoolWhiteTexture.textureView(),
				RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
			)
		}
		return TextureSetup.noTexture()
	}

	override fun scissorArea(): ScreenRectangle? {
		return null
	}

	override fun bounds(): ScreenRectangle? {
		return geometryBounds
	}

	companion object {
		fun accepts(geometry: BlazeKoolPrimitiveGeometry): Boolean {
			return !geometry.state.writeDepth && geometry.vertices.isNotEmpty()
		}

		private fun computeBounds(vertices: List<BlazeKoolVertex>): ScreenRectangle? {
			if (vertices.isEmpty()) {
				return null
			}
			var minX = Float.POSITIVE_INFINITY
			var minY = Float.POSITIVE_INFINITY
			var maxX = Float.NEGATIVE_INFINITY
			var maxY = Float.NEGATIVE_INFINITY
			var index = 0
			while (index < vertices.size) {
				val vertex = vertices[index]
				minX = min(minX, vertex.x)
				minY = min(minY, vertex.y)
				maxX = max(maxX, vertex.x)
				maxY = max(maxY, vertex.y)
				index++
			}
			if (!minX.isFinite() || !minY.isFinite() || !maxX.isFinite() || !maxY.isFinite()) {
				return null
			}
			val left = floor(minX.toDouble()).toInt()
			val top = floor(minY.toDouble()).toInt()
			val right = ceil(maxX.toDouble()).toInt()
			val bottom = ceil(maxY.toDouble()).toInt()
			return ScreenRectangle(left, top, (right - left).coerceAtLeast(1), (bottom - top).coerceAtLeast(1))
		}
	}
}
