# Interface shenanigans (or monad transforms):
```kotlin
class Field<T> {
	context(owner: Database)
    fun getData(): T
	
	context(owner: Database)
    fun setData(value: T)
}

val myField = object : Field<Int> {
	fun getData(owner: Database): Int {
		return owner[SomeKey]
	}
	
	context(owner: Database)
	fun setData(value: Int) {
		owner[SomeKey] = value
	}
}

val db: Database

// Current:
val Database.myField = myField

db.myField.getData(db) // have to provide first arg even when it's already there

// Easier syntax:
interface BoundField<T> {
	val field: Field<T>
	val db: Database
	
    fun getData(): T = field.getData(db)
	// have to manually specify each function, 
    // AND you force any inheritors to implement BoundField as well, 
    // AND you can't use ANY extension functions even when the interface barely even exists in the first place
}

// bindContext also now has to be part of the Field interface too,
// or else any methods inheritors add won't get picked up
val Database.myField 
	get() = myField.bindTo(this) 

db.myField.getData() // but at least it can be called like a normal field?

// IDEAL SITUATION:
inline val Database.myField
    get() = createBoundInterface(myField, this) // not a real interface. just exposes a set of bound functions under a type name that get statically inlined

@Macro fun <reified T, vararg Args> createBoundInterface(receiver: T, boundArgs: Array<Args>, argTypes: Array<Type>): RandomInterface { 
	val newFunctions = T::class.functions.map { func ->
		if (func.parameters.take(boundArgs.size).map { type } == argTypes) {
			return@map func.copy( // copy name, documentation, etc.
				parameters=func.parameters.drop(boundArgs.size),
				body = { args ->
					func.invoke(*boundArgs, *args)
                }
			) // (will probably be replaced with a `.bindArgs(pos, ...args)` method if actually done)
		}
    }
    return newInterface(receiver, newFunctions) // 
}

val f = db.myField
f.getData()

```

...this might be out of scope for constant expressions ;_;

but _god_ i love dicking around with the type system, _there's so much there it's **insane**_



# Auto-generated Copy Method:
Replace the hardcoded `data class` `copy` method generation - which has a number of stipulations - with a more ad-hoc approach.

```kotlin
@Macro fun Copy(classDeclaration: KtClassDeclaration): KtClassDeclaration {
	val primCtor = requireNotNull(classDeclaration.primaryConstructor)
	
	return classDeclaration.copy(functions = classDeclaration.functions + run {
		val args = primCtor.parameters.map { param ->
			param.copy(defaultValue = KtGetValueExpression(receiver=ThisRef, target=param.ctorProperty))
		}
		KtFunctionDeclaration(
			name = "copy",
			parameters = args,
			body = { KtConstructorCall(primCtor, args=args.map { KtGetValueExpression(it) }) }
		)
	})
}

@ApplyMacro(Macro(::Copy)) // where `Macro` is itself a macro that turns the direct function reference into `@MacroReference("our.package.Copy")`, which is a valid annotation member. It also transforms its own call signature into whatever the function reference given to it is, allowing argument validation.
class Foo(val x: String, val y: String) {
    // Adds function:
    fun copy(x: String = this.x, y: String = this.y) = Foo(x, y)
}

// Or for more flexible copy methods:
class Foo(val bar: Bar, val baz: Baz) {
	constructor(x: String, y: String) : this(Bar(x), Baz(y))
	
    @ApplyMacro([
        Macro(::WithParamsOf, // Using variable-arity generics, detects `::WithParamsOf`'s argument types and adds type-validation for the rest of the parameters' types
            /* target   = */ ctorWithSignature<Foo, String, String>(), // Constant expression to disambiguate the reference (and validate its existence), since Kotlin itself doesn't support that.
	        /* insertAt = */ 0,
        )
	    // `@MacroInvocation(@FunctionReference("our.package.WithParamsOf", signature=[KtFunctionDeclaration::class, KtFunctionDeclaration::class]), [@ConstructorReference(Foo::class, [String::class, String::class]), 0])`
	])
    fun copy(): Foo { // becomes `copy(x: String, y: String)`, changing as the signature of the other constructor changes
		return 
	}
}

```

# Automatic Context on All Functions
Use a member as an automatic context parameter

# Class metadata
Throw out all the stdlib-only "@IntrinsicCandidate" stuff, 
with constant structs we can _programmatically_ do stuff about the type.

```kotlin
@ConstExpr
inline fun <reified T : Any> getFunctionNamesReturningString(): Array<out String> {
	return T::class.declaredFunctions // we know this at compile-time
		.filter { it.returnType == String::class } // we know this at compile-time
		.mapToArray { it.name } // we know this at compile-time
}

class MyClass {
	fun foo(): String
	
    fun bar()
}

// We evaluate this at compile-time, and since each element of the array is a constant value, 
// we can easily place it into bytecode as an array of those constant values.
val x = getFunctionNamesReturningString<MyClass>()
// val x = ["foo"];
```


