package com.micheanl.kool.api.geometry

data class BlazeKoolVertex(
	val x: Float,
	val y: Float,
	val z: Float,
	val color: Int,
	val nx: Float,
	val ny: Float,
	val nz: Float,
	val u: Float = 0.0f,
	val v: Float = 0.0f
)
