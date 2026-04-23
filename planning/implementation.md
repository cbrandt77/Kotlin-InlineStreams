Alright, now how do we actually implement the damn thing

Stipulations:

- Everything pretends it's an actual object, same as with Sequence and Stream
- Said object cannot leave the function it's in if it's not inside an inline function, 
    at least not without being "boxed" into a deferred sequence
    - wait, if we allow inline functions... we could actually make a full framework for this
- Chaining functions actually nests them
- All of this is inlined
- Each "step" is given a "get next element function" instead of the element itself so that it can fast-forward the stream if it needs to
  - Things like "filter" will need to fast-forward the stream

Problems:

- wait a second, how does "dropping" work if the functors can't hold state between iterations?
  - that would have to be in the terminal operation, but then people will want to chain stuff after that too
  - so do we pass each thing a "get next element" function instead of the element itsel- OH MY GOD RIGHT RIGHT RIGHT LAMBDA CALCULUS STRIKES AGAIN
  - if we give it a "get next element" function, it can freely cycle through... uhhh WAIT YEAH OF COURSE yeah we give
    the inner step a modified getNextElement function that counts how many it's done and drops those? or uhhhh no it can't

```kotlin
typealias SequenceAction<U> = (downstream: (U) -> Unit) -> Unit

// stub like with contracts, that returns a typed singleton for type-safety
inline fun <U> sequenceOperation(action: SequenceAction<U>): Sequence<U> { return Sequence.instance<U>() }
```


```kotlin
class Sequence<T> private constructor() {
	companion object {
		private val _inst = Sequence<Any>()
        
        @JvmStatic fun <T> instance() = _inst as Sequence<T>
	}
    
    operator fun invoke(): T { error("Not inlined") }
}

inline fun Sequence<T>.map(functor: (T) -> U): Sequence<U> {
	// replace the "get" function with invoking the sequence
	return sequenceOperation { downstream ->
		downstream(functor(this()))
	}
}

inline fun Sequence<Iterable<T>>.flatten(): Sequence<T> {
    return sequenceOperation { downstream ->
	    for (el in this()) {
		    downstream(el)
	    }
    }
}

// and then maybe(?) these generate a different inline version that we can actually nest easily at compile-time?
// We replace the chain with the nested calls to this, and ideally, the function inliner can take over from there.
/*inline fun <T> flatten(get: () -> Iterable<T>, downstream: (T) -> Unit) {
	for (el in get()) {
		downstream(el)
	}
}*/

// wait, this doesn't work... this strategy - both a get and a downstream - would only work well for a CUSTOM inliner, not for regular inlining.
// with regular inlining, we end up with a really weird doubly-linked list of actions that doesn't play very well
// so we need a custom chain inliner
```


# Figured it out
When the step is tacked on, only the previous step is known, but that previous step DOES change the `getNextItem` function.

```typescript
// functional form:
let drop = (N: Int) => (getNextElement: () -> T) => (downstream: (T) -> Unit) => {
	for (i in range(N)) {
		getNextElement() // drop it
    }
	for (let el = getNextElement(); el != EOF; el = getNextElement()) {
		downstream(el)
    }
}

let map = (functor: (T) -> U) => (getNextElement: () -> T) => (downstream: (T) -> Unit) => {
	downstream(functor(getNextElement()))
}

function map(functor: (T) -> U, next: StreamOperation<*, T>)


// then assembled with:
map(t => t, drop(4, iter::next))
// such that when each progressive one is called, it provides itself as "downstream" to the getNextElement it's given
```

```kotlin
typealias StreamOperation<in T, out U> = (getNextElement: () -> T, downstream: (U) -> Unit) -> Unit

fun <T, U> map(functor: (T) -> U, previousStep: StreamOperation<*, T>) {
	val action: StreamOperation<T, U> = { getNextElement, downstream ->
		downstream(functor(getNextElement()))
    }
	previousStep.bind(downstream = this) // note: this isn't exactly it, but you get the idea
    return action;
}
```




```kotlin
// The idea is for this:
list.map { it.thing }.map { it.otherThing }.filter { it is Something }.collect(Collectors.toList())

// to evaluate to this:
val outList = mutableListOf()
for (el in list) {
	val x = el.thing
    val y = x.otherThing
    if (it !is Something)
		continue;
    
    outList.add(it)
}
```