```kotlin
@ConstExpr val KFunction<*>.jvmDescriptor // the only constval KFunctions are function references, which we definitely have metadata for
    get() = buildString {
		append('(')
	    this@jvmDescriptor.parameters.forEach {
		    append(JVMStuff.jvmDescriptorOf(it.type))
	    }
        append(')')
        append(JvmStuff.jvmDescriptorOf(this@jvmDescriptor.returnType))
    }

val desc = MyClass::foo.jvmDescriptor
// val desc = "()Ljava/lang/String;"


@Macro fun asmInvokeInsn(target: KFunction<*>): MethodInsnNode { // says it returns the type that will be inlined
	val func = JvmStuff.jvmMetadataOf(target) // returns a constant object containing metadata about the target method
	require (!func.isConstructor) {
		"Constructors require three insns"
	}
    // opcode: a constant value
    val opcode = when {
		func.isStatic -> Opcodes.INVOKESTATIC // integer "const val"
		func.owner.isInterface -> Opcodes.INVOKEINTERFACE
        else -> Opcodes.INVOKEVIRTUAL
	}
    val methodName = target.name // KFunction#name: a ConstExpr prop 
    val methodDesc = func.descriptorString // JvmMetadata#descriptorString: a ConstExpr prop
    val isInterface = opcode == Opcodes.INVOKEINTERFACE // 
    
//    return MethodInsnNode(opcode, methodName, methodDesc, isInterface) // If this were a constexpr function, would attempt to precompute and store it as a constant value
	
    // Return from macro: easy way to execute standard kotlin code type-safely
    return MacroUtils.execute {
		// all items enclosed by the lambda are constant values, therefore they can be computed and inlined at the callsite
		MethodInsnNode(opcode, methodName, methodDesc, isInterface)
    }
}

val fooInsn = asmInvokeInsn(MyClass::foo)
// val fooInsn = run { MethodInsnNode(Opcodes.INVOKEINTERFACE, "foo", "()Ljava/lang/String;", true) }
```



# Inline streams
```kotlin
x.inlineStream()
	.map { it.y }
	.flatMap { it.l }
	.forEach { println(it) }
// convert to:
for (el in x) {
	map(el, functor={ it.y }, breakLoop={ break },  next={ mappedEl ->
		flatMap(mappedEl, flattener={ it.l }, breakLoop={ break },  next={ listItem ->
			terminal(listItem, action={ println(it) })
		})
	})
}

@ApplyMacro("InlineStream") // should, I guess, turn this into a stub and create a function `map` that has everything? 
// "InlineStream" macro should also generate this hard-link to the generated function `map`
@InlineStreamMetadata(
	generatedFunction=FunctionReference(
		"this.package.map", 
		typeParameters=[TypeParam("T"), TypeParam("U")],
		argTypes=[
			TypeRef(typeParam="T"), // element: T
			TypeRef(lambdaType=LambdaType([TypeRef(Unit::class)])), // break: () -> Unit
			TypeRef(lambdaType=LambdaType([TypeRef(Unit::class), TypeRef(typeParam="U")])), // nextAction: (U) -> Unit
			TypeRef(lambdaType=LambdaType([TypeRef(typeParam="U"), TypeRef(typeParam="T")])), // functor: (T) -> U
		]
	),
	// arg 0 of this function maps to arg 3 of the generated `map(el, etc.)` function
	argMapsToParams=ArgMapping(["functor"], ["functor"]),
	/*stateVarsMapToParams=ParamMapping(
		["foo"],
		
	)*/
)
inline fun <T, U> InlineStream<T>.map(functor: (T) -> U): InlineStream<U> {
	// generated: `error("Inline stream called without a terminal operation!")`
	return intermediateOperation<T, U> { el, _, nextAction ->
		nextAction(functor(el))
	}
}
// Intermediate operations aren't even macros, they're just regular functions that are unrunnable.

// "InlineStream" macro generates this function:
inline fun <T, U> map(element: T, `break`: () -> Unit, nextAction: (U) -> Unit, functor: (T) -> U) {
	nextAction(functor(el))
}


// resolve macros from outermost to innermost to allow macros to parse the bodies of other macros. 
// problem: this will allow macros to emit other macros, which requires a billion compilation passes. 
//  ALTHOUGH, we could actually keep it all in expression form, and manually check the returned expression 
//  for any macros and invoke them with the current tree, since we have the entire tree and we're just 
//  working with source code at that point
@MacroRequires(receiverType=KtFunctionCall::class, reason="Inline stream functions may only be chained.") // static analysis to give error if called with the wrong expression. // also should be repeatable
@Macro inline fun <T> InlineStream<T>.forEach(action: (T) -> Unit) {
	val prevCall: KtFunctionCall<*> = requireNotNull(reveal(this) as? KtFunctionCall<InlineStream<T>>) {
		"Inline stream functions may only be chained, not set to a variable."
	} // in this case, it would be `x.inlineStream().map { it.y }.flatMap { it.l }
	val forEachBody = reveal(action) // KtLambda
	val prevFunction = prevCall.callee
	val prevFunctionArgs = prevCall.arguments
	val prevFunMetadata = prevFunction.getAnnotation(InlineStreamMetadata::class).single() // should allow accessing repeatable annotations too
	val prevFunBody = prevFunction.body
	// do the rest of the parsing like we do in IR in the current plugin
}


