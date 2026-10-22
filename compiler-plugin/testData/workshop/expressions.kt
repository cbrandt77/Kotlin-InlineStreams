// I want to see what the IR looks like for this
interface KtStatement
interface KtExpression<out T> : KtStatement
class KtLambda<T : Function<*>> : KtExpression<T>

interface KtCallable
class KtMutablePropertyRef<T> : KtCallable

class VarRef<T> {
	fun get(): T { TODO() }
	fun set(value: T): Unit { TODO() }
}

typealias BreakLoop = () -> Unit

fun <T> stub() {
	TODO()
}

object MacroUtils {
	@JvmName("exprAny")
	inline fun <T : Any> expr(source: T): KtExpression<T> { TODO() }
	
	@JvmName("exprLamba")
	inline fun <LAMBDA : Function<*>> expr(source: LAMBDA): KtLambda<LAMBDA> { TODO() }
	
	inline fun <E, R> using(expr: KtExpression<E>, usage: (E) -> R): R { TODO() }
	
	inline fun <T : Function<*>> lambdaWithShape(lambda: T): KtLambda<T> { TODO() }
	
	inline fun <E> embed(expr: KtExpression<E>): E { TODO() }
	
	inline fun lineNumber(expr: Any): Unit { TODO() }
	
	inline fun <T, R> using(expr: KtMutablePropertyRef<T>, usage: (VarRef<T>) -> R): R { TODO() }
	
	inline fun <T> compile(block: () -> T): Pair<List<KtStatement>, List<KtCallable>> { TODO() }
}


fun <T> test(numToDrop: Int) {
	val toDropExpr = MacroUtils.expr(numToDrop)
	val (setupStatements: List<KtStatement>, symbols: List<KtCallable>) = MacroUtils.compile { /* a macro in itself. returns (statements defined, ...typed declarations in order? I don't know if "changes call signature based on BODY of block given to it" is a thing we could even REMOTELY do...) */
		var leftToDrop = MacroUtils.embed(toDropExpr) // forcibly embed it as a constant into the thing. could `compile` be `UtilsObject.()`, and `embed` be `UtilsObject#embed`?
	}
	val leftToDropSymbol: KtMutablePropertyRef<Int> = symbols[0] as KtMutablePropertyRef<Int>
	
	// `using`: macro to let you reference the target of a symbol inside a code block.
	//          remaps references to its variable to the original symbol.
	// also, by doing it this way, we can properly reference the source variable directly!!!
	//       we don't need any custom parameters at all, including those state getters/setters!
	//       we don't even need to generate any inline functions!!! this is so much cleaner!!!!!
	val actionLambda = { nextAction: KtLambda<(T, BreakLoop) -> Unit> ->
		MacroUtils.using(leftToDropSymbol) { leftToDrop: VarRef<Int> ->
			MacroUtils.lambdaWithShape<(T, () -> Unit) -> Unit> { el: T, `break`: () -> Unit ->
				// lambdaWithShape: Macro that instantiates a KtLambda accepting the given parameters. YOU CAN DO THIS TYPING IN VANILLA!!!!!
				//      (note: needs to resolve any macros inside it first before compiling, probably, since all of these helpers are going to be macros as well)
				/*MacroUtils.lineNumber()*/ // for the debugger so it stops here when it calls drop
				if (leftToDrop.get() == 0) {
					MacroUtils.embed(nextAction)(el, `break`)
				} else {
					leftToDrop.set(leftToDrop.get() + 1) // lambdaWithShape needs to map `VarRef#get` and `VarRef#set` to KtGetValueExpr and KtSetValueExpr
				}
			}
		}
	}
	
}

fun box(): String {
	return "OK"
}