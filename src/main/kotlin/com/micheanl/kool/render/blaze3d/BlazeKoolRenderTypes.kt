package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.BlazeKool
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveType
import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import java.util.concurrent.ConcurrentHashMap

object BlazeKoolRenderTypes {
	private val renderTypes = ConcurrentHashMap<BlazeKoolRenderState, RenderType>()

	private val trianglePipeline: RenderPipeline = RenderPipelines.register(
		RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(BlazeKool.id("pipeline/triangles"))
			.withVertexShader("core/position_color")
			.withFragmentShader("core/position_color")
			.withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withDepthStencilState(DepthStencilState.DEFAULT)
			.withCull(false)
			.build()
	)

	private val triangles: RenderType = RenderType.create(
		"blazekool_triangles",
		RenderSetup.builder(trianglePipeline).createRenderSetup()
	)

	fun triangles(): RenderType = triangles

	fun renderType(state: BlazeKoolRenderState): RenderType {
		return renderTypes.computeIfAbsent(state, ::createRenderType)
	}

	private fun createRenderType(state: BlazeKoolRenderState): RenderType {
		val builder = RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withLocation(BlazeKool.id("pipeline/kool/${pipelineName(state)}"))
			.withVertexShader(vertexShaderName(state))
			.withFragmentShader(fragmentShaderName(state))
			.withVertexBinding(0, vertexFormat(state))
			.withPrimitiveTopology(state.primitiveType.topology)
			.withDepthStencilState(depthStencilState(state))
			.withColorTargetState(colorTargetState(state))
			.withCull(state.cullMethod == CullMethod.CULL_BACK_FACES)
		if (state.textured) {
			builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
		}
		val pipeline = RenderPipelines.register(
			builder.build()
		)
		val setupBuilder = RenderSetup.builder(pipeline)
		if (state.textured && state.texture != null) {
			setupBuilder.withTexture("Sampler0", state.texture)
		}
		if (state.blendMode != BlendMode.DISABLED) {
			setupBuilder.sortOnUpload()
		}
		return RenderType.create("blazekool_${pipelineName(state)}", setupBuilder.createRenderSetup())
	}

	private fun pipelineName(state: BlazeKoolRenderState): String {
		val textureName = if (state.textured) "tex" else "color"
		return "${state.primitiveType.name.lowercase()}_${textureName}_${state.blendMode.name.lowercase()}_${state.depthCompareOp.name.lowercase()}_${state.writeDepth}"
	}

	private fun vertexShaderName(state: BlazeKoolRenderState): String {
		return when {
			state.primitiveType == BlazeKoolPrimitiveType.LINES -> "core/rendertype_lines"
			state.primitiveType == BlazeKoolPrimitiveType.POINTS -> "core/debug_point"
			state.textured -> "core/position_tex_color"
			else -> "core/position_color"
		}
	}

	private fun fragmentShaderName(state: BlazeKoolRenderState): String {
		return when {
			state.primitiveType == BlazeKoolPrimitiveType.LINES -> "core/rendertype_lines"
			state.textured -> "core/position_tex_color"
			else -> "core/position_color"
		}
	}

	private fun vertexFormat(state: BlazeKoolRenderState) = when {
		state.primitiveType == BlazeKoolPrimitiveType.LINES -> DefaultVertexFormat.POSITION_COLOR_NORMAL_LINE_WIDTH
		state.primitiveType == BlazeKoolPrimitiveType.POINTS -> DefaultVertexFormat.POSITION_COLOR_LINE_WIDTH
		state.textured -> DefaultVertexFormat.POSITION_TEX_COLOR
		else -> DefaultVertexFormat.POSITION_COLOR
	}

	private fun depthStencilState(state: BlazeKoolRenderState): DepthStencilState {
		return DepthStencilState(compareOp(state.depthCompareOp), state.writeDepth)
	}

	private fun colorTargetState(state: BlazeKoolRenderState): ColorTargetState {
		return when (state.blendMode) {
			BlendMode.DISABLED -> ColorTargetState.DEFAULT
			BlendMode.BLEND_ADDITIVE -> ColorTargetState(BlendFunction.ADDITIVE)
			BlendMode.BLEND_MULTIPLY_ALPHA -> ColorTargetState(BlendFunction.TRANSLUCENT)
			BlendMode.BLEND_PREMULTIPLIED_ALPHA -> ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
		}
	}

	private fun compareOp(compareOp: DepthCompareOp): CompareOp {
		return when (compareOp) {
			DepthCompareOp.ALWAYS -> CompareOp.ALWAYS_PASS
			DepthCompareOp.NEVER -> CompareOp.NEVER_PASS
			DepthCompareOp.LESS -> CompareOp.LESS_THAN
			DepthCompareOp.LESS_EQUAL -> CompareOp.LESS_THAN_OR_EQUAL
			DepthCompareOp.GREATER -> CompareOp.GREATER_THAN
			DepthCompareOp.GREATER_EQUAL -> CompareOp.GREATER_THAN_OR_EQUAL
			DepthCompareOp.EQUAL -> CompareOp.EQUAL
			DepthCompareOp.NOT_EQUAL -> CompareOp.NOT_EQUAL
		}
	}
}
