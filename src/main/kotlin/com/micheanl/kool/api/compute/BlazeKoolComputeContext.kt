package com.micheanl.kool.api.compute

import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.pipeline.ComputePass
import de.fabmax.kool.pipeline.ComputePipeline
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.StorageTexture
import de.fabmax.kool.util.Buffer

class BlazeKoolComputeContext(
	val task: ComputePass.Task,
	val pipeline: ComputePipeline,
	val groupsX: Int,
	val groupsY: Int,
	val groupsZ: Int,
	val pipelineBindings: List<BindGroupData.BindingData>,
	private val bufferWriter: (GpuBuffer, Buffer) -> Unit,
	private val textureWriter: (Texture<*>, ImageData) -> Unit,
	private val textureSliceWriter: (Texture<*>, Int, ImageData) -> Unit,
	private val bufferReader: (GpuBuffer) -> Buffer,
	private val textureReader: (Texture<*>) -> ImageData,
	private val textureSliceReader: (Texture<*>, Int) -> ImageData
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

	fun writeStorageBuffer(buffer: GpuBuffer, data: Buffer) {
		bufferWriter(buffer, data)
	}

	fun writeStorageTexture(texture: StorageTexture, data: ImageData) {
		textureWriter(texture.asTexture, data)
	}

	fun writeStorageTexture(texture: StorageTexture, mipLevel: Int, data: ImageData) {
		textureSliceWriter(texture.asTexture, mipLevel, data)
	}

	fun writeTexture(texture: Texture<*>, data: ImageData) {
		textureWriter(texture, data)
	}

	fun readStorageBuffer(buffer: GpuBuffer): Buffer {
		return bufferReader(buffer)
	}

	fun readTexture(texture: Texture<*>): ImageData {
		return textureReader(texture)
	}

	fun readTexture(texture: Texture<*>, mipLevel: Int): ImageData {
		return textureSliceReader(texture, mipLevel)
	}

	fun readStorageTexture(texture: StorageTexture): ImageData {
		return textureReader(texture.asTexture)
	}

	fun readStorageTexture(texture: StorageTexture, mipLevel: Int): ImageData {
		return textureSliceReader(texture.asTexture, mipLevel)
	}
}
