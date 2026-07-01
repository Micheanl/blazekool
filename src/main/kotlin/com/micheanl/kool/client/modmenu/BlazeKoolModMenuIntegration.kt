package com.micheanl.kool.client.modmenu

import com.micheanl.kool.client.gui.BlazeKoolDemoScreen
import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi

class BlazeKoolModMenuIntegration : ModMenuApi {
	override fun getModConfigScreenFactory(): ConfigScreenFactory<BlazeKoolDemoScreen> {
		return ConfigScreenFactory { parent -> BlazeKoolDemoScreen(parent, "ui") }
	}
}
