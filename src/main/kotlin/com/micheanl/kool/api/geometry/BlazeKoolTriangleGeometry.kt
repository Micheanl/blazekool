package com.micheanl.kool.api.geometry

import com.micheanl.kool.render.blaze3d.BlazeKoolRenderTypes
import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.rendertype.RenderType

class BlazeKoolTriangleGeometry(
	private val vertices: List<BlazeKoolVertex>,
	override val renderType: RenderType = BlazeKoolRenderTypes.triangles()
) : BlazeKoolGeometry {
	init {
		require(vertices.size % 3 == 0) { "Triangle geometry requires a vertex count divisible by 3" }
	}

	override fun submit(pose: PoseStack.Pose, buffer: VertexConsumer) {
		var index = 0
		val size = vertices.size
		while (index < size) {
			emitVertex(pose, buffer, vertices[index])
			index++
		}
	}

	private fun emitVertex(pose: PoseStack.Pose, buffer: VertexConsumer, vertex: BlazeKoolVertex) {
		buffer.addVertex(pose, vertex.x, vertex.y, vertex.z)
			.setColor(vertex.color)
			.setUv(vertex.u, vertex.v)
			.setNormal(pose, vertex.nx, vertex.ny, vertex.nz)
	}
}
