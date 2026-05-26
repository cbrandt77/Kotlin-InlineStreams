// RUN_PIPELINE_TILL: BACKEND
// MODULE: main
// FILE: Main.kt
package pkg

import io.github.cbrandt77.kt.inlinestreams.*

inline fun <T, U> InlineStream<T>.map(mapper: (T) -> U): InlineStream<U> {
	return intermediateOperation { el, _, downstreamAction ->
		downstreamAction(mapper(el))
	}
}

inline fun <T, U> InlineStream<T>.flatMap(mapper: (T) -> Iterable<U>): InlineStream<U> {
	return intermediateOperation { el, _, downstreamAction ->
		for (it in mapper(el)) {
			downstreamAction(it)
		}
	}
}

fun <T> InlineStream<T>.drop(n: Int): InlineStream<T> {
	var numDropped = 0
	
	return intermediateOperation { el, _, downstreamAction ->
		if (numDropped < n) {
			++numDropped
		} else {
			downstreamAction(el)
		}
	}
}

fun <T> InlineStream<T>.firstOrNull(): T? {
	var out: T? = null;
	
	terminalOperation { el, breakLoop ->
		out = el
		breakLoop()
	}
	
	return out;
}

public fun dsl(): Int? {
	return listOf("hi", "there", "gamer")
		.inlineStream()
		.map { it.toCharArray() }
		.flatMap { it.toList() }
		.drop(4)
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

public inline fun <T> firstOrNull(el: T, breakLoop: () -> Unit, `set-out`: (T?) -> Unit) {
	`set-out`(el)
	breakLoop()
}

public inline fun <T> drop(`arg-n`: Int, el: T, breakLoop: () -> Unit, downstreamAction: (T) -> Unit, `get-numDropped`: () -> Int, `set-numDropped`: (Int) -> Unit) {
	if (`get-numDropped`() < `arg-n`) {
		`set-numDropped`(`get-numDropped`() + 1)
	} else {
		downstreamAction(el)
	}
}

public fun transformed(): Int? {
	return run {
		val `2$arg$drop$n` = 4
		var `2$drop$numDropped` = 0
		
		var `4$firstOrNull$out`: Int? = null
		run inlineStream2@ {
			for (el in listOf("hi", "there", "gamer")) {
				map({ it.toCharArray() }, el, { return@inlineStream2 }) {
					flatMap({ it.toList() }, it, { return@inlineStream2 }, {
						drop(`2$arg$drop$n`, it, { return@inlineStream2 }, {
							map({ it.code }, it, { return@inlineStream2 }) {
								firstOrNull(it, { return@inlineStream2 }, { `4$firstOrNull$out` = it })
							}
						}, { `2$drop$numDropped` }, { `2$drop$numDropped` = it })
					})
				}
			}
		}
		return@run `4$firstOrNull$out`
	}
}

fun sequence(): Int? {
	 return sequenceOf("hi", "there", "gamer")
		.map { it.toCharArray() }
		.flatMap { it.toList() }
		.drop(4)
		.map { it.code }
		.firstOrNull()
}

fun box(): String {
	val fromDsl = dsl()
	val fromTransformed = transformed()
	val fromSequence = sequence()
	if (fromDsl == fromTransformed && fromDsl == fromSequence)
		return "OK"
	else if (fromTransformed != fromSequence)
		return "The manually-transformed version is wrong. Got $fromTransformed instead of $fromSequence"
	else if (fromDsl != fromSequence)
		return "Expected: $fromSequence. From dsl function: $fromDsl"
	else
		return "Fallthrough"
}