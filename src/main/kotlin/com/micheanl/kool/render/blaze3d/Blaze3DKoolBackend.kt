package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.micheanl.kool.api.compute.BlazeKoolComputeContext
import com.micheanl.kool.api.compute.BlazeKoolComputeRegistry
import com.mojang.blaze3d.systems.RenderSystem
import de.fabmax.kool.modules.ksl.KslBlinnPhongShader
import de.fabmax.kool.pipeline.BindGroupData
import de.fabmax.kool.pipeline.BlendMode
import de.fabmax.kool.FrameData
import de.fabmax.kool.KoolContext
import de.fabmax.kool.PassData
import de.fabmax.kool.ViewData
import de.fabmax.kool.math.Vec3i
import de.fabmax.kool.modules.ksl.KslLitShader
import de.fabmax.kool.modules.ksl.KslPbrShader
import de.fabmax.kool.modules.ksl.KslPbrSplatShader
import de.fabmax.kool.modules.ksl.KslComputeShader
import de.fabmax.kool.modules.ksl.KslShader
import de.fabmax.kool.modules.ksl.KslUnlitShader
import de.fabmax.kool.modules.ksl.blocks.ColorBlockConfig
import de.fabmax.kool.modules.ksl.blocks.ColorSpaceConversion
import de.fabmax.kool.modules.ksl.blocks.PropertyBlockConfig
import de.fabmax.kool.pipeline.ComputePass
import de.fabmax.kool.pipeline.ComputePassImpl
import de.fabmax.kool.pipeline.ComputePipeline
import de.fabmax.kool.pipeline.ComputeShaderCode
import de.fabmax.kool.pipeline.DrawCommand
import de.fabmax.kool.pipeline.DrawPipeline
import de.fabmax.kool.pipeline.FrameCopy
import de.fabmax.kool.pipeline.GpuBuffer
import de.fabmax.kool.pipeline.ImageData
import de.fabmax.kool.pipeline.OffscreenPass2d
import de.fabmax.kool.pipeline.OffscreenPass2dImpl
import de.fabmax.kool.pipeline.OffscreenPassCube
import de.fabmax.kool.pipeline.OffscreenPassCubeImpl
import de.fabmax.kool.pipeline.RenderPass
import de.fabmax.kool.pipeline.RenderPass.MipMapRenderOrder
import de.fabmax.kool.pipeline.RenderPass.MipMode
import de.fabmax.kool.pipeline.RenderPassColorTextureAttachment
import de.fabmax.kool.pipeline.RenderPassDepthTextureAttachment
import de.fabmax.kool.pipeline.ShaderCode
import de.fabmax.kool.pipeline.Texture
import de.fabmax.kool.pipeline.backend.BackendFeatures
import de.fabmax.kool.pipeline.backend.DeviceCoordinates
import de.fabmax.kool.pipeline.backend.RenderBackend
import de.fabmax.kool.pipeline.shading.AlphaMode
import de.fabmax.kool.scene.Scene
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
	private val directDraws = ArrayList<BlazeKoolDirectDrawCommand>(256)
	private val geometryCache = KoolDrawCommandGeometryCache(resources)
	private val directDrawCollector = BlazeKoolDirectDrawCollector(resources)

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
		directDraws.clear()
		directDrawCollector.beginFrame()
		frameData.forEachPass { passData ->
			renderPass(passData)
		}
		directDrawCollector.endFrame()
		renderBridge.replaceKoolGeometry(submittedGeometry)
		renderBridge.replaceDirectDraws(directDraws)
	}

	override fun cleanup(ctx: KoolContext) {
		renderBridge.clearKoolGeometry()
		geometryCache.clear()
		directDrawCollector.clear()
		resources.clear()
	}

	override fun generateKslShader(shader: KslShader, pipeline: DrawPipeline): ShaderCode {
		val mapping = shaderMapping(shader, pipeline)
		val shaderPipeline = BlazeKoolKslShaderRegistry.register(shader, pipeline, mapping)
		return Blaze3DKoolShaderCode(shaderPipeline.shaderKey, shaderMetadata(shader, shaderPipeline))
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
			deferred.complete(resources.fallbackTextureData(texture))
		}
	}

	override fun downloadBuffer(buffer: GpuBuffer, deferred: CompletableDeferred<Unit>, resultBuffer: Buffer) {
		if (resources.downloadBuffer(buffer, resultBuffer)) {
			deferred.complete(Unit)
		} else {
			resources.clearBufferData(resultBuffer)
			deferred.complete(Unit)
		}
	}

	private fun renderPass(passData: PassData) {
		val gpuPass = passData.gpuPass
		when (gpuPass) {
			is ComputePass -> collectComputePass(gpuPass)
			is Scene.ScreenPass -> {
				passData.forEachView { viewData ->
					collectScreenViewGeometry(viewData)
				}
				copyFrameCopies(gpuPass, passData.frameCopies)
			}
			is RenderPass -> {
				prepareOffscreenPass(gpuPass)
				renderOffscreenMipLevels(gpuPass, passData)
				copyFrameCopies(gpuPass, passData.frameCopies)
			}
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
					pipelineBindings = bindings,
					bufferWriter = resources::replaceBufferData,
					textureWriter = resources::replaceTextureData,
					textureSliceWriter = ::replaceComputeTextureSlice,
					bufferReader = resources::readBufferData,
					textureReader = resources::readTextureData,
					textureSliceReader = ::readComputeTextureSlice
				)
				BlazeKoolComputeRegistry.executorFor(context)?.dispatch(context)
				syncComputeOutputs(bindings)
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

	private fun syncComputeOutputs(bindings: List<BindGroupData.BindingData>) {
		var index = 0
		while (index < bindings.size) {
			val binding = bindings[index]
			when (binding) {
				is BindGroupData.StorageBufferBindingData -> binding.storageBuffer?.let(resources::updateBuffer)
				is BindGroupData.StorageTexture1dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageTexture2dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageTexture3dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				else -> Unit
			}
			index++
		}
	}

	private fun replaceComputeTextureSlice(texture: Texture<*>, mipLevel: Int, imageData: ImageData) {
		resources.replaceTextureSliceData(texture, BlazeKoolTextureSlice(mipLevel = mipLevel), imageData)
	}

	private fun readComputeTextureSlice(texture: Texture<*>, mipLevel: Int): ImageData {
		return resources.readTextureData(texture, BlazeKoolTextureSlice(mipLevel = mipLevel))
	}

	private fun collectScreenViewGeometry(viewData: ViewData) {
		viewData.drawQueue.view.setupView()
		viewData.drawQueue.view.viewPipelineData.captureBuffer()
		viewData.drawQueue.forEach { command ->
			command.updatePipelineData()
			command.captureData()
		}
		var copyIndex = 0
		viewData.drawQueue.forEach { command ->
			while (copyIndex < viewData.frameCopies.size && command.drawGroupId > viewData.frameCopies[copyIndex].drawGroupId) {
				copyFrameCopy(viewData.drawQueue.renderPass, viewData.frameCopies[copyIndex])
				copyIndex++
			}
			collectCommandGeometry(command)
		}
		while (copyIndex < viewData.frameCopies.size) {
			copyFrameCopy(viewData.drawQueue.renderPass, viewData.frameCopies[copyIndex])
			copyIndex++
		}
	}

	private fun renderOffscreenMipLevels(pass: RenderPass, passData: PassData) {
		when (val mipMode = pass.mipMode) {
			MipMode.Generate -> {
				pass.setupMipLevel(0)
				syncMipLevelPipelineData(passData)
				collectOffscreenViews(passData, mipLevel = 0, numMipLevels = 1)
			}
			MipMode.Single -> {
				pass.setupMipLevel(0)
				syncMipLevelPipelineData(passData)
				collectOffscreenViews(passData, mipLevel = 0, numMipLevels = 1)
			}
			is MipMode.Render -> {
				val numMipLevels = pass.numRenderMipLevels
				if (mipMode.renderOrder == MipMapRenderOrder.HigherResolutionFirst) {
					var mipLevel = 0
					while (mipLevel < numMipLevels) {
						pass.setupMipLevel(mipLevel)
						syncMipLevelPipelineData(passData)
						collectOffscreenViews(passData, mipLevel, numMipLevels)
						mipLevel++
					}
				} else {
					var mipLevel = numMipLevels - 1
					while (mipLevel >= 0) {
						pass.setupMipLevel(mipLevel)
						syncMipLevelPipelineData(passData)
						collectOffscreenViews(passData, mipLevel, numMipLevels)
						mipLevel--
					}
				}
			}
		}
	}

	private fun syncMipLevelPipelineData(passData: PassData) {
		passData.forEachView { viewData ->
			viewData.drawQueue.view.setupView()
			viewData.drawQueue.view.viewPipelineData.captureBuffer()
			viewData.drawQueue.forEach { command ->
				command.updatePipelineData()
				command.captureData()
			}
		}
	}

	private fun collectOffscreenViews(passData: PassData, mipLevel: Int, numMipLevels: Int) {
		val isLastMipLevel = mipLevel == numMipLevels - 1
		passData.forEachView { viewData ->
			var copyIndex = 0
			viewData.drawQueue.forEach { command ->
				while (isLastMipLevel && copyIndex < viewData.frameCopies.size && command.drawGroupId > viewData.frameCopies[copyIndex].drawGroupId) {
					copyFrameCopy(viewData.drawQueue.renderPass, viewData.frameCopies[copyIndex])
					copyIndex++
				}
				prepareDrawCommandResources(command)
			}
			while (isLastMipLevel && copyIndex < viewData.frameCopies.size) {
				copyFrameCopy(viewData.drawQueue.renderPass, viewData.frameCopies[copyIndex])
				copyIndex++
			}
		}
	}

	private fun collectCommandGeometry(command: DrawCommand) {
		val directDraw = directDrawCollector.collect(command)
		if (directDraw != null) {
			directDraws += directDraw
		} else {
			val geometry = createGeometry(command)
			if (geometry != null) {
				submittedGeometry += geometry
			}
		}
	}

	private fun prepareDrawCommandResources(command: DrawCommand) {
		if (!command.isActive) {
			return
		}
		command.pipeline.capturedPipelineData.bufferedBindings.forEach { binding ->
			when (binding) {
				is BindGroupData.Texture1dBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.Texture2dBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.Texture3dBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.TextureCubeBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.Texture2dArrayBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.TextureCubeArrayBindingData -> binding.texture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageBufferBindingData -> binding.storageBuffer?.let(resources::updateBuffer)
				is BindGroupData.StorageTexture1dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageTexture2dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				is BindGroupData.StorageTexture3dBindingData -> binding.storageTexture?.asTexture?.let(resources::ensureTextureResource)
				else -> Unit
			}
		}
	}

	private fun prepareOffscreenPass(pass: RenderPass) {
		var index = 0
		while (index < pass.colorAttachments.size) {
			val attachment = pass.colorAttachments[index] as? RenderPassColorTextureAttachment<*>
			if (attachment != null) {
				resources.ensureTextureResource(attachment.texture)
			}
			index++
		}
		val depthAttachment = pass.depthAttachment as? RenderPassDepthTextureAttachment<*>
		depthAttachment?.texture?.let(resources::ensureTextureResource)
	}

	private fun copyFrameCopies(pass: RenderPass, frameCopies: List<FrameCopy>) {
		var copyIndex = 0
		while (copyIndex < frameCopies.size) {
			copyFrameCopy(pass, frameCopies[copyIndex])
			copyIndex++
		}
	}

	private fun copyFrameCopy(pass: RenderPass, frameCopy: FrameCopy) {
		if (frameCopy.isCopyColor) {
			copyColorAttachments(pass, frameCopy)
		}
		if (frameCopy.isCopyDepth) {
			copyDepthAttachment(pass, frameCopy)
		}
	}

	private fun copyColorAttachments(pass: RenderPass, frameCopy: FrameCopy) {
		var index = 0
		while (index < frameCopy.colorCopy.size && index < pass.colorAttachments.size) {
			val source = (pass.colorAttachments[index] as? RenderPassColorTextureAttachment<*>)?.texture
			if (source != null) {
				resources.copyTextureData(source, frameCopy.colorCopy[index])
			}
			index++
		}
	}

	private fun copyDepthAttachment(pass: RenderPass, frameCopy: FrameCopy) {
		val source = (pass.depthAttachment as? RenderPassDepthTextureAttachment<*>)?.texture
		val target = frameCopy.depthCopy
		if (source != null && target != null) {
			resources.copyTextureData(source, target)
		}
	}

	private fun createGeometry(command: DrawCommand): BlazeKoolGeometry? {
		if (!command.isActive) {
			return null
		}
		return geometryCache.getOrCreate(command)
	}
}

