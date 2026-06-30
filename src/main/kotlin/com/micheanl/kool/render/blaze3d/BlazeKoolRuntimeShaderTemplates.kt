package com.micheanl.kool.render.blaze3d

import java.nio.charset.StandardCharsets

object BlazeKoolRuntimeShaderTemplates {
	private const val TEMPLATE_ROOT = "/assets/blazekool/shaders/runtime/"
	private val vertexTemplate by lazy { load("ksl_minecraft_runtime.vsh") }
	private val fragmentTemplate by lazy { load("ksl_minecraft_runtime.fsh") }
	private val lightingUniforms by lazy { load("lighting_uniforms.glsl") }
	private val fogUniforms by lazy { load("fog_uniforms.glsl") }
	private val lightingFunctions by lazy { load("lighting_functions.glsl") }
	private val fogFunctions by lazy { load("fog_functions.glsl") }
	private val colorSpaceFunctions by lazy { load("colorspace_functions.glsl") }
	private val lightingVertexVaryings by lazy { load("lighting_vertex_varyings.glsl") }
	private val lightingFragmentVaryings by lazy { load("lighting_fragment_varyings.glsl") }
	private val fogVertexVaryings by lazy { load("fog_vertex_varyings.glsl") }
	private val fogFragmentVaryings by lazy { load("fog_fragment_varyings.glsl") }
	private val lightingVertexBody by lazy { load("lighting_vertex_body.glsl") }
	private val lightingFragmentBody by lazy { load("lighting_fragment_body.glsl") }
	private val fogVertexBody by lazy { load("fog_vertex_body.glsl") }
	private val fogFragmentBody by lazy { load("fog_fragment_body.glsl") }
	private val alphaDefaultBody by lazy { load("alpha_default_body.glsl") }
	private val alphaCutoutBody by lazy { load("alpha_cutout_body.glsl") }
	private val opaqueAlphaBody by lazy { load("opaque_alpha_body.glsl") }
	private val premultipliedAlphaBody by lazy { load("premultiplied_alpha_body.glsl") }
	private val srgbToLinearBody by lazy { load("srgb_to_linear_body.glsl") }
	private val linearToSrgbBody by lazy { load("linear_to_srgb_body.glsl") }
	private val linearToSrgbHdrBody by lazy { load("linear_to_srgb_hdr_body.glsl") }

	fun glslVersionDirective(): String {
		return load("version.glsl").trim()
	}

	fun vertex(mapping: BlazeKoolShaderMapping): String {
		return render(
			vertexTemplate,
			mapOf(
				"GLSL_VERSION" to glslVersionDirective(),
				"LIGHTING_UNIFORMS" to enabled(mapping.usesLighting, lightingUniforms),
				"FOG_UNIFORMS" to enabled(mapping.usesFog, fogUniforms),
				"LIGHTING_FUNCTIONS" to enabled(mapping.usesLighting, lightingFunctions),
				"FOG_FUNCTIONS" to enabled(mapping.usesFog, fogFunctions),
				"LIGHTING_VARYINGS" to enabled(mapping.usesLighting, lightingVertexVaryings),
				"FOG_VARYINGS" to enabled(mapping.usesFog, fogVertexVaryings),
				"LIGHTING_BODY" to enabled(mapping.usesLighting, lightingVertexBody),
				"FOG_BODY" to enabled(mapping.usesFog, fogVertexBody)
			)
		)
	}

	fun fragment(mapping: BlazeKoolShaderMapping): String {
		return render(
			fragmentTemplate,
			mapOf(
				"GLSL_VERSION" to glslVersionDirective(),
				"FOG_UNIFORMS" to enabled(mapping.usesFog, fogUniforms),
				"FOG_FUNCTIONS" to enabled(mapping.usesFog, fogFunctions),
				"COLORSPACE_FUNCTIONS" to colorSpaceFunctions,
				"LIGHTING_VARYINGS" to enabled(mapping.usesLighting, lightingFragmentVaryings),
				"FOG_VARYINGS" to enabled(mapping.usesFog, fogFragmentVaryings),
				"ALPHA_BODY" to alphaBody(mapping.alphaCutoff),
				"OPAQUE_ALPHA_BODY" to enabled(mapping.opaqueAlpha, opaqueAlphaBody),
				"LIGHTING_BODY" to enabled(mapping.usesLighting, lightingFragmentBody),
				"COLORSPACE_BODY" to colorSpaceBody(mapping.colorSpace),
				"PREMULTIPLIED_ALPHA_BODY" to enabled(mapping.premultipliedAlpha, premultipliedAlphaBody),
				"FOG_BODY" to enabled(mapping.usesFog, fogFragmentBody)
			)
		)
	}

	private fun alphaBody(alphaCutoff: Float?): String {
		val template = if (alphaCutoff == null) alphaDefaultBody else alphaCutoutBody
		return render(template, mapOf("ALPHA_CUTOFF" to alphaCutoffLiteral(alphaCutoff)))
	}

	private fun colorSpaceBody(colorSpace: BlazeKoolShaderColorSpace): String {
		return when (colorSpace) {
			BlazeKoolShaderColorSpace.AS_IS -> ""
			BlazeKoolShaderColorSpace.SRGB_TO_LINEAR -> srgbToLinearBody
			BlazeKoolShaderColorSpace.LINEAR_TO_SRGB -> linearToSrgbBody
			BlazeKoolShaderColorSpace.LINEAR_TO_SRGB_HDR -> linearToSrgbHdrBody
		}
	}

	private fun enabled(condition: Boolean, source: String): String {
		return if (condition) source else ""
	}

	private fun render(template: String, values: Map<String, String>): String {
		var rendered = template
		values.forEach { (name, value) ->
			rendered = rendered.replace("\${$name}", value)
		}
		return rendered
	}

	private fun alphaCutoffLiteral(value: Float?): String {
		val finite = value?.takeIf { it.isFinite() } ?: 0.0f
		return finite.toString()
	}

	private fun load(name: String): String {
		val stream = BlazeKoolRuntimeShaderTemplates::class.java.getResourceAsStream(TEMPLATE_ROOT + name)
			?: error("Missing BlazeKool runtime shader template: $name")
		return stream.use { input -> String(input.readAllBytes(), StandardCharsets.UTF_8) }.trimEnd()
	}
}
