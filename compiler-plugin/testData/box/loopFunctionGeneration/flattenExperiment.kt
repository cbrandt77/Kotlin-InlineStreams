import io.github.cbrandt77.kt.inlinestreams.*

fun <T> InlineStream<Iterable<T>>.flatten(): InlineStream<T> {
	var state = 4
	
	return intermediateOperation { el, breakLoop, downstreamAction ->
		for (item in el) {
			downstreamAction(item)
		}
	}
}

fun box(): String {
	return "OK"
}