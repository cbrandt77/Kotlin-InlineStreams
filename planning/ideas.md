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

```kotlin
// CompileTimeOnly classes used in non-constexpr functions may not escape the function in which they were created. 
@CompileTimeOnly class InlineStream<out T> {
	@InlineStreamInternals
	val _source: Generator<KtExpression<out T>>
	
	/**
	 * Contains each mapping function in order
	 */
	@InlineStreamInternals
	val _mappings: List<(el: KtExpression<*>, `break`: KtExpression<*>, nextAction: KtLambda) -> KtExpression<Unit>>
	
	@Macro fun build(): KtExpression<out T> {
		val setup = _source.init() // sets up the for..in loop
	
	}
}

@Macro inline fun <T, U> InlineStream<T>.map(transform: (T) -> U): InlineStream<U> {
	return this.withMapping(transform) // fuckin whatever aaaaaaaaa my brainnnnnnnnnn
}
```


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
@MacroBuilder class InlineStream<I, T>(
	private val sourceIterator: Iterator<I>,
	/* With constexpr, possibly model typed lambdas as morphisms of actual lambda types?
	 * Use constexpr functions to extract the args of the lambda type for use later on? */
	private val giveNextToPrev: (KtLambda<(T, BreakLoop) -> Unit>) -> KtLambda<(I, BreakLoop) -> Unit>,
	private val setupState: List<KtStatement>,
	private val teardownState: List<KtStatement>
) {
	/* @Macro */ fun <U> bind(call: (callShouldCall: KtLambda<(U, BreakLoop) -> Unit>) -> KtLambda<(I, BreakLoop) -> Unit>, setupState: List<KtStatement> = listOf(), teardownState: List<KtStatement> = listOf()): InlineStream<U> {
		return InlineStream(sourceIterator, { nextAction: KtLambda<out Unit, in U> ->
			giveNextToPrev(call(nextAction))
		}, this.setupState + setupState, this.teardownState + teardownState)
	}
	
	/* @Macro */ fun terminal(terminalAction: (returnTarget: KtBlock) -> KtLambda<out Unit>): KtStatement {
		// now the question is, how do we make this easy to write?
		return MacroUtils.compile {
			run {
				@MacroHelper val firstAction: KtLambda<(I, BreakLoop) -> Unit> = constEvaluate {
					// need to evaluate this inside the code block to get a static reference to the @run target
					giveNextToPrev(terminalAction(symbol(this@run)))
				}
				evaluateAll(setupState) // just add the raw statements. any variables in here should already be referenced statically by the operation functions
				for (el in embed(sourceIterator) /*  */) {
					firstAction.invokeExpr(el, { break })
				}
				evaluate(teardownState)
			}
		}
	}
}

@Macro // meta? macro? I think we allow @Macro functions to return "macro builders". this is SUCH A COOL IDEA!!!
inline fun <T> InlineStream<T>.drop(numToDrop: Int): InlineStream<T> {
	val toDropExpr = expr(numToDrop)
	val (setupStatements: List<KtStatement>, leftToDropSymbol: KtMutablePropertyRef<Int>) = MacroUtils.compile { /* a macro in itself. returns (statements defined, *typed declarations in order) */
		var leftToDrop = embed(toDropExpr) // forcibly embed it as a constant into the thing. could `compile` be `UtilsObject.()`, and `embed` be `UtilsObject#embed`?
	}
	
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
					leftToDrop.set(leftToDrop.get() + 1) // I think you can define `++` operators on types? like `operator VarRef<Int>.inc() = this.set(this.get() + 1)`
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
- Can convert code to expressions and manipulate them (might be inherent to the design, since it'll have to compile the macro calls first anyway to execute them)
- Can use a macro to retrieve expressions INSIDE the code it's building and then manipulate them
- `embed`: Embed an expression into the code being generated and allow it to be manipulated in code.
  - For example, `embed<KtLambda<() -> Unit>>(foo).invoke()`
  - Needs to be able to retrieve the value the variable it's given holds.