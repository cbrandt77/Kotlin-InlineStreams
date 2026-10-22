# Constant/Runtime Execution Paths w/ Monad

```kotlin
@ConstExpr fun foo(pFunc: KFunction<*>): String {
	val func = ConstantOrDynamic(pFunc)
	return func.map { it.name }
	// returns constexpr name if `func` is a compile-time constant, 
	//      or executes dynamic dispatch if it's not.
	// is this even necessary? what problem does this solve??? doesn't it do the exact same thing regardless?
	
	return pFunc.name // this needs to be evaluated at compile-time if and only if `pFunc` is a constant.
	// but also, pFunc COULD be a dynamic, in which case the caller 
	// should just LINK to THIS function instead of ever eval'ing this.
	// so... what's the deal here?
	// I mean seriously, what's the deal here? why is this bad?
}
```


The central conceit: both constexpr and macros ARE macros, but constexpr functions do compile-time processing.
WRONG.

ConstExpr functions return a VALUE that gets USED in the COMPILATION PROCESS. e.g. for linking, etc.
Macros CAN compute values, but their result is EMBEDDED IN THE OUTPUT.

So really, getting the name of a function would be something like this:
```kotlin
@Macro fun getName(func: KFunction<*>): String {
	return KtStringLiteral(func.name)
}
```
PROBLEM: THIS MIGHT NEED TO BE CALLED AT RUNTIME TOO!!!
YOU DON'T WANT TO LOCK THINGS LIKE "ADDITION" TO ONLY BEING DONE AT COMPILE-TIME!!!

THAT'S WHERE CONSTEXPR FUNCTIONS COME IN: THEY'RE **FUNCTIONS**, SO THEY CAN BE CALLED AT RUNTIME!!!
THEY CAN'T COMPUTE EXPRESSIONS, BUT THEY DO GENUINELY RETRIEVE VALUES. IT'S JUST THAT THEY'RE SAFE TO BE RUN AT COMPILE-TIME TOO!

SO NO, I HAD IT RIGHT ALL ALONG.

- ConstExpr functions: compute a value, retrieve a constant, manipulate string literals (as values).
	- If run at compile-time, result is directly embedded in the bytecode.
	- Needs to return the same result at runtime as at compile-time, it's just that it can ALSO be run at compile-time.
- Macros: compute EXPRESSIONS, build CODE.
	- CANNOT BE USED AT RUNTIME!!!
- Macros are essentially a MUTATION of constexpr functions.
- They behave the same way, can use constexpr functions, but act on EXPRESSIONS instead of VALUES.
	- Are also able to resolve the values of constants if needed.

WAIT, so then my monad idea WOULD actually work for a MACRO!!!  
KEEP FROM HAVING TO DIRECTLY EMIT THE CODE,
INSTEAD JUST WRITE THE CODE THAT SHOULD BE EXECUTED
OR COMPUTE IT IF THE INPUT WAS CONSTANT!!!

```kotlin

@Macro fun usesFunctionName(func: KFunction<*>): String {
	// BROKE:
	if (isConstant(func)) {
		return StringLiteral(func.name)
	} else {
		return GetValueExpression(receiver=expr(func), KFunction::name)
	}
	
	// WOKE:
	return ConstantOrDynamic(func)
		.map {
			// must only call ConstExprs inside these blocks, 
			//  since they're basically saying 
			//   "if func is constant, you can eval this at compile-time, else emit the instructions to eval this at runtime"
			it.name
		}.calcOrEmit()
	// if `func` is dynamic:  `execute { expr(func).let { it.name } })`
	// if `func` is constant: `StringLiteral(func.name)`
}
```
// I think this'll actually mesh REALLY cleanly into the compiler, too,
since WE HAVE THE IR TREE FOR THE CODE THEY WANT TO EMIT INSIDE THAT FUNCTION BLOCK!!!

So this is actually an example of one of those compile-time structs I was thinking about,
where the object itself gets replaced at compile-time.

I wonder if we just use the strategy we used with `InlineStream`?
Make it a `value class <Unit>` and require that they be chained??

Or is there something that can be done with an actual object??? I guess it's a "macro object"???

