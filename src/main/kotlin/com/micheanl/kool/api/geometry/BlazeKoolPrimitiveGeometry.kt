package com.micheanl.kool.api.geometry

import com.micheanl.kool.render.blaze3d.BlazeKoolRenderTypes
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.rendertype.RenderType

class BlazeKoolPrimitiveGeometry(
	private val vertices: List<BlazeKoolVertex>,
	private val state: BlazeKoolRenderState
) : BlazeKoolGeometry {
	override val renderType: RenderType = BlazeKoolRenderTypes.renderType(state)

	override fun submit(pose: PoseStack.Pose, buffer: VertexConsumer) {
		var index = 0
		val size = vertices.size
		while (index < size) {
			emitVertex(pose, buffer, vertices[index])
			index++
		}
	}

	private fun emitVertex(pose: PoseStack.Pose, buffer: VertexConsumer, vertex: BlazeKoolVertex) {
		val consumer = buffer.addVertex(pose, vertex.x, vertex.y, vertex.z)
			.setColor(vertex.color)
		if (state.textured || state.shaderPipeline != null) {
			consumer.setUv(vertex.u, vertex.v)
		}
		if (state.shaderPipeline == null && (state.primitiveType == BlazeKoolPrimitiveType.LINES || state.primitiveType == BlazeKoolPrimitiveType.POINTS)) {
			consumer.setLineWidth(state.lineWidth)
		}
		if (state.shaderPipeline != null || state.primitiveType != BlazeKoolPrimitiveType.POINTS) {
			consumer.setNormal(pose, vertex.nx, vertex.ny, vertex.nz)
		}
	}
}
