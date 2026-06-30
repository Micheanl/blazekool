package com.micheanl.kool.render.blaze3d

import com.mojang.blaze3d.systems.RenderPass
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.FilterMode
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import java.util.Optional
import java.util.OptionalDouble

class BlazeKoolDirectDrawRenderer {
	fun render(context: LevelRenderContext, draws: List<BlazeKoolDirectDrawCommand>) {
		if (draws.isEmpty()) {
			return
		}
		val renderTarget = context.gameRenderer().mainRenderTarget()
		val colorTexture = RenderSystem.outputColorTextureOverride ?: renderTarget.colorTextureView ?: return
		val depthTexture = if (renderTarget.useDepth) {
			RenderSystem.outputDepthTextureOverride ?: renderTarget.depthTextureView
		} else {
			null
		}
		val renderPass = RenderSystem.getDevice()
			.createCommandEncoder()
			.createRenderPass({ "BlazeKool Direct Draw" }, colorTexture, Optional.empty(), depthTexture, OptionalDouble.empty())
		renderPass.use { pass ->
			var index = 0
			while (index < draws.size) {
				val draw = draws[index]
				pass.setPipeline(draw.pipeline)
				RenderSystem.bindDefaultUniforms(pass)
				pass.setUniform("DynamicTransforms", RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy()))
				bindUniforms(pass, draw)
				bindDefaultTextures(pass, context, draw)
				bindTextures(pass, draw)
				pass.setVertexBuffer(0, draw.vertexBuffer.slice())
				pass.setIndexBuffer(draw.indexBuffer, draw.indexType)
				pass.drawIndexed(draw.indexCount, 1, 0, 0, 0)
				index++
			}
		}
	}

	private fun bindDefaultTextures(pass: RenderPass, context: LevelRenderContext, draw: BlazeKoolDirectDrawCommand) {
		if (draw.requiresSampler("Sampler0")) {
			pass.bindTexture("Sampler0", BlazeKoolWhiteTexture.textureView(), BlazeKoolWhiteTexture.sampler())
		}
		if (draw.requiresSampler("Sampler2")) {
			pass.bindTexture("Sampler2", Minecraft.getInstance().gameRenderer.lightmap(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))
		}
	}

	private fun bindUniforms(pass: RenderPass, draw: BlazeKoolDirectDrawCommand) {
		var index = 0
		while (index < draw.uniformBindings.size) {
			val uniform = draw.uniformBindings[index]
			pass.setUniform(uniform.name, uniform.buffer)
			index++
		}
	}

	private fun bindTextures(pass: RenderPass, draw: BlazeKoolDirectDrawCommand) {
		var index = 0
		while (index < draw.textureBindings.size) {
			val texture = draw.textureBindings[index]
			pass.bindTexture(texture.name, texture.textureView, texture.sampler)
			index++
		}
	}
}