private fun shaderMetadata(shader: KslShader, shaderPipeline: BlazeKoolShaderPipeline): BlazeKoolShaderMetadata {
	val layers = HashMap<String, Int>()
	when (shader) {
		is KslUnlitShader -> addColorBlockLayers(layers, shader.colorCfg)
		is KslPbrSplatShader -> {
			addColorBlockLayers(layers, shader.cfg.splatMapCfg)
			addPropertyBlockLayers(layers, shader.displacementCfg)
			var index = 0
			while (index < shader.cfg.materials.size) {
				val material = shader.cfg.materials[index]
				addColorBlockLayers(layers, material.colorCfg)
				addColorBlockLayers(layers, material.emissionCfg)
				addPropertyBlockLayers(layers, material.aoCfg)
				addPropertyBlockLayers(layers, material.roughnessCfg)
				addPropertyBlockLayers(layers, material.metallicCfg)
				index++
			}
		}
		is KslPbrShader -> {
			addLitShaderLayers(layers, shader)
			addPropertyBlockLayers(layers, shader.roughnessCfg)
			addPropertyBlockLayers(layers, shader.metallicCfg)
		}
		is KslBlinnPhongShader -> {
			addLitShaderLayers(layers, shader)
			addPropertyBlockLayers(layers, shader.shininessCfg)
			addPropertyBlockLayers(layers, shader.specularStrengthCfg)
		}
		is KslLitShader -> addLitShaderLayers(layers, shader)
	}
	return if (layers.isEmpty()) {
		BlazeKoolShaderMetadata(emptyMap(), shaderPipeline)
	} else {
		BlazeKoolShaderMetadata(layers, shaderPipeline)
	}
}

