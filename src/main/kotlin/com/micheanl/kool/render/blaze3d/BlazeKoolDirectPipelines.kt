package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.BlazeKool
import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.RenderPipeline
import de.fabmax.kool.util.LongHash
import net.minecraft.client.renderer.BindGroupLayouts
import net.minecraft.client.renderer.RenderPipelines
import java.util.concurrent.ConcurrentHashMap

object BlazeKoolDirectPipelines {
	private val pipelines = ConcurrentHashMap<Key, RenderPipeline>()

	fun pipeline(state: BlazeKoolRenderState, layout: BindGroupLayout): RenderPipeline {
		val shaderPipeline = state.shaderPipeline ?: return BlazeKoolRenderTypes.pipeline(state)
		val key = Key(state, shaderPipeline.shaderKey, layoutKey(layout))
		val pipeline = pipelines.computeIfAbsent(key) {
			val builder = BlazeKoolRenderTypes.pipelineBuilder(state)
				.withLocation(BlazeKool.id("pipeline/direct/${BlazeKoolRenderTypes.pipelineName(state)}_${key.layoutHash.hash.toULong().toString(16)}"))
			if (shaderPipeline.mapping.usesLighting) {
				builder.withBindGroupLayout(BindGroupLayouts.LIGHTING)
			}
			RenderPipelines.register(builder
				.withBindGroupLayout(layout)
				.build())
		}
		BlazeKoolKslShaderRegistry.precompile(pipeline, shaderPipeline)
		return pipeline
	}

	private fun layoutKey(layout: BindGroupLayout): LongHash {
		return LongHash {
			for (uniform in layout.uniforms) {
				this += uniform.name()
				this += uniform.type().name
				this += uniform.gpuFormat()?.name ?: ""
			}
			for (sampler in layout.samplers) {
				this += sampler
			}
		}
	}

	private data class Key(
		val state: BlazeKoolRenderState,
		val shaderHash: LongHash,
		val layoutHash: LongHash
	)
}
