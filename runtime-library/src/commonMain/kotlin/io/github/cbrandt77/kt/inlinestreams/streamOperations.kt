@file:Suppress("NOTHING_TO_INLINE", "RedundantUnitReturnType", "UnusedReceiverParameter")

package io.github.cbrandt77.kt.inlinestreams

import io.github.cbrandt77.kt.inlinestreams.intermediateOperation

public inline fun <T, U> InlineStream<T>.map(transformation: (T) -> U): InlineStream<U> {
	return intermediateOperation { element, _, downstreamAction ->
		downstreamAction(transformation(element))
	}
}

// TODO add array versions of these
public inline fun <T, U> InlineStream<T>.flatMap(transformation: (T) -> Iterable<U>): InlineStream<U> {
	return intermediateOperation { element, _, downstreamAction ->
		for (it in transformation(element)) {
			downstreamAction(it)
		}
	}
}

public inline fun <T> InlineStream<Iterable<T>>.flatten(): InlineStream<T> {
	return flatMap { it }
}

public inline fun <T> InlineStream<T>.filter(predicate: (T) -> Boolean): InlineStream<T> {
	return intermediateOperation { element, _, downstreamAction ->
		if (predicate(element)) {
			downstreamAction(element)
		}
	}
}

public inline fun <T : Any> InlineStream<T?>.filterNotNull(): InlineStream<T> {
	return intermediateOperation { element, _, downstreamAction ->
		if (element != null) {
			downstreamAction(element)
		}
	}
}


public inline fun <T> InlineStream<T>.drop(numToDrop: Int): InlineStream<T> {
	var numDropped = 0
	
	return intermediateOperation { element, _, downstreamAction ->
		if (numDropped < numToDrop) {
			++numDropped
		} else {
			downstreamAction(element)
		}
	}
}


public inline fun <T> InlineStream<T>.take(numToTake: Int): InlineStream<T> {
	var numTaken = 0
	
	return intermediateOperation { element, breakLoop, downstreamAction ->
		if (numTaken < numToTake) {
			++numTaken
			
			downstreamAction(element)
			
			if (numTaken == numToTake) {
				breakLoop()
			}
		}
	}
}


public inline fun <T> InlineStream<T>.forEach(action: (T) -> Unit): Unit {
	intermediateOperation<T, T> { element, _, _ ->
		action(element)
	}
}

public inline fun <T> InlineStream<T>.onEach(action: (T) -> Unit): InlineStream<T> {
	return intermediateOperation { element, _, downstreamAction ->
		action(element)
		downstreamAction(element)
	}
}

public inline fun <T, OUT : Collection<T>> InlineStream<T>.collect(collectionFactory: () -> OUT, accumulator: (OUT, T) -> Unit): OUT {
	val outCollection = collectionFactory()
	
	terminalOperation { el, _ ->
		accumulator(outCollection, el)
	}
	
	return outCollection;
}

public inline fun <T> InlineStream<T>.toArrayList(): ArrayList<T> {
	return collect({ ArrayList() }, { list, el -> list.add(el) })
}

public inline fun <T> InlineStream<T>.toMutableList(): MutableList<T> {
	return toArrayList()
}

public inline fun <T> InlineStream<T>.toList(): List<T> {
	return toArrayList().compactIfPossible()
}

// this is really a composed map and filter, so we chain the two like we'd do outside the loop
public inline fun <T, U : Any> InlineStream<T>.mapNotNull(mapper: (T) -> U?): InlineStream<U> {
	return map(mapper).filterNotNull()
}

public inline fun <T> InlineStream<T>.firstOrNull(): T? {
	var out: T? = null
	
	terminalOperation { element, breakLoop ->
		out = element
		breakLoop()
	}
	
	return out;
}

public inline fun <T> InlineStream<T>.first(): T {
	return firstOrNull() ?: throw NoSuchElementException("'first' expected element")
}