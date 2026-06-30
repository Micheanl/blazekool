package com.micheanl.kool.integration.kool

import de.fabmax.kool.AssetLoader
import de.fabmax.kool.AssetRef
import de.fabmax.kool.Assets
import de.fabmax.kool.LoadedAsset
import de.fabmax.kool.MimeType
import de.fabmax.kool.math.Vec2i
import de.fabmax.kool.modules.audio.AudioClipImpl
import de.fabmax.kool.pipeline.BufferedImageData2d
import de.fabmax.kool.pipeline.ImageData2d
import de.fabmax.kool.pipeline.ImageData3d
import de.fabmax.kool.pipeline.TexFormat
import de.fabmax.kool.platform.HttpCache
import de.fabmax.kool.platform.ImageDecoder
import de.fabmax.kool.platform.imageAtlasTextureData
import de.fabmax.kool.util.Uint8BufferImpl
import net.minecraft.client.Minecraft
import net.minecraft.resources.Identifier
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.net.URI
import java.util.Base64

class MinecraftKoolAssetLoader : AssetLoader() {
	override suspend fun loadAudio(ref: AssetRef.Audio): LoadedAsset.Audio {
		return LoadedAsset.Audio(ref, runCatching {
			AudioClipImpl(readAsset(ref.path), ref.path.substringAfterLast('.').lowercase())
		})
	}

	override suspend fun loadBlob(ref: AssetRef.Blob): LoadedAsset.Blob {
		return LoadedAsset.Blob(ref, runCatching { Uint8BufferImpl(readAsset(ref.path)) })
	}

	override suspend fun loadBufferedImage2d(ref: AssetRef.BufferedImage2d): LoadedAsset.BufferedImage2d {
		return LoadedAsset.BufferedImage2d(ref, loadImage(ref.path, ref.format, ref.resolveSize))
	}

	override suspend fun loadImage2d(ref: AssetRef.Image2d): LoadedAsset.Image2d {
		return LoadedAsset.Image2d(ref, loadImage(ref.path, ref.format, ref.resolveSize))
	}

	override suspend fun loadImageAtlas(ref: AssetRef.ImageAtlas): LoadedAsset.ImageAtlas {
		val imageRef = AssetRef.BufferedImage2d(ref.path, ref.format, ref.resolveSize)
		return LoadedAsset.ImageAtlas(ref, loadBufferedImage2d(imageRef).result.mapCatching {
			imageAtlasTextureData(it, ref.tilesX, ref.tilesY)
		})
	}

	private fun loadImage(path: String, format: TexFormat): Result<BufferedImageData2d> = runCatching {
		decodeImage(path, format, null)
	}

	private fun loadImage(path: String, format: TexFormat, resolveSize: Vec2i?): Result<BufferedImageData2d> {
		return runCatching { decodeImage(path, format, resolveSize) }
	}

	private fun decodeImage(path: String, format: TexFormat, resolveSize: Vec2i?): BufferedImageData2d {
		val bytes = readAsset(path)
		val mimeType = mimeType(path)
		return when (mimeType) {
			MimeType.IMAGE_SVG -> ImageDecoder.loadSvg(ByteArrayInputStream(bytes), format, resolveSize)
			else -> ImageDecoder.loadImage(ByteArrayInputStream(bytes), format, resolveSize)
		}
	}

	private fun readAsset(path: String): ByteArray {
		if (Assets.isDataUri(path)) {
			return decodeDataUri(path)
		}
		if (path.startsWith("http://", true) || path.startsWith("https://", true)) {
			return readHttpAsset(path)
		}
		val resourceId = Identifier.parse(path)
		val resource = Minecraft.getInstance().resourceManager.getResourceOrThrow(resourceId)
		return resource.open().use { input -> input.readBytes() }
	}

	private fun readHttpAsset(path: String): ByteArray {
		val cached = HttpCache.loadHttpResource(path)
		if (cached != null) {
			return cached.readBytes()
		}
		return URI(path).toURL().openStream().use { input -> input.readBytes() }
	}

	private fun decodeDataUri(path: String): ByteArray {
		val dataIndex = path.indexOf(";base64,")
		if (dataIndex < 0) {
			throw FileNotFoundException(path)
		}
		return Base64.getDecoder().decode(path.substring(dataIndex + 8))
	}

	private fun mimeType(path: String): MimeType {
		if (path.startsWith("data:image/svg+xml", true)) {
			return MimeType.IMAGE_SVG
		}
		if (path.startsWith("data:image/png", true)) {
			return MimeType.IMAGE_PNG
		}
		if (path.startsWith("data:image/jpeg", true) || path.startsWith("data:image/jpg", true)) {
			return MimeType.IMAGE_JPG
		}
		return MimeType.forFileName(path.substringBefore('?'))
	}
}
