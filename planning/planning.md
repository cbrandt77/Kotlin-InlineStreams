Central idea: inline chains of functional transformations


# Problem we're solving:
```kotlin
list.asSequence().map { it.thing }.flatMap { it.members }.toList()

// is actually:
FlatMappingSequence(FilteringSequence(Sequence(list), { it.thing }), { it.members }).toList()

// even though because it can't handle concurrency and doesn't leave the function, it's literally JUST shorthand for:
val outList = mutableListOf()
for (el in list) {
	val it_thing = el.thing
    for (el2 in el.members) {
		outList += el2
    }
}
```

Basically, rather than construct an _actual_ intermediate object for this just to carry a reference to an _actual_ mapping function,
we create a framework for **nesting inline functions by chaining them**.
```kotlin
// looking at it functionally, what this is doing...
list.asSequence().map { it.thing }.flatMap { it.members }.toList()

// ...if we give the functors names "mapper" and "flatMapper", and show the toList as the collector it is...
list.asSequence().map(mapper).flatMap(flatMapper).fold(mutableListOf()) { list, it -> list.add(it) }

// ...is this:
forEachElement(fold { list, it -> list.add(flatMapper(map(listElement))) })(list, outList)
//....... I think...... functional stuff is HARD, dude
```

(don't shame the JS, IDEA doesn't have haskell or lisp highlighting)

```kotlin
fun forEachElement(action: (T) -> Unit): (Iterable<T>) -> Unit {
	return { list: Iterable<T> -> 
		for (el in list) { 
			action(el) 
		} 
	}
}

fun map(functor: (T) -> U): (T) -> U { // I mean this is just the nature of functors, is it not?
	return { el: T -> functor(el) }
}

fun flatMap(functor: (T) -> Iterable<U>, action: (U) -> Unit): (T, (U) -> Unit) -> Unit {
	return { el: T, action: (U) -> Unit -> forEachElement(action)(functor(el)) }
}
```

So a flatmap is a consuming conversion by necessity, though really all of them a-

Wait no, you're thinking too structurally. 
Let's use the lambda calculus: functions can only have one parameter now. What does that mean?

This also means we can reorder the parameters any which-way with the information we have access to at the time,
binding any parameter with whatever information we have.

```javascript
let onEachElement = (action) => (list) => list.forEach(action)

let map = (functor) => (element) => functor(element)

let flatMap = (functor) => (item) => (action) => onEachElement(action)(functor(item))

let collect = (collectionTarget) => (item) => collectionTarget.add(item)

// oh jeez ok this is gonna be tough cause we now have functions with their own state......
let indexed = (list: Iterable<T>) => (action: (T, Int) => Unit) => {
	let i = 0
    onEachElement((el) => {
		action(el, i++)
    })(list)
}


// WAIT THAT'S WHY THEY RETURN UNIT!!!!  BECAUSE THERE ARE NO "VOID" FUNCTIONS IN FUNCTIONAL PROGRAMMING!!!!!

let flatten = (item: Iterable<T>) => (action) => onEachElement(action)(item)

// I forgot we can also bind any parameter we want in any order, we don't have to actually reorder them on the backend:
let flattenWithBoundAction = (action) => (item: Iterable<T>) => flatten(action)(item)


let zip = (firstIterable: Iterable<T>) => (secondIterable: Iterable<U>) => (action: (T, U) => Unit) => {
	while (firstIterable.hasNext() && secondIterable.hasNext()) {
        action(firstIterable.next(), secondIterable.next())
    }
}

// now how do we do some kind of mapping action here? oh wait no we just pass it later on down the line
```

but realistically, all I need to do is just make all of these inline and change out how the arguments work

```kotlin
fun Iterable<T>.inlineStream() {
	for (el in this) {
		action(el) // calls whatever inline function comes after it
	}
}


```

wait, the receiver is literally just a shorthand for "the first parameter"
```kotlin
list.map { it.thing }.flatMap { it.members }.collect(Collectors.toList())

// of course! though implicit here, the forEach is always a "terminal" step in Streams
forEachElement(list) { el -> collect(input = { flatMap(input = { map(input = el, functor = { it.thing }) }, functor = { it.members }) }, collector = Collectors.toList()) }
```

