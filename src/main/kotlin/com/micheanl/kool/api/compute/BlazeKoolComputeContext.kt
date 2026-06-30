package com.micheanl.kool.api.compute

import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.pipeline.ComputePass
import de.fabmax.kool.pipeline.ComputePipeline
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.StorageTexture

class BlazeKoolComputeContext(
	val task: ComputePass.Task,
	val pipeline: ComputePipeline,
	val groupsX: Int,
	val groupsY: Int,
	val groupsZ: Int,
	val pipelineBindings: List<BindGroupData.BindingData>
) {
	val storageBuffers: List<GpuBuffer> = pipelineBindings
		.filterIsInstance<BindGroupData.StorageBufferBindingData>()
		.mapNotNull { it.storageBuffer }

	val textures: List<Texture<*>> = pipelineBindings
		.mapNotNull {
			when (it) {
				is BindGroupData.Texture1dBindingData -> it.texture
				is BindGroupData.Texture2dBindingData -> it.texture
				is BindGroupData.Texture3dBindingData -> it.texture
				is BindGroupData.TextureCubeBindingData -> it.texture
				is BindGroupData.Texture2dArrayBindingData -> it.texture
				is BindGroupData.TextureCubeArrayBindingData -> it.texture
				else -> null
			}
		}

	val storageTextures: List<StorageTexture> = pipelineBindings
		.mapNotNull {
			when (it) {
				is BindGroupData.StorageTexture1dBindingData -> it.storageTexture
				is BindGroupData.StorageTexture2dBindingData -> it.storageTexture
				is BindGroupData.StorageTexture3dBindingData -> it.storageTexture
				else -> null
			}
		}
}
