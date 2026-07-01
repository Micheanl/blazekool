package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolRenderState
import com.mojang.blaze3d.IndexType
import com.mojang.blaze3d.buffers.GpuBuffer
import com.mojang.blaze3d.buffers.GpuBufferSlice
import com.mojang.blaze3d.pipeline.BindGroupLayout
import com.mojang.blaze3d.pipeline.RenderPipeline
import com.mojang.blaze3d.textures.GpuSampler
import com.mojang.blaze3d.textures.GpuTextureView

class BlazeKoolDirectDrawCommand(
	val renderState: BlazeKoolRenderState,
	val pipeline: RenderPipeline,
	val vertexBuffer: GpuBuffer,
	val indexBuffer: GpuBuffer,
	val indexType: IndexType,
	val indexCount: Int,
	val centerX: Float,
	val centerY: Float,
	val centerZ: Float,
	uniformBindings: List<UniformBinding>,
	textureBindings: List<TextureBinding>,
	val bindGroupLayout: BindGroupLayout
) : AutoCloseable {
	var uniformBindings: List<UniformBinding> = uniformBindings
		private set
	var textureBindings: List<TextureBinding> = textureBindings
		private set

	fun replaceBindings(uniformBindings: List<UniformBinding>, textureBindings: List<TextureBinding>) {
		closeUniformBindings(this.uniformBindings)
		this.uniformBindings = uniformBindings
		this.textureBindings = textureBindings
	}

	fun requiresSampler(name: String): Boolean {
		return bindGroupLayout.samplers.contains(name)
	}

	fun distanceSquaredTo(x: Double, y: Double, z: Double): Double {
		val dx = centerX - x
		val dy = centerY - y
		val dz = centerZ - z
		return dx * dx + dy * dy + dz * dz
	}

	override fun close() {
		if (!vertexBuffer.isClosed) {
			vertexBuffer.close()
		}
		if (!indexBuffer.isClosed) {
			indexBuffer.close()
		}
		closeUniformBindings(uniformBindings)
	}

	private fun closeUniformBindings(uniformBindings: List<UniformBinding>) {
		var index = 0
		while (index < uniformBindings.size) {
			val buffer = uniformBindings[index].buffer.buffer()
			if (!buffer.isClosed) {
				buffer.close()
			}
			index++
		}
	}

	class UniformBinding(
		val name: String,
		val buffer: GpuBufferSlice
	)

	class TextureBinding(
		val name: String,
		val textureView: GpuTextureView,
		val sampler: GpuSampler
	)
}
