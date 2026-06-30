package com.micheanl.kool.render.blaze3d

import de.fabmax.kool.util.LongHash
import net.minecraft.resources.Identifier

data class BlazeKoolShaderMetadata(
	val textureArrayLayers: Map<String, Int>,
	val shaderPipeline: BlazeKoolShaderPipeline? = null
) {
	companion object {
		val EMPTY: BlazeKoolShaderMetadata = BlazeKoolShaderMetadata(emptyMap())
	}
}

interface BlazeKoolShaderMetadataSource {
	val metadata: BlazeKoolShaderMetadata
}

class BlazeKoolShaderPipeline(
	val shaderKey: LongHash,
	val vertexShader: Identifier,
	val fragmentShader: Identifier,
	val sources: BlazeKoolGeneratedShaderSources,
	val mapping: BlazeKoolShaderMapping
) {
	override fun equals(other: Any?): Boolean {
		return this === other || other is BlazeKoolShaderPipeline &&
			shaderKey == other.shaderKey &&
			vertexShader == other.vertexShader &&
			fragmentShader == other.fragmentShader
	}

	override fun hashCode(): Int {
		var result = shaderKey.hashCode()
		result = 31 * result + vertexShader.hashCode()
		result = 31 * result + fragmentShader.hashCode()
		return result
	}
}

data class BlazeKoolGeneratedShaderSources(
	val blazeVertexSource: String,
	val blazeFragmentSource: String,
	val openGlVertexSource: String,
	val openGlFragmentSource: String,
	val vulkanVertexSource: String,
	val vulkanFragmentSource: String,
	val wgslVertexSource: String,
	val wgslFragmentSource: String
)

data class BlazeKoolShaderMapping(
	val family: BlazeKoolShaderFamily,
	val usesLighting: Boolean,
	val usesFog: Boolean,
	val premultipliedAlpha: Boolean,
	val opaqueAlpha: Boolean,
	val alphaCutoff: Float?,
	val colorSpace: BlazeKoolShaderColorSpace
)

enum class BlazeKoolShaderFamily {
	UNLIT,
	LIT,
	BLINN_PHONG,
	PBR,
	PBR_SPLAT,
	CUSTOM
}

enum class BlazeKoolShaderColorSpace {
	AS_IS,
	SRGB_TO_LINEAR,
	LINEAR_TO_SRGB,
	LINEAR_TO_SRGB_HDR
}
