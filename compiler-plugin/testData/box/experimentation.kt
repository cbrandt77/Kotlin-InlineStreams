
class InlineSequence<T>
private constructor() {
	companion object {
		@PublishedApi
		@JvmField internal val inst = InlineSequence<Any>()
		
		@Suppress("UNCHECKED_CAST")
		@JvmStatic inline fun <T> intermediateOperation(action: (downstream: (T) -> Unit) -> Unit): InlineSequence<T> = inst as InlineSequence<T>
		
		@JvmStatic inline fun <T> terminalOperation(action: () -> Unit): T = error("not transformed")
	}
	
	
	operator fun invoke(): T { TODO() }
}

inline fun <T, U> InlineSequence<T>.map(functor: (T) -> U): InlineSequence<U> {
	return InlineSequence.intermediateOperation { downstream ->
		downstream(functor(this()))
	}
}

fun box(): String {
	return "OK"
}