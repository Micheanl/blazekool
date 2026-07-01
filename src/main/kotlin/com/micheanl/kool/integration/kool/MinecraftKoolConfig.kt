package com.micheanl.kool.integration.kool

import de.fabmax.kool.KoolConfigJvm
import de.fabmax.kool.math.Vec2i
import net.minecraft.client.Minecraft
import java.nio.file.Files

object MinecraftKoolConfig {
	fun create(client: Minecraft, window: MinecraftKoolWindow): KoolConfigJvm {
		val baseDir = client.gameDirectory.toPath().resolve("blazekool")
		val storageDir = baseDir.resolve("storage")
		val httpCacheDir = baseDir.resolve("http-cache")
		Files.createDirectories(storageDir)
		Files.createDirectories(httpCacheDir)

		return KoolConfigJvm(
			defaultAssetLoader = MinecraftKoolAssetLoader(),
			defaultFont = KoolConfigJvm.DEFAULT_MSDF_FONT_INFO,
			numSamples = 1,
			storageDir = storageDir.toString(),
			httpCacheDir = httpCacheDir.toString(),
			windowSubsystem = MinecraftKoolWindowSubsystem(client, window),
			windowTitle = "BlazeKool",
			windowSize = Vec2i(client.window.width, client.window.height),
			asyncSceneUpdate = false
		)
	}
}