private fun shaderMapping(shader: KslShader, pipeline: DrawPipeline): BlazeKoolShaderMapping {
	val family = shaderFamily(shader)
	return BlazeKoolShaderMapping(
		family = family,
		usesLighting = family != BlazeKoolShaderFamily.UNLIT && family != BlazeKoolShaderFamily.CUSTOM,
		usesFog = family != BlazeKoolShaderFamily.CUSTOM,
		premultipliedAlpha = pipeline.blendMode == BlendMode.BLEND_PREMULTIPLIED_ALPHA,
		opaqueAlpha = alphaMode(shader) is AlphaMode.Opaque,
		alphaCutoff = (alphaMode(shader) as? AlphaMode.Mask)?.cutOff,
		colorSpace = colorSpace(shader)
	)
}

private fun shaderFamily(shader: KslShader): BlazeKoolShaderFamily {
	return when (shader) {
		is KslPbrSplatShader -> BlazeKoolShaderFamily.PBR_SPLAT
		is KslPbrShader -> BlazeKoolShaderFamily.PBR
		is KslBlinnPhongShader -> BlazeKoolShaderFamily.BLINN_PHONG
		is KslLitShader -> BlazeKoolShaderFamily.LIT
		is KslUnlitShader -> BlazeKoolShaderFamily.UNLIT
		else -> BlazeKoolShaderFamily.CUSTOM
	}
}

