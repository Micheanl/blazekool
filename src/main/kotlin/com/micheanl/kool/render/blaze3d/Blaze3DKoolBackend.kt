package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.micheanl.kool.api.compute.BlazeKoolComputeContext
import com.micheanl.kool.api.compute.BlazeKoolComputeRegistry
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.FrameData
import de.fabmax.kool.KoolContext
import de.fabmax.kool.PassData
import de.fabmax.kool.ViewData
import de.fabmax.kool.math.Vec3i
import de.fabmax.kool.modules.ksl.KslComputeShader
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.pipeline.ComputePass
import de.fabmax.kool.pipeline.ComputePassImpl
import de.fabmax.kool.pipeline.ComputePipeline
import de.fabmax.kool.pipeline.ComputeShaderCode
import de.fabmax.kool.pipeline.DrawCommand
import de.fabmax.kool.pipeline.DrawPipeline
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.OffscreenPass2d
import de.fabmax.kool.pipeline.OffscreenPass2dImpl
import de.fabmax.kool.pipeline.OffscreenPassCube
import de.fabmax.kool.pipeline.OffscreenPassCubeImpl
import de.fabmax.kool.pipeline.ShaderCode
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.backend.BackendFeatures
import de.fabmax.kool.pipeline.backend.DeviceCoordinates
import de.fabmax.kool.pipeline.backend.RenderBackend
import de.fabmax.kool.util.Buffer
import de.fabmax.kool.util.Color
import de.fabmax.kool.util.LongHash
import kotlinx.coroutines.CompletableDeferred
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

