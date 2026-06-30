package com.micheanl.kool.engine

import com.mojang.blaze3d.systems.DeviceFeatures
import com.mojang.blaze3d.systems.DeviceInfo
import com.mojang.blaze3d.systems.DeviceLimits

data class BlazeKoolDevice(
	val backendName: String,
	val deviceName: String,
	val vendorName: String,
	val driverInfo: String,
	val depthZeroToOne: Boolean,
	val limits: DeviceLimits,
	val features: DeviceFeatures
) {
	val supportsIndirectDraw: Boolean
		get() = features.drawIndirect()

	val supportsMultiDrawIndirect: Boolean
		get() = features.multiDrawIndirect()

	companion object {
		fun from(info: DeviceInfo): BlazeKoolDevice = BlazeKoolDevice(
			backendName = info.backendName(),
			deviceName = info.name(),
			vendorName = info.vendorName(),
			driverInfo = info.driverInfo(),
			depthZeroToOne = info.isZZeroToOne(),
			limits = info.limits(),
			features = info.features()
		)
	}
}
