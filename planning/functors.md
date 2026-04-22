Note: `getEl` must return `Either<T, EOF>`, allowing the functions to terminate early.

Also, wait, there is a problem: if we're inlining all of these, all of that EOF checking seems silly....
Ah well, I'll think about it later. Right now I just want to focus on chaining.



# Filter
Intent: Only allow items matching the predicate to proceed to the next step

// OHHH WE'RE PASSING getEl FUNCTIONS TO EACH OTHER!!!!!!!! AAAAAA THIS IS SO COOL
//      (though under this, we're actually abandoning the idea of a "forEach terminal operation".)
//      (Instead, this can only iterate over things. Which is fine, since this is a substitute for loops, but it's something to be noted.)
```kotlin
fun doFilter(getEl: () -> T, predicate: (T) -> Boolean): T {
	while (true) {
		val el = getEl()
        if (predicate(el))
			return el;
	}
    throw error();
}
```


# Drop N Items
```kotlin
fun doDrop(getElement: () -> T, toDrop: Int): T {
	for (i in range(toDrop)) {
		getElement() // abandon these
	}
    return getElement()
}
```

# Map
```kotlin
fun doMap(getElement: () -> T, mapper: (T) -> U): U {
	return mapper(getElement())
}
```

wait... but then how do we chain things?

ok for example, let's do drop then map:
```kotlin
sequence.drop(4).map { it.thing }

// under our new framework, this would be the function:
{ 
	{ 
        for (i in range(4)) {
            sequence.next()
        }
        return sequence.next();
    }().thing
}
```

wait but what if we do `drop(4).drop(4)`? you'd think this would be `drop(8)`, but it's actually `drop(4 * 4)`, 
and I don't think that's how it works in sequences...
wait yeah and for every single element, it'll drop 4 elements.  So drop and take don't work in pure functions.

so we need to figure out how to structure this so it's "persistent", or rather, doesn't get repeated......
well... if we're talking about a loop, you can't do that. And again, this is just a substitute for a loop.


... wait no I think I'm _over_-thinking it now, I'm literally just trying to inline this. If a portion needs state, just add a section to the terminal function that can store state as like variables or something idk
I forgot to leverage the medium I'm working in