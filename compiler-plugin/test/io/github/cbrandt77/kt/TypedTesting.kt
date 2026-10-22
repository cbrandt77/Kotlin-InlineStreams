package io.github.cbrandt77.kt



fun <FUNC : Function<*>> performAction(action: FUNC) {
//	run {
//		val x = { return@run it }
//	}
}

fun foo() {
	// !!!!!
	performAction<(String, Int) -> Unit> { string, i ->
	
	}
}

