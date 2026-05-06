package pkg

import io.github.cbrandt77.kt.inlinestreams.*

fun <T> InlineStream<T>.testOperation(functor: (T) -> T): InlineStream<T> {
	return intermediateOperation { el, breakLoop, downstreamAction ->
		println(functor)
		downstreamAction(el)
		breakLoop()
	}
}
fun box(): String {
	return "OK"
}