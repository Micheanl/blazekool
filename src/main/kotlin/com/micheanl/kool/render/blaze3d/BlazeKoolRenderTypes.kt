package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.BlazeKool
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveType
import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.mojang.blaze3d.GpuFormat
import com.mojang.blaze3d.PrimitiveTopology
import com.mojang.blaze3d.platform.NativeImage
import com.mojang.blaze3d.pipeline.BlendFunction
import com.mojang.blaze3d.pipeline.ColorTargetState
import com.mojang.blaze3d.pipeline.DepthStencilState
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.platform.CompareOp
import com.mojang.blaze3d.vertex.DefaultVertexFormat
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.pipeline.CullMethod
import de.fabmax.kool.pipeline.DepthCompareOp
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import net.minecraft.client.renderer.texture.DynamicTexture
import net.minecraft.client.renderer.rendertype.RenderSetup
import net.minecraft.client.renderer.rendertype.RenderType
import net.minecraft.resources.Identifier
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
		val renderType = renderTypes.computeIfAbsent(state, ::createRenderType)
		BlazeKoolKslShaderRegistry.precompile(renderType.pipeline(), state.shaderPipeline)
		return renderType
	}

	private fun createRenderType(state: BlazeKoolRenderState): RenderType {
		val builder = pipelineBuilder(state)
			.withLocation(BlazeKool.id("pipeline/kool/${pipelineName(state)}"))
		if (state.textured || state.shaderPipeline != null) {
			builder.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
		}
		if (state.shaderPipeline?.mapping?.usesLighting == true) {
			builder.withBindGroupLayout(BindGroupLayouts.LIGHTING)
		}
		val pipeline = RenderPipelines.register(
			builder.build()
		)
		BlazeKoolKslShaderRegistry.precompile(pipeline, state.shaderPipeline)
		val setupBuilder = RenderSetup.builder(pipeline)
		val texture = if (state.textured && state.texture != null) {
			state.texture
		} else if (state.shaderPipeline != null) {
			BlazeKoolWhiteTexture.location()
		} else {
			null
		}
		if (texture != null) {
			setupBuilder.withTexture("Sampler0", texture)
		}
		if (state.blendMode != BlendMode.DISABLED) {
			setupBuilder.sortOnUpload()
		}
		return RenderType.create("blazekool_${pipelineName(state)}", setupBuilder.createRenderSetup())
	}

	fun pipelineBuilder(state: BlazeKoolRenderState): RenderPipeline.Builder {
		return RenderPipeline.builder(RenderPipelines.MATRICES_FOG_SNIPPET)
			.withVertexShader(vertexShaderName(state))
			.withFragmentShader(fragmentShaderName(state))
			.withVertexBinding(0, vertexFormat(state))
			.withPrimitiveTopology(state.primitiveType.topology)
			.withDepthStencilState(depthStencilState(state))
			.withColorTargetState(colorTargetState(state))
			.withCull(state.cullMethod == CullMethod.CULL_BACK_FACES)
	}

	fun pipeline(state: BlazeKoolRenderState): RenderPipeline {
		return renderType(state).pipeline()
	}

	fun pipelineName(state: BlazeKoolRenderState): String {
		val textureName = if (state.textured) "tex" else "color"
		val shaderName = state.shaderPipeline?.shaderKey?.hash?.toULong()?.toString(16) ?: "core"
		return "${state.primitiveType.name.lowercase()}_${textureName}_${state.blendMode.name.lowercase()}_${state.depthCompareOp.name.lowercase()}_${state.writeDepth}_$shaderName"
	}

	private fun vertexShaderName(state: BlazeKoolRenderState): Identifier {
		val shaderPipeline = state.shaderPipeline
		if (shaderPipeline != null) {
			return shaderPipeline.vertexShader
		}
		val path = when {
			state.primitiveType == BlazeKoolPrimitiveType.LINES -> "core/rendertype_lines"
			state.primitiveType == BlazeKoolPrimitiveType.POINTS -> "core/debug_point"
			state.textured -> "core/position_tex_color"
			else -> "core/position_color"
		}
		return Identifier.withDefaultNamespace(path)
	}

	private fun fragmentShaderName(state: BlazeKoolRenderState): Identifier {
		val shaderPipeline = state.shaderPipeline
		if (shaderPipeline != null) {
			return shaderPipeline.fragmentShader
		}
		val path = when {
			state.primitiveType == BlazeKoolPrimitiveType.LINES -> "core/rendertype_lines"
			state.textured -> "core/position_tex_color"
			else -> "core/position_color"
		}
		return Identifier.withDefaultNamespace(path)
	}

	private fun vertexFormat(state: BlazeKoolRenderState) = when {
		state.shaderPipeline != null -> DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL
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

object BlazeKoolWhiteTexture {
	private val location = BlazeKool.id("runtime/white")
	private var registered = false

	fun location(): Identifier {
		if (!registered) {
			val image = NativeImage(1, 1, false)
			image.setPixel(0, 0, -1)
			Minecraft.getInstance().textureManager.register(location, DynamicTexture({ "BlazeKool White Texture" }, image))
			registered = true
		}
		return location
	}

	fun textureView(): GpuTextureView {
		return Minecraft.getInstance().textureManager.getTexture(location()).textureView
	}

	fun sampler(): GpuSampler {
		return Minecraft.getInstance().textureManager.getTexture(location()).sampler
	}
}
