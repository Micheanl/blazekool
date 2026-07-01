package com.micheanl.kool.render.blaze3d

import com.micheanl.kool.api.geometry.BlazeKoolGeometry
import com.micheanl.kool.api.geometry.BlazeKoolPrimitiveGeometry
import com.mojang.blaze3d.vertex.PoseStack
import net.minecraft.client.gui.GuiGraphicsExtractor
import de.fabmax.kool.pipeline.BlendMode
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.SubmitNodeCollector
import java.util.concurrent.CopyOnWriteArrayList

class BlazeKoolRenderBridge {
	private val geometries = CopyOnWriteArrayList<BlazeKoolGeometry>()
	private val koolGeometries = CopyOnWriteArrayList<BlazeKoolGeometry>()
	private val solidDirectDraws = CopyOnWriteArrayList<BlazeKoolDirectDrawCommand>()
	private val translucentDirectDraws = CopyOnWriteArrayList<BlazeKoolDirectDrawCommand>()
	private val directRenderer = BlazeKoolDirectDrawRenderer()
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
		clearDirectDraws()
	}

	fun replaceKoolGeometry(nextGeometry: List<BlazeKoolGeometry>) {
		koolGeometries.clear()
		koolGeometries.addAll(nextGeometry)
	}

	fun replaceDirectDraws(nextDraws: List<BlazeKoolDirectDrawCommand>) {
		solidDirectDraws.clear()
		translucentDirectDraws.clear()
		var index = 0
		while (index < nextDraws.size) {
			val draw = nextDraws[index]
			if (draw.renderState.blendMode == BlendMode.DISABLED) {
				solidDirectDraws.add(draw)
			} else {
				translucentDirectDraws.add(draw)
			}
			index++
		}
	}

	fun clearKoolGeometry() {
		koolGeometries.clear()
		clearDirectDraws()
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

	fun renderSolidDirect(context: LevelRenderContext) {
		directRenderer.render(context, solidDirectDraws)
	}

	fun renderTranslucentDirect(context: LevelRenderContext) {
		val cameraPosition = Minecraft.getInstance().gameRenderer.mainCamera().position()
		val sortedDraws = translucentDirectDraws.sortedByDescending { draw ->
			draw.distanceSquaredTo(cameraPosition.x, cameraPosition.y, cameraPosition.z)
		}
		directRenderer.render(context, sortedDraws)
	}

	fun extractGui(graphics: GuiGraphicsExtractor) {
		var index = 0
		while (index < koolGeometries.size) {
			val geometry = koolGeometries[index]
			if (geometry is BlazeKoolPrimitiveGeometry && BlazeKoolGuiGeometryRenderState.accepts(geometry)) {
				graphics.guiRenderState.addGuiElement(BlazeKoolGuiGeometryRenderState(geometry))
			}
			index++
		}
	}

	private fun clearDirectDraws() {
		solidDirectDraws.clear()
		translucentDirectDraws.clear()
	}
}
