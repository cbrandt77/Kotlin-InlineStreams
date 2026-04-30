// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt
package pkg

import io.github.cbrandt77.kt.inlinestreams.*

public fun dsl(): Int? {
	return listOf("hi", "there", "gamer")
		.inlineStream()
		.map { it.toCharArray() }
		.flatMap { it.toList() }
		.map { it.code }
		.firstOrNull()
}

public inline fun <T, U> map(mapper: (T) -> U, el: T, breakLoop: () -> Unit, downstreamAction: (U) -> Unit) {
	downstreamAction(mapper(el))
}

public inline fun <T, U> flatMap(mapper: (T) -> Iterable<U>, el: T, breakLoop: () -> Unit, downstreamAction: (U) -> Unit) {
	for (it in mapper(el)) {
		downstreamAction(it)
	}
}

public inline fun <T> flatten(el: Iterable<T>, breakLoop: () -> Unit, downstreamAction: (T) -> Unit) {
	flatMap({ it }, el, breakLoop, downstreamAction)
}

public inline fun <T> firstOrNull(el: T, breakLoop: () -> Unit, `set-out`: (T) -> Unit) {
	`set-out`(el)
	breakLoop()
}

public fun transformed(): Int? {
	return run {
		var out: Int? = null
		for (el in listOf("hi", "there", "gamer")) {
			run inlineStream2@ {
				map({ it.toCharArray() }, el, { return@inlineStream2 }) {
					flatMap({ it.toList() }, it, { return@inlineStream2 }, {
						map({ it.code }, it, { return@inlineStream2 }) {
							firstOrNull(it, { return@inlineStream2 }, { out = it })
						}
					})
				}
			}
		}
		return@run out
	}
}

fun box(): String {
	val fromDsl = dsl()
	val fromTransformed = transformed()
	if (fromDsl == fromTransformed)
		return "OK"
	else
		return "Expected $fromTransformed, got $fromDsl"
}