// intermediateOperation is a stub as well
inline fun <T, U> InlineStream<T>.intermediateOperation(action: (element: T, `break`: () -> Unit, then: (U) -> Unit) -> Unit): InlineStream<U> { error("Run without terminal operation!") }
```



Issue: We can't declare macros that need to be compiled in the same project as things that require it.
FIR won't validate functions that use references that don't exist yet, so it'll constantly fail.

Solution: Do what Rust did: compile programmatic macros in another module.
They can still use the same dependencies as the normal project, but they're compiled in an entirely separate process.


# Inline Builders
```kotlin
inline fun buildObject(configure: MyObject.Builder.() -> Unit): MyObject {
	return MyObject.Builder().apply(configure).build()
}

val idiom1 = buildObject {
	val x = someComputation()
	field1 = x
	field2 = run { etc() }
	field3 = z
}
val idiom2 = MyObject.builder()
	.field1(someComputation())
	.field2(run { etc() })
	.field3(z)
	.build()
/*
Problems:
- This needs a mutable builder class at runtime
- Mutable builder class has to be kept in-sync with the source class
- Said mutable builder class literally only exists because there are too many constructor arguments. 
  It's a sourcecode-only style, not actually used for anything but syntax, with a real runtime cost.
 */
/*
Solution - a macro that:
1. Creates a fake "builder" interface at compile-time, automatically in sync with the source class
2. Converts the builder expression to a constructor call
 */
val idiom1 = run {
	val x = someComputation()
	MyObject(field1=x, field2=run { etc() }, field3=z) // moves initialization expressions into constructor call
}
val idiom2 = MyObject(field1=someComputation(), field2=run { etc() }, field3=z)
```

# Inline DSLs
Look at this HTML example from the official docs:
```kotlin
html {
    head {
        title { +"HTML encoding with Kotlin" }
    }
    body {
        h1 { +"HTML encoding with Kotlin" }
        p {
            +"this format can be used as an"
            +"alternative markup to HTML"
        }

        // An element with attributes and text content
        a(href = "http://kotlinlang.org") { +"Kotlin" }

        // Mixed content
        p {
            +"This is some"
            b { +"mixed" }
            +"text. For more see the"
            a(href = "http://kotlinlang.org") {
                +"Kotlin"
            }
            +"project"
        }
        p {
            +"some text"
            ul {
                for (i in 1..5)
                    li { +"${i}*2 = ${i*2}" }
            }
        }
    }
}
```
Output:
```html
<html>
  <head>
    <title>
      HTML encoding with Kotlin
    </title>
  </head>
  <body>
    <h1>
      HTML encoding with Kotlin
    </h1>
    <p>
      this format can be used as an
      alternative markup to HTML
    </p>
    <a href="http://kotlinlang.org">
      Kotlin
    </a>
    <p>
      This is some
      <b>
        mixed
      </b>
      text. For more see the
      <a href="http://kotlinlang.org">
        Kotlin
      </a>
      project
    </p>
    <p>
      some text
      <ul>
        <li>
          1*2 = 2
        </li>
        <li>
          2*2 = 4
        </li>
        <li>
          3*2 = 6
        </li>
        <li>
          4*2 = 8
        </li>
        <li>
          5*2 = 10
        </li>
      </ul>
    </p>
  </body>
</html>
```

We have SO MANY OBJECTS HERE at runtime just for compile-time syntax!
The syntax is _incredibly_ important, but we can calculate a TON of this at compile-time with constant expressions and macros.

Note that many of these things will NOT actually be compile-time only.
However, we can simplify the stuff that IS compile-time evaluatable into our final object, leaving the stuff that needs to be evaluated at runtime as functions to be evaluated.


...now the important question is, _how_ do we do this? 
ConstExprs modifying objects made sense in my head, 
but doesn't make sense in context, since those objects 
won't actually EXIST at runtime.

Maybe constant expressions ALSO get access to the expression
creating their value? How would THAT work, though...

So essentially, this:
```kotlin
val htmlString = html {
	body {
		p {
			+"this format can be used as an"
			+"alternative markup to $SOME_LANGUAGE"
		}
		
		// An element with attributes and text content
		a(href = runtimeLink()) { +"Kotlin" }
	}
}
```
needs to turn into this:
```kotlin
val bodyString = """
<html>
	<body>
		<p>
			this format can be used as an
			alternative markup to $SOME_LANGUAGE
		</p>
		<a href=${runtimeLink()}>Kotlin</a>
	</body>
</html>
"""
```
so this is a macro then? the root node is a macro?

or _is there something that can be done with constants????_

_there's **something there**, it's on the tip of my tongue, right at the edge of my brain_

```kotlin
@CompileTimeOnly class Intermediate {
	val constantList = StringLiteralBuilder() // contains both constant values and things that need to be computed at runtime (function blocks, can't be evaluated at compile-time)
	
