package com.micheanl.kool.api.geometry

import com.mojang.blaze3d.PrimitiveTopology
import de.fabmax.kool.scene.geometry.PrimitiveType

enum class BlazeKoolPrimitiveType(
	val topology: PrimitiveTopology,
	val koolPrimitiveType: PrimitiveType?
) {
	TRIANGLES(PrimitiveTopology.TRIANGLES, PrimitiveType.TRIANGLES),
	TRIANGLE_STRIP(PrimitiveTopology.TRIANGLE_STRIP, PrimitiveType.TRIANGLE_STRIP),
	LINES(PrimitiveTopology.LINES, PrimitiveType.LINES),
	POINTS(PrimitiveTopology.POINTS, PrimitiveType.POINTS)
}