class Blaze3DKoolBackend(
	private val renderBridge: BlazeKoolRenderBridge
) : RenderBackend {
	private val resources = BlazeKoolResourceManager()
	private val submittedGeometry = ArrayList<BlazeKoolGeometry>(256)
	private val geometryCache = KoolDrawCommandGeometryCache(resources)

	override val name: String = "Blaze3D Kool Backend"
	override val apiName: String
		get() = RenderSystem.tryGetDevice()?.getDeviceInfo()?.backendName() ?: "Blaze3D"
	override val deviceName: String
		get() = RenderSystem.tryGetDevice()?.getDeviceInfo()?.name() ?: "Minecraft GPU Device"

	override val features: BackendFeatures
		get() {
			val info = RenderSystem.tryGetDevice()?.getDeviceInfo()
			return BackendFeatures(
				computeShaders = true,
				cubeMapArrays = true,
				reversedDepth = info?.isZZeroToOne() == true,
				maxSamples = 1,
				readWriteStorageTextures = true,
				depthOnlyShaderColorOutput = Color.BLACK,
				maxComputeWorkGroupsPerDimension = Vec3i(65_535, 65_535, 65_535),
				maxComputeWorkGroupSize = Vec3i(1_024, 1_024, 64),
				maxComputeInvocationsPerWorkgroup = 1_024
			)
		}

	override val deviceCoordinates: DeviceCoordinates
		get() {
			val info = RenderSystem.tryGetDevice()?.getDeviceInfo()
			return if (info?.isZZeroToOne() == true) {
				DeviceCoordinates.VULKAN
			} else {
				DeviceCoordinates.OPEN_GL
			}
		}

	override val frameGpuTime: Duration = ZERO
	override val isAsyncRendering: Boolean = false

	override fun renderFrame(frameData: FrameData, ctx: KoolContext) {
		submittedGeometry.clear()
		frameData.forEachPass { passData ->
			renderPass(passData)
		}
		renderBridge.replaceKoolGeometry(submittedGeometry)
	}

	override fun cleanup(ctx: KoolContext) {
		renderBridge.clearKoolGeometry()
		geometryCache.clear()
		resources.clear()
	}

	override fun generateKslShader(shader: KslShader, pipeline: DrawPipeline): ShaderCode {
		return Blaze3DKoolShaderCode(pipeline.pipelineHash)
	}

	override fun generateKslComputeShader(shader: KslComputeShader, pipeline: ComputePipeline): ComputeShaderCode {
		return Blaze3DKoolComputeShaderCode(pipeline.pipelineHash)
	}

	override fun createOffscreenPass2d(parentPass: OffscreenPass2d): OffscreenPass2dImpl {
		return BlazeKoolOffscreenPass2dImpl(parentPass, resources)
	}

	override fun createOffscreenPassCube(parentPass: OffscreenPassCube): OffscreenPassCubeImpl {
		return BlazeKoolOffscreenPassCubeImpl(parentPass, resources)
	}

	override fun createComputePass(parentPass: ComputePass): ComputePassImpl {
		return BlazeKoolComputePassImpl()
	}

	override fun <T : ImageData> uploadTextureData(tex: Texture<T>) {
		resources.uploadTexture(tex)
	}

	override fun downloadTextureData(texture: Texture<*>, deferred: CompletableDeferred<ImageData>) {
		val imageData = resources.downloadTextureData(texture)
		if (imageData != null) {
			deferred.complete(imageData)
		} else {
			deferred.completeExceptionally(IllegalStateException("Texture data is not available in the BlazeKool resource layer"))
		}
	}

	override fun downloadBuffer(buffer: GpuBuffer, deferred: CompletableDeferred<Unit>, resultBuffer: Buffer) {
		if (resources.downloadBuffer(buffer, resultBuffer)) {
			deferred.complete(Unit)
		} else {
			deferred.completeExceptionally(IllegalStateException("Buffer data is not available in the BlazeKool resource layer"))
		}
	}

	private fun renderPass(passData: PassData) {
		val gpuPass = passData.gpuPass
		if (gpuPass is ComputePass) {
			collectComputePass(gpuPass)
			return
		}
		passData.forEachView { viewData ->
			collectViewGeometry(viewData)
		}
	}

	private fun collectComputePass(pass: ComputePass) {
		val impl = pass.impl as? BlazeKoolComputePassImpl ?: return
		val dispatches = ArrayList<BlazeKoolComputeDispatch>(pass.tasks.size)
		var index = 0
		while (index < pass.tasks.size) {
			val task = pass.tasks[index]
			if (task.isEnabled) {
				task.beforeDispatch()
				val bindings = prepareComputeTask(pass, task)
				val context = BlazeKoolComputeContext(
					task = task,
					pipeline = task.pipeline,
					groupsX = task.numGroups.x,
					groupsY = task.numGroups.y,
					groupsZ = task.numGroups.z,
					pipelineBindings = bindings
				)
				BlazeKoolComputeRegistry.executorFor(context)?.dispatch(context)
				dispatches += BlazeKoolComputeDispatch(
					name = task.shader.name,
					groupsX = task.numGroups.x,
					groupsY = task.numGroups.y,
					groupsZ = task.numGroups.z
				)
				task.afterDispatch()
			}
			index++
		}
		impl.replaceDispatches(dispatches)
	}

	private fun prepareComputeTask(pass: ComputePass, task: ComputePass.Task): List<BindGroupData.BindingData> {
		task.pipeline.updatePipelineData(pass)
		task.pipeline.captureBuffer()
		val bindings = task.pipeline.capturedPipelineData.bufferedBindings
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			when (binding) {
				is BindGroupData.StorageBufferBindingData -> binding.storageBuffer?.let(resources::updateBuffer)
				is BindGroupData.Texture1dBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.Texture2dBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.Texture3dBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.TextureCubeBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.Texture2dArrayBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.TextureCubeArrayBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageTexture1dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageTexture2dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageTexture3dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				else -> Unit
			}
			index++
		}
		return bindings
	}

	private fun collectViewGeometry(viewData: ViewData) {
		viewData.drawQueue.forEach { command ->
			val geometry = createGeometry(command)
			if (geometry != null) {
				submittedGeometry += geometry
			}
		}
	}

	private fun createGeometry(command: DrawCommand): BlazeKoolGeometry? {
		if (!command.isActive) {
			return null
		}
		return geometryCache.getOrCreate(command)
	}
}

private class Blaze3DKoolShaderCode(override val hash: LongHash) : ShaderCode

private class Blaze3DKoolComputeShaderCode(override val hash: LongHash) : ComputeShaderCode
