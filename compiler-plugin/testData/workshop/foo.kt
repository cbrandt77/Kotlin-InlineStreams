import kotlin.reflect.KFunction

fun foo(func: KFunction<*>) {

}

fun box(): String {
	val f = ::foo
	foo(f)
	return "OK"
}