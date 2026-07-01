package com.micheanl.kool.client.gui

import com.micheanl.kool.client.demo.BlazeKoolDemoRuntime
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

class BlazeKoolDemoScreen(
	private val parent: Screen?,
	private val startDemo: String?
) : Screen(Component.literal("BlazeKool Kool Demo")) {
	override fun init() {
		BlazeKoolDemoRuntime.ensureDemoLoaded(minecraft, startDemo)
	}

	override fun extractBackground(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
	}

	override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, a: Float) {
		BlazeKoolDemoRuntime.ensureDemoLoaded(minecraft, startDemo)
		BlazeKoolDemoRuntime.extractGui(graphics)
	}

	override fun onClose() {
		minecraft.gui.setScreen(parent)
	}

	override fun isPauseScreen(): Boolean {
		return false
	}
}