	fun toConstantValue(): String {
		return constantList.toStringLiteral()
	}
}

@CompileTimeOnly class StringLiteralBuilder {
	val items: MutableList<Either<String, KtExpression<out String>>>
	
	fun append(constval: ConstantValue<String>)
	
	fun append(runtimeEval: KtExpression<out String>)
	
	@ConstExpr fun toLiteralString(): KtStringLiteral {
		// literally BUILD the string template here
		return KtString(buildString {
			for (el in items) {
				append(it.map(left={ it }, right={ "\${$it}" }))
			}
		})
	}
}

@CompileTimeOnly data class ConstantAndDynamicList<T> {
	val constants: MutableList<Either<ConstantValue<T>, KtExpression<out T>>>
	
	
}
```


WAIT OK!!! OK!!!!!!!
CONSTEXPR FUNCTIONS CAN ACCEPT BOTH CONSTANT VALUES AND DYNAMIC ONES

```kotlin
@ConstExpr fun acceptsString(s: String) {
	val asConstant: StringLiteral = revealConstant(s)
	val asDynamic: KtExpresion<out String> = revealDynamic(s)
}
```

THEN THEY CAN GIVE THOSE EXPRESSIONS TO A MACRO???????? HUH??????? NO, that doesn't work, beacuse the macro isn't the one calling it.

I guess we need compile-time-only objects that are allowed to work with expressions?

```kotlin
@CompileTimeOnly class BodyNode {
	// must only be visible to CompileTimeOnly classes
	private val ownItems = mutableListOf<Either<ConstantValue<String>, KtExpression<String>>>()
	
	@ConstExpr fun append(s: String) {
		ownItems += constantOrDynamic(s) 
	}
	
	fun h1(configure: H1Node.() -> Unit) {
		+H1Node().apply(configure)
	}
	
	fun build(): ConstantDynamic<String> { // where ConstantDynamic is a big bundle of both literal Strings and expressions that return strings. 
		val bldr = ConstantDynamicBuilder()
		bldr.addAll(ownItems)
		return nodes.fold(bldr) { add(it.build()) }
	}
}
```
or maybe they're ALL expressions that produce strings, 
it's just that some are KtStringLiteral and some are 
arbitrary expressions?

```kotlin
class StringTemplateBuilder {
	private val items: MutableList<KtExpression<out String>>
	
	fun build(): KtExpression<out String> {
		val bldr
		
		for (el in items) {
			if (el is KtStringLiteral) {
				bldr.append(el)
			} else {
				bldr.append("\${$el}")
			}
		}
	}
}
```

this feels so much cleaner actually. 
Instead of working with the "idea" of constant values,
just work with expressions as input, and allow constant 
ones to be resolved at compile-time.

```kotlin
// @ConstantOnly: raises FIR error if arg needs runtime information to evaluate
@ConstExpr fun myCompileTimeOnlyFunction(@ConstantOnly s: String)

// This one CAN be evaluated at compile-time, but may also be done at runtime, too.
@ConstExpr fun Int.add(i: Int): Int {
	// would need to calculate this if both `this` and `i` are constant,
	// or link to the function if either are dynamic...
	
	// this makes sense in the context of constexpr, 
	// but then if dynamic values just link to the function,
	// we lose the ability to manipulate expressions and inline builders...
	return this + i
}

@ConstExpr fun addForumSignature(arg: String): String {
	@ConstExpr if (isRuntime) {
		return arg + "  ~gamer"
	} else {
		// we can't let these lines exist at runtime, 
		//  since expression classes ONLY exist at compile-time,
		//  so `const if` is a necessity.
		// allow `const if` only inside constexpr and inline functions,
		//  since macros just use normal `if` statements
		// wait but literally the only different case a constexpr fun could evaluate
		//  is if the function is being called at compiletime or runtime
		val dynArg: KtExpression<out String> = asExpression(arg)
		return if (dynArg.isConstant) { // ?
			dynArg.staticConcat("  ~gamer") // probably not necessary to split these
		} else {
			dynArg.dynConcat("  ~gamer")
		}.asType()
	}
}
```