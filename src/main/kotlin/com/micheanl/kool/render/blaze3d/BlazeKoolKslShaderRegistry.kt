package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.BlazeKool
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.shaders.ShaderSource
import com.mojang.blaze3d.shaders.ShaderType
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.generator.KslGenerator
import de.fabmax.kool.modules.ksl.lang.KslShaderStageType
import de.fabmax.kool.pipeline.DrawPipeline
import de.fabmax.kool.pipeline.backend.gl.GlslGenerator
import de.fabmax.kool.pipeline.backend.vk.GlslGeneratorVk
import de.fabmax.kool.pipeline.backend.wgsl.WgslGenerator
import de.fabmax.kool.util.LongHash
import net.minecraft.resources.Identifier
import java.util.concurrent.ConcurrentHashMap

object BlazeKoolKslShaderRegistry {
	private val sourcesByShader = ConcurrentHashMap<Identifier, ShaderStageSource>()
	private val shaderSources = ShaderSource { id, type -> sourceFor(id, type) }

	fun register(shader: KslShader, pipeline: DrawPipeline, mapping: BlazeKoolShaderMapping): BlazeKoolShaderPipeline {
		val generatedSources = generateSources(shader, pipeline, mapping)
		val shaderKey = shaderKey(shader, pipeline, generatedSources)
		val vertexShader = BlazeKool.id("ksl/${shaderKey.hashString()}/vertex")
		val fragmentShader = BlazeKool.id("ksl/${shaderKey.hashString()}/fragment")
		sourcesByShader[vertexShader] = ShaderStageSource(
			blazeSource = generatedSources.blazeVertexSource,
			openGlSource = generatedSources.openGlVertexSource,
			vulkanSource = generatedSources.vulkanVertexSource
		)
		sourcesByShader[fragmentShader] = ShaderStageSource(
			blazeSource = generatedSources.blazeFragmentSource,
			openGlSource = generatedSources.openGlFragmentSource,
			vulkanSource = generatedSources.vulkanFragmentSource
		)
		return BlazeKoolShaderPipeline(shaderKey, vertexShader, fragmentShader, generatedSources, mapping)
	}

	fun precompile(pipeline: RenderPipeline, shaderPipeline: BlazeKoolShaderPipeline?) {
		if (shaderPipeline != null) {
			RenderSystem.tryGetDevice()?.precompilePipeline(pipeline, shaderSources)
		}
	}

	private fun sourceFor(id: Identifier, shaderType: ShaderType): String? {
		val source = sourcesByShader[id] ?: return null
		return when (shaderType) {
			ShaderType.VERTEX -> source.blazeSource
			ShaderType.FRAGMENT -> source.blazeSource
		}
	}

	private fun generateSources(shader: KslShader, pipeline: DrawPipeline, mapping: BlazeKoolShaderMapping): BlazeKoolGeneratedShaderSources {
		val openGl = GlslGenerator.generateProgram(
			shader.program,
			pipeline,
			GlslGenerator.Hints(glslVersionStr = BlazeKoolRuntimeShaderTemplates.glslVersionDirective(), compat1dSampler = true)
		)
		val vulkan = GlslGeneratorVk.generateProgram(shader.program, pipeline)
		val wgsl = WgslGenerator.generateProgram(shader.program, pipeline)
		return BlazeKoolGeneratedShaderSources(
			blazeVertexSource = minecraftVertexSource(mapping),
			blazeFragmentSource = minecraftFragmentSource(mapping),
			openGlVertexSource = openGl.stage(KslShaderStageType.VertexShader),
			openGlFragmentSource = openGl.stage(KslShaderStageType.FragmentShader),
			vulkanVertexSource = vulkan.stage(KslShaderStageType.VertexShader),
			vulkanFragmentSource = vulkan.stage(KslShaderStageType.FragmentShader),
			wgslVertexSource = wgsl.stage(KslShaderStageType.VertexShader),
			wgslFragmentSource = wgsl.stage(KslShaderStageType.FragmentShader)
		)
	}

	private fun shaderKey(shader: KslShader, pipeline: DrawPipeline, sources: BlazeKoolGeneratedShaderSources): LongHash {
		return LongHash {
			this += shader.name
			this += pipeline.name
			this += pipeline.vertexLayout.hash
			this += pipeline.bindGroupLayouts.viewScope.hash
			this += pipeline.bindGroupLayouts.pipelineScope.hash
			this += pipeline.bindGroupLayouts.meshScope.hash
			this += pipeline.cullMethod
			this += pipeline.blendMode
			this += pipeline.depthCompareOp
			this += pipeline.autoReverseDepthFunc
			this += pipeline.isWriteDepth
			this += pipeline.lineWidth
			this += sources.blazeVertexSource
			this += sources.blazeFragmentSource
			this += sources.openGlVertexSource
			this += sources.openGlFragmentSource
			this += sources.vulkanVertexSource
			this += sources.vulkanFragmentSource
		}
	}

	private fun KslGenerator.GeneratedSourceOutput.stage(type: KslShaderStageType): String {
		return stages[type] ?: ""
	}

	private fun minecraftVertexSource(mapping: BlazeKoolShaderMapping): String {
		return BlazeKoolRuntimeShaderTemplates.vertex(mapping)
	}

	private fun minecraftFragmentSource(mapping: BlazeKoolShaderMapping): String {
		return BlazeKoolRuntimeShaderTemplates.fragment(mapping)
	}

	private fun LongHash.hashString(): String {
		return hash.toULong().toString(16)
	}

	private data class ShaderStageSource(
		val blazeSource: String,
		val openGlSource: String,
		val vulkanSource: String
	)
}