```kotlin
@Macro value class ConstantOrDynamic<T>(private val expr: KtExpression<out T>) {
	@Macro inline fun <U> map(transform: (T) -> U): ConstantOrDynamic<U> {
		require(callerFunc().isMacro)
		val tfLambda: KtLambda<out U, in T> = expr(transform)
		return if (expr.isConstant && tfLambda.isConstant) {
			tfLambda.constEval(expr.eval())
		} else {
			expr(transform).invokingLambda(args=expr)
		}.let(::ConstantOrDynamic)
	}
	
	@Macro inline fun calcOrEmit(): T {
		if (!expr.isConstant)
			return expr.asType() // I don't know how this will work..............
		else
			return literal(expr.constEval())
	}
}
```

What would we need for `InlineStream<T>` to work here? Let's work with this, then we can focus on other monads...

# InlineStream Strategy
Create a chain of functions from first to last that retroactively inserts the next action's lambda into the previous one's expression

So you have basically:
```kotlin
x.map {}.flatMap{}.forEach{}
// First:
action = { nextAction: KtLambda<out Unit, in X> ->
	callMapFunction(args, nextAction)
}
// Then:
action = { nextAction: KtLambda<out Unit, in M> ->
	action(callFlatMapFunction(args, nextAction))
}
// Then as the terminal:
action(forEachAction())

// Creating:
callMapFunction(args, callFlatMapFunction(args, forEachAction()))
// WHICH IS EXACTLY WHAT WE WANT!!!
```

WHICH MEANS WE DON'T EVEN NEED TO DO THE JANKY "TRANSFORM THE DSL FUNCTION" THING!!!  WE CAN JUST MAKE IT A MACRO BUILDER!!!

