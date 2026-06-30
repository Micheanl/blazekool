package com.micheanl.kool.api.geometry

import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import net.minecraft.resources.Identifier

data class BlazeKoolRenderState(
	val primitiveType: BlazeKoolPrimitiveType,
	val blendMode: BlendMode,
	val cullMethod: CullMethod,
	val depthCompareOp: DepthCompareOp,
	val writeDepth: Boolean,
	val lineWidth: Float,
	val texture: Identifier?,
	val textured: Boolean
) {
	companion object {
		fun untextured(primitiveType: BlazeKoolPrimitiveType): BlazeKoolRenderState = BlazeKoolRenderState(
			primitiveType = primitiveType,
			blendMode = BlendMode.BLEND_MULTIPLY_ALPHA,
			cullMethod = CullMethod.CULL_BACK_FACES,
			depthCompareOp = DepthCompareOp.LESS_EQUAL,
			writeDepth = true,
			lineWidth = 1.0f,
			texture = null,
			textured = false
		)
	}
}
