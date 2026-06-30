package com.micheanl.kool.api.compute

fun interface BlazeKoolComputeExecutor {
	fun dispatch(context: BlazeKoolComputeContext)
}
