
Assumptions:
- Composite operations only have one return statement
- Composite operations are a macro for a chain and fill in their chain

```kotlin
// Intermediates
fun <T> InlineStream<Iterable<T>>.flatten(): InlineStream<T> {
     return flatMap { it: T -> it }
}
fun <T, U : Any> InlineStream<T>.mapNotNull(mapper: (T) -> U?): InlineStream<U> {
     return map(mapper).filterNonNull()
}
fun <T : Any> InlineStream<T?>.filterNonNull(): InlineStream<T> {
     return filter { it: T -> it != null }
}

// Terminal
fun <T> InlineStream<T>.first(): T {
     return firstOrNull() ?: error("...")
}

// Also have to be on the lookout for:
fun <T> InlineStream<T>.customThing(): T {
     return map { it.something }
            .intermediateOperation { el, breakLoop, downstreamAction ->
                    
             }
}
// ok it probably IS better to flatten these beforehand.....
```


So, how do we do this?


Intermediate operations:

```kotlin
fun <T> InlineStream<T>.composite(arg: String): InlineStream<T> {
	return this.op1().op2(arg)
}

// becomes:
fun <T> InlineStream<T>.composite(arg: String): InlineStream<T> {
	val op1_state: Int = 4
    var op2_state: String = "foo"
	
	return intermediateOperation { el, breakLoop, downstreamAction ->
		loopAction-op1(el, breakLoop, downstreamAction = { el2 ->
			loopAction-op2(
                arg, 
                el2, breakLoop, downstreamAction = downstreamAction, 
                `<get-op2_state>` = { op2_state }, 
                `<set-op2_state>` = { op2_state = it }
			)
        }, `<get-op1_state>` = { op1_state })
    }
}
```

# Terminal operations:
given:
```kotlin
fun <T> InlineStream<T>.firstOrNull(): T? {
	var out: T? = null
    
    terminalOperation { el, breakLoop ->
		out = el
        breakLoop()
    }
    
    return out;
}
```

We do this:
```kotlin
// Source code:
fun <T> InlineStream<T>.first(): T {
	return firstOrNull() ?: throw NoSuchElementException()
}
// Actual IR:
fun <T> InlineStream<T>.first(): T {
	return when (val tmp_0 = firstOrNull()) {
		null -> throw NoSuchElementException()
        else -> tmp_0
	}
}

// Transformed IR:
fun <T> InlineStream<T>.first(): T {
	var `0$firstOrNull$out`: T? = null
    
    terminalOperation { el, breakLoop ->
		loopAction-firstOrNull(el, breakLoop, `<set-out>` = { `0$firstOrNull$out` = it })
    }
    
    return when (val tmp_0 = run { 
		return@run `0$firstOrNull$out`;
	}) { // replace firstOrNull call with EPILOGUE of firstOrNull, with variable and return symbols redirected 
		null -> throw NoSuchElementException()
	}
}
```