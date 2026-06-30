package com.micheanl.kool.api.geometry

import kotlin.math.roundToInt

fun blazeKoolColor(red: Float, green: Float, blue: Float, alpha: Float): Int {
	val a = colorChannel(alpha)
	val r = colorChannel(red)
	val g = colorChannel(green)
	val b = colorChannel(blue)
	return a shl 24 or (r shl 16) or (g shl 8) or b
}

private fun colorChannel(value: Float): Int = (value.coerceIn(0.0f, 1.0f) * 255.0f).roundToInt()