private fun alphaMode(shader: KslShader): AlphaMode? {
	return when (shader) {
		is KslPbrSplatShader -> shader.cfg.alphaMode
		is KslLitShader -> shader.cfg.alphaMode
		is KslUnlitShader -> null
		else -> null
	}
}

private fun colorSpace(shader: KslShader): BlazeKoolShaderColorSpace {
	val conversion = when (shader) {
		is KslPbrSplatShader -> shader.cfg.colorSpaceConversion
		is KslLitShader -> shader.cfg.colorSpaceConversion
		is KslUnlitShader -> ColorSpaceConversion.AsIs
		else -> ColorSpaceConversion.AsIs
	}
	return when (conversion) {
		ColorSpaceConversion.AsIs -> BlazeKoolShaderColorSpace.AS_IS
		is ColorSpaceConversion.SrgbToLinear -> BlazeKoolShaderColorSpace.SRGB_TO_LINEAR
		is ColorSpaceConversion.LinearToSrgb -> BlazeKoolShaderColorSpace.LINEAR_TO_SRGB
		is ColorSpaceConversion.LinearToSrgbHdr -> BlazeKoolShaderColorSpace.LINEAR_TO_SRGB_HDR
	}
}

private fun addLitShaderLayers(layers: MutableMap<String, Int>, shader: KslLitShader) {
	addColorBlockLayers(layers, shader.colorCfg)
	addColorBlockLayers(layers, shader.emissionCfg)
	addPropertyBlockLayers(layers, shader.aoCfg)
	addPropertyBlockLayers(layers, shader.displacementCfg)
}

private fun addColorBlockLayers(layers: MutableMap<String, Int>, config: ColorBlockConfig) {
	var index = 0
	while (index < config.colorSources.size) {
		val source = config.colorSources[index]
		if (source is ColorBlockConfig.TextureArrayColor) {
			layers[source.textureName] = source.arrayIndex
		}
		index++
	}
}

private fun addPropertyBlockLayers(layers: MutableMap<String, Int>, config: PropertyBlockConfig) {
	var index = 0
	while (index < config.propertySources.size) {
		val source = config.propertySources[index]
		if (source is PropertyBlockConfig.TextureArrayProperty) {
			layers[source.textureName] = source.arrayIndex
		}
		index++
	}
}

private class Blaze3DKoolShaderCode(
	override val hash: LongHash,
	override val metadata: BlazeKoolShaderMetadata
) : ShaderCode, BlazeKoolShaderMetadataSource

private class Blaze3DKoolComputeShaderCode(override val hash: LongHash) : ComputeShaderCode