```kotlin

// But in this altered model, the idea is that this:
list.map { it.thing }
	.map { it.otherThing }
	.filter { it is Something }
	.map { it.thirdThing }
	.collect(Collectors.toList())

// evaluates to this:
val iter = list.iterator()
val outList = mutableListOf()
while (iter.hasNext()) {
	for (el in iter) {
		val x = el.thing
        val y = el.otherThing
        if (it is Something) {
            val z = y.thirdThing
            outList.add(z)
        }
    }
}


// so what if we kept testing this out?
list.map { it.thing }
    .mapNotNull { it.otherThing } // shorthand for `.map { it.otherThing }.filter { it != null }`
    .filter { it is Something }
    .map { it.thirdThing }
    .flatMap { it.listOfThings }
    .drop(4)
    .collect(Collectors.toList())
// this evalutes to:
val iter = list.iterator()
val outList = mutableListOf()
for (el in iter) {
	val map1 = el.thing
    val map2 = map1.otherThing
    if (map2 != null) {
		val map3 = it.thirdThing
        val flatMap1 = it.listOfThings
        for (el in flatMap1) {
			var numDropped = 0
			for (el in flatMap1) { // note that the outer loop is only ca-FUCK no it isn't only called once, it does need some kind of global state ://///
                if (numDropped < 4) {
                  ++numDropped
                } else {
                  outList.add(el)
                }
            }
        }
    }
}
// looking at it this way, downstream things ARE nested within the upstream ones.
// let's replace the inlined version with some named inline functions:
inline fun <T, U> map(el: T, functor: (T) -> U, downstream: (U) -> Unit) {
	val mapped = functor(el)
    downstream(mapped)
}

inline fun mapNotNull(el: T, functor: (T) -> U?, downstream: (U) -> Unit) {
	val mapped = functor(el)
    if (mapped != null)
		downstream(mapped)
}

inline fun filter(el: T, predicate: (T) -> Boolean, downstream: (T) -> Unit) {
	if (predicate(el))
		downstream(el)
}

inline fun flatten(el: Iterable<T>, downstream: (T) -> Unit) {
	flatMap(el, { it }, downstream)
}

inline fun flatMap(el: T, functor: (T) -> Iterable<U>, downstream: (U) -> Unit) {
    for (item in functor(el)) {
        downstream(item)
    }
}

inline fun drop(el: Iterable<T>, N: Int, getNumAlreadyDropped: () -> Int, setNumAlreadyDropped: (Int) -> Unit, downstream: (T) -> Unit) {
	if (getNumAlreadyDropped() < N) {
		setNumAlreadyDropped(getNumAlreadyDropped() + 1)
        // dropping = refusing to invoke the downstream function
    } else {
		downstream(el)
    }
}

inline fun <T> toList(element: T, getList: () -> List<T>) {
	getCollection().add(element)
}
// probably declared like this:
inline fun <T> InlineStream<T>.toList(): List<T> {
	// declare state that exists outside of the loop
	val outList = mutableListOf<T>()
    
    terminalOperation { el: T, `terminate`: (() -> Unit) -> // note: no "continue" because that's implicit in the "not executing the downstream operation"
		outList.add(el)
    }
    
    return outList; // detects this as a terminal operation because it doesn't return its operation
}

inline fun <T> InlineStream<T>.take(num: Int /* parameters are also stored as external "state", but passed by value i guess */): InlineStream<T> {
	// state outside of loop, we'll have to add getters and setters to the generated inline function for each of these ig
	var numTaken = 0
    
    return operation { el: T, `terminate`: () -> Unit ->
		if (numTaken < num) {
			++numTaken
        } else {
			`terminate`() // break the loop early. Any terminal operations that return a value should have initialized their return value with a default value, so if nothing passes through, that default value is taken
          // question: what do we do about "firstOrFail" if there's no "on loop end" callback?
        }
    }
}

value class InlineStream<T>
private constructor(private val dummy: Nothing?) {
	companion object {
		private val instance = InlineStream<Any>(null)
      
        fun <T> instance() = instance as InlineStream<T>
	}
}

// stub just like contracts
inline fun <T, U> InlineStream<T>.operation((element: T, `downstream`: (U) -> Unit, `break`: () -> Unit)): InlineStream<U> {
	return this as InlineStream<U>
}

// things like "take" and "findFirst" will need the ability to break the loop early
// so we don't still iterate through literally every single element after getting the thing we want

val iter = list.iterator()

var dropped = 0
val collectInto = mutableListOf()
for (el in iter) {
	map(el, { it.thing }) { map1 ->
        mapNotNull(map1, { it.otherThing }) { map2 ->
			filter(map2, { it is Something }) { filtered ->
				flatMap(filtered, { it.listOfThings }) { flattenedEl ->
					drop(4, { dropped }, { dropped = it }) { afterDrop ->
						collect(afterDrop, { collectInto })
                    }
                }
            }
        }
	}
}
```

