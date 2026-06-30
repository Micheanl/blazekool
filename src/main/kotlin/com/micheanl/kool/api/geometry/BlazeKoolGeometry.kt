package com.micheanl.kool.api.geometry

import com.mojang.blaze3d.vertex.PoseStack
import com.mojang.blaze3d.vertex.VertexConsumer
import net.minecraft.client.renderer.rendertype.RenderType

interface BlazeKoolGeometry {
	val renderType: RenderType

	fun submit(pose: PoseStack.Pose, buffer: VertexConsumer)
}