```kotlin
@Macro
fun Iterable<T>.inlineStream(): InlineStream<T, T> {
	return InlineStream(MacroUtils.expr(this), { it } /* I == T */, listOf(), listOf())
}

@MacroBuilder
class InlineStream<I, T> private constructor(
	private val sourceIterator: KtExpression<Iterator<I>>,
	/* With constexpr, possibly model typed lambdas as morphisms of actual lambda types?
	 * Use constexpr functions to extract the args of the lambda type for use later on? 
	 * 
	 * actually, is that even necessary? 
	 * embed() is the only one that'll need that, and the generics system is good enough 
	 * to let us convert it into the source lambda type when embedding if give the lambda as a type parameter */
	private val giveNextToPrev: (KtLambda<(T, BreakLoop) -> Unit>) -> KtLambda<(I, BreakLoop) -> Unit>,
	private val setupState: List<KtStatement>,
	private val teardownState: List<KtStatement>
) {
	/* @Macro */ fun <U> bind(call: (callShouldCall: KtLambda<(U, BreakLoop) -> Unit>) -> KtLambda<(I, BreakLoop) -> Unit>, setupState: List<KtStatement> = listOf(), teardownState: List<KtStatement> = listOf()): InlineStream<U> {
		return InlineStream(sourceIterator, { nextAction: KtLambda<out Unit, in U> ->
			giveNextToPrev(call(nextAction))
		}, this.setupState + setupState, this.teardownState + teardownState)
	}
	
	/* @Macro */ fun terminal(terminalAction: (returnStatement: KtLambda<(T) -> Unit>) -> KtLambda<out Unit>): KtStatement {
		// now the question is, how do we make this easy to write?
		return MacroUtils.compile {
			run {
				@MacroUtils.Helper val firstAction: KtLambda<(I, BreakLoop) -> Unit> = constEvaluate {
					// need to evaluate this inside the code block to get a static reference to the @run target
					giveNextToPrev(terminalAction(expr { return@run it }))
				}
				evaluateAll(setupState) // just add the raw statements. any variables in here should already be referenced statically by the operation functions
				for (el in embed(sourceIterator)) {
					firstAction.invokeExpr(el, { break })
				}
				evaluate(teardownState)
			}
		}
	}
}

@Macro // meta? macro? I think we allow @Macro functions to return "macro builders".
inline fun <T> InlineStream<T>.drop(numToDrop: Int): InlineStream<T> {
	val toDropExpr = expr(numToDrop)
	val (setupStatements: List<KtStatement>, symbols: List<KtCallable>) = MacroUtils.compile { /* a macro in itself. returns (statements defined, ...typed declarations in order? I don't know if "changes call signature based on BODY of block given to it" is a thing we could even REMOTELY do...) */
		var leftToDrop = embed(toDropExpr) // forcibly embed it as a constant into the thing. could `compile` be `UtilsObject.()`, and `embed` be `UtilsObject#embed`?
	}
	val (leftToDropSymbol: KtMutablePropertyRef<Int>) = symbols
	
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
				MacroUtils.lineNumber(this@drop) // for the debugger so it stops here when it calls drop
				if (leftToDrop.get() == 0) {
					embed(nextAction)(el)
				} else {
					leftToDrop.set(leftToDrop.get() + 1) // lambdaWithShape needs to map `VarRef#get` and `VarRef#set` to KtGetValueExpr and KtSetValueExpr
				}
			}
		}
	}
	
	return this.bind(actionLambda, setup = setupStatements)
}
```

Macro requirements:
- Knows who invoked it and at what offset (`lineNumber` util)
- Knows if its caller is a macro itself
- Can remap references to variables to be references to other variables
- Can reference the _idea_ of a variable without using its declaration specifically (link to (F)IRSymbol?)

Macro utils requirements:
- Can retrieve the expression for a symbol not directly passed to it, but that is referenced inside a lambda
  - `fun x(i: Int)` -> `embedConst(i)`
  - `@Helper val x: KtLiteral; embed(x)`
- Can convert code to expressions and manipulate them 
  - (might be inherent to the design, since it'll have to compile the macro calls first anyway to execute them)
- Can use a macro to retrieve expressions INSIDE the code it's building and then manipulate them
- `embed`: Embed an expression into the code being generated and allow it to be manipulated in code.
  - For example, `embed<KtLambda<() -> Unit>>(foo).invoke()`
  - Needs to be able to retrieve the value the variable it's given holds.
- Must evaluate all macros given to it first...
- Must be able to const-evaluate functions in the context of the block it's been given.........
  - For example: `MacroUtils.compile { run { @Helper val x = symbol(this@run) } }`
    - Do we extract the raw IR for that and compile and run it on its own???
- `compile` blocks may not reference variables declared outside the block.
  - Exception: Variable references from `using`
  - Exception: Variables holding expressions, referenced by other utils consuming them to return a constant value

Macro builder requirements:
- Instances may not even escape the **expression** in which they were declared. 
  - They must remain in a chain, and may not even be assigned to variables, since they do not exist at runtime. 
  - Exception: May escape into or from `@Macro` functions, including functions of the macro builder.
  - Exception: May be "saved" to `@Helper` vars declared in `compile` blocks
- Allowed to hold expressions
  - We can safely say that no runtime code is going to give it expressions, since it's compiler-only, so we don't need to restrict input there
- No restrictions needed on what types it's allowed to hold
- All methods of the object must be constexpr-correct
- Must be consumed and discarded by a macro



# Alt form of injecting expressions from macro invocations
Alt form of getting expressions from macro invocations, to reduce the amount of reflection needed on the compiler's part?
or does it even matter if we just make them intrinsics?
I think intrinsics work better, cause we get the direct expresssssss no wait we don't get the direct expression...
though I _think_ macros are going to end up being intrinsics _anyway_, just to interface with the whole system?
```kotlin
@Macro inline fun someMacro(iParam: Int, iParamExpr: KtExpression<Int> = MacroUtils.calc())
```
yeah, intrinsics are the best way to handle macros, since you actually get access to the full call's tree and have the opportunity to evaluate it fully
also, I think this will also allow us to evaluate macros inside the code blocks 
before the code block itself gets evaluated by the macro?

WAIT, HOLD UP...
IF MACROS CAN ALTER FUNCTION PARAMETERS... :D
```kotlin
@Macro inline fun someMacro(
	iParam: Int, 
	/* injected by @Macro macro: 
	`expr_iParam: KtExpression<Int> = MacroUtils.calc()` 
	*/
)
```


I don't think macros can alter function parameters, though, so this might not be a good idea.
Plus, having it in the macro's signature now requires the consumer to depend on us tooooooo... wait no they'd HAVE TO to have the macro work anyway, so this isn't a problem
but it does introduce it as part of the interface when it's an implementation detail regardless
So no, we should use `MacroUtils.expr(iParam)`.

Or, do one better: produce an object `CallInfo` that contains expressions for ALL params given to the macro. 
That way we don't have to look up some symbol being passed to it.