# Definition:
```kotlin
// Sequence actions are defined like this:
inline fun InlineSequence<T>.drop(N: Int): InlineSequence<T> {
	// global state goes here, will be inserted outside of the loop
	var numDropped = 0
    
    // actual action goes here:
    return sequenceAction { el, downstreamAction ->
		if (numDropped < N) {
            ++numDropped
        } else {
			downstreamAction(el)
        }
    }
}

// We then generate this inline function in FIR:
inline fun <T> drop(/* parameters = */ N: Int, /* state accessors = */ getNumDropped: () -> Int, setNumDropped: (Int) -> Unit, el: T, downstreamAction: (T) -> Unit) {
	// the function from sequenceAction:
	if (getNumDropped() < N) {
		setNumDropped(getNumDropped() + 1)
    } else {
		downstreamAction(el)
    }
}

// And its usage:
list.inlineSequence().drop(4).someTerminatingStep()
// becomes:
var `drop1$numDropped` = 0 // state from original declaration
for (el in list) {
	drop(N = 4, getNumDropped = { `drop1$numDropped` }, setNumDropped = { `drop1$numDropped` = it }) { afterDrop ->
        someTerminatingStep(afterDrop)
    }
}
```

## Terminating Early
Some steps need to stop the loop from iterating over every single element, and should break the loop early.  Things like "any", "findFirst", etc.


```kotlin
// Sequence actions that can break early are defined like so:
inline fun <T> InlineSequence<T>.take(N: Int): InlineSequence<T> {
	// State:
	var numTaken = 0
    
    // Action:
    return terminatingSequenceAction { el: T, `break`: (() -> Unit), downstreamAction: ((T) -> Unit) ->
		if (numTaken < N) {
			++numTaken
            downstreamAction()
            // prevent another potentially-long iteration just to get to this point by terminating the loop before it can iterate again
            if (numTaken == N) {
				`break`()
            }
        }
    }
}

// and generate this function:
inline fun <T> take(N: Int, getNumTaken: () -> Int, setNumTaken: (Int) -> Unit, el: T, `break`: () -> Unit, downstreamAction: (T) -> Unit) {
    if (getNumTaken() < N) {
		setNumTaken(getNumTaken() + 1)
        downstreamAction()
        // prevent another potentially-long iteration just to get to this point by terminating the loop before it can iterate again
        if (numTaken == N) {
          `break`()
        }
    }
}

// with usage:
list.inlineSequence().take(4).someEnding()
// becoming:
run {
    var `take1$numTaken` = 0
    mainLoop@
    for (el in list) {
      take(4, { `take1$numTaken` }, { `take1$numTaken` = it }, el, { break@mainLoop }) {
        someEnding(it)
      }
    }
}
```

## Terminal steps
Terminal steps return a value. We can identify them by the fact that they do not return their `sequenceAction`, and also I guess they don't need a `downstream` parameter either sooooo ok.

### Definition
```kotlin
fun <T> InlineSequence<T>.toList(): List<T> {
	// State:
	val outList = mutableListOf<T>()
    
    terminalSequenceAction { el: T ->
		outList.add(el)
    }
    
    return outList; // by returning this, we know what should be the last thing in the `run` statement generated to wrap the loop
}
```

### Generates:
```kotlin
inline fun <T> toList(getOutList: () -> MutableList<T> /* only getter because `val` */, el: T) {
	getOutList().add(el)
}
```

### Usage:

Before:
```kotlin
val x = list.inlineSequence().toList()
```

After:
```kotlin
val x = run {
	val `toList1$outList` = mutableListOf()
	for (el in list) {
		toList({ `toList1$outList` }, el)
    }
    return@run `toList1$outList`;
}
```