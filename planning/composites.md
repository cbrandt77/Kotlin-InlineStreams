
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


```kotlin
// During the loop function generation phase, 
// crap wait no but we can't reference loop functions BEFORE we generate them aaaaaaa we have to make sure whatever's being referenced is generated first
fun <T> InlineStream<T>.composite(arg: String): InlineStream<T> {
	return this.op1().op2(arg)
}
```