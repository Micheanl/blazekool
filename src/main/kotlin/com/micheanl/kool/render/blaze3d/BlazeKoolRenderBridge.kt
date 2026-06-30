package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.mojang.blaze3d.vertex.PoseStack
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.renderer.SubmitNodeCollector
import java.util.concurrent.CopyOnWriteArrayList

class BlazeKoolRenderBridge {
	private val geometries = CopyOnWriteArrayList<BlazeKoolGeometry>()
	private val koolGeometries = CopyOnWriteArrayList<BlazeKoolGeometry>()
	private var width = 1
	private var height = 1

	fun resize(width: Int, height: Int) {
		this.width = width.coerceAtLeast(1)
		this.height = height.coerceAtLeast(1)
	}

	fun add(geometry: BlazeKoolGeometry) {
		geometries.addIfAbsent(geometry)
	}

	fun remove(geometry: BlazeKoolGeometry) {
		geometries.remove(geometry)
	}

	fun clear() {
		geometries.clear()
		koolGeometries.clear()
	}

	fun replaceKoolGeometry(nextGeometry: List<BlazeKoolGeometry>) {
		koolGeometries.clear()
		koolGeometries.addAll(nextGeometry)
	}

	fun clearKoolGeometry() {
		koolGeometries.clear()
	}

	fun collectSubmits(context: LevelRenderContext) {
		val poseStack = context.poseStack()
		val collector = context.submitNodeCollector()
		submitGeometries(collector, poseStack, koolGeometries)
		submitGeometries(collector, poseStack, geometries)
	}

	private fun submitGeometries(collector: SubmitNodeCollector, poseStack: PoseStack, source: List<BlazeKoolGeometry>) {
		val size = source.size
		var index = 0
		while (index < size) {
			val geometry = source[index]
			collector.submitCustomGeometry(poseStack, geometry.renderType, geometry::submit)
			index++
		}
	}
}
