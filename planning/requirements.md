This is a plugin for the Kotlin compiler that allows things to be precomputed at compile-time.
It should support macros (generating new source code from an input),
constexpr variables (holding structs containing constant precomputed info),
and constexpr functions (calculating constant info at compile-time).

This plugin looks to essentially centralize the "inline function", 
"interface delegation", "property delegation", "class metadata",
"intrinsic candidate", and most use-cases of compiler plugins 
into a user-friendly, extensible, compile-time macro/constant-computation framework. 

Intrinsic functions can be replaced 

# Constant Values:
- Must be known at compile-time.
- Must be embeddable into bytecode.
- Do not necessarily need to _be_ immutable, but **may not be mutated at compile-time**.
- Subjects:
	- Literal primitives (boxed or otherwise)
	- String literals
	- References to `const val`s
	- References to `public static final` values of any of the above types
	- References to enum class instances
	- KClass literals (`Foo::class`)
	- Java class literals (`Foo::class.java`)
		- (not lookup-based class literals like `Class.forName("pkg.Foo")`)
	- Function references
	- Property references
	- Object results of constructor calls that:
		- do not have side effects
		- do not read global non-constants
		- do not call non-constant expressions/constructors
		- only have constant values as arguments
    - Annotation instances (guaranteed to be pure-ctor serializable data classes)
	- Object results of ConstExpr functions that are `Serializable` (to be embeddable into bytecode without requiring further computation at runtime to satisfy)
	- Arrays of any of the above
	- Unboxed value class instances that resolve to any of the above
		- Includes value-classes wrapping value-classes wrapping one of the above
		- Does not include boxed value class instances
	- Arrays of said value classes

# ConstExpr Functions
- Are evaluated at compile-time.
- Must not have side effects. (i.e. modify global variables)
- May not be evaluated at runtime.
- Arguments:
	- May only accept constant values as arguments.
	- Knows the data type of its arguments.
	- May have default arguments.
		- Default arguments must be constant values.
	- Must accept type arguments.
		- Generics must be resolvable at compile-time. (i.e. not a type parameter of another function/class that isn't itself constant)
		- Knows the class metadata of type parameters. (reified)
- Return values:
	- May only return [constant values](#constant-values).
	- Return value must always be the same when given the same input. (injective transform, pure)
	- Return value must be inlined into the callsite's compilation result.
- External Interactions:
	- May freely call constexpr functions/properties of its argument(s).
	- May call non-constexpr functions/properties of its arguments that do not have side effects and do not modify the input object.
	- May freely call any [constexpr-valid](#constexpr-valid-functions) functions and constructors.
	- May freely access [constexpr fields](#constexpr-fields).
	- May use non-constexpr functions and constructors that have allowed IO side effects.
		- Specifically: `System.out`, etc. access and `PrintStream#println` and its morphes.
		- Global state is undefined behavior in this, since compilation isn't deterministic, but it's impossible to avoid _every_ stdout call.
- Example Uses:
  - Custom String literal manipulation (e.g. stdlib `trimIndent`, `trimMargin`)
  - Custom constant computation (e.g. stdlib primitive plus, minus, bitshifts, toString "methods")
  - Static assertions on types, allowing union-typed parameter validation without needing a wrapper
    - How do we detect this inside a non-constant function? Do we have to scan every single call for if it contains a static-assert, and then walk up to see?? this feels messy...

## ConstExpr-Valid Functions
An arbitrary function is considered "constexpr-valid" if all of these are satisfied:
- Does not originate from the current module. (Anything from the current module must be annotated as `@ConstExpr`, but we can't expect every library in existence to do this.)
- Does not access any global non-constexpr fields, save for allowed IO.
- Does not invoke any non-"constexpr-valid" functions, save for allowed IO.
- Its owning object, if any, is not a global or referenced by a global. (This shouldn't happen, since constexpr and constexpr-valid functions already can't access globals.)

Constexpr-valid functions may be freely invoked by constexpr functions, so they don't have to rewrite the entire stdlib from scratch.

Constructor calls are judged by the same means as regular functions for constexpr-validity,
and their superclass constructor(s) must be checked for the same.

Just because an object may be instantiated with a constexpr-valid constructor call does not mean that all of its methods are.
Each method call must be judged individually for constexpr-correctness.


### Problem: Even constexpr functions could be in classes whose static initializers require runtime information

Possible Solutions:
- For constexpr functions, we can extract them from the surrounding area and etc etc
- What do we do for constexpr-valid functions, though? I guess we could copy them out too before running?

Constexpr functions, by necessity, are pure.
As such, they should be able to be extracted 
from the surrounding file entirely and run on their own.

### Problem: third-party constexpr functions, by necessity, don't have compile-time functions in their runtime binary distributed to consumers

Possible solutions:
- What's in a klib, anyway? Can we use that? I've often wanted Kotlin-specific info from deps for use in IR...
- TODO
- Could always _literally_ move constexpr functions to their own classes.
  - essentially have them in a different module
  - they shouldn't throw a ClassNotFoundException because they'll never be loaded
- Bundle the compile-time versions of constexpr functions and macros into another release binary

# ConstExpr Fields:
- Initialized with a [constant value](#constant-values)
	- Cannot hold object results of constant expressions.
		- Can hold object results of constant expressions that are `Serializable`, since this must be embedded into bytecode.
	- Can be initialized with constexpr-valid constructor calls whose arguments are constant values.
	- Can be initialized with macros returning constexpr-valid expressions.
- Value may not be modified at compile-time.
	- Value members may not be mutated at compile-time, as global state at compile-time is undefined behavior (maybe? or is this actually ok? like collecting a list of functions or something)
- Value must be accessible to constexpr functions and properties.
- Value must be accessible at runtime.
- Value may have constexpr functions and properties.
	- Value's constexpr functions must be accessible at compile-time.
	- Value's constexpr functions must be accessible at runtime.
- Value may have _non_-constant functions and properties.
	- Value's non-constant members must _not_ be accessible at compile-time.
- Example uses:
	- Class metadata
	- Value classes wrapping things that would otherwise be `const val`.
    - 
    - Not intended for simple constructors without complex computation involved.
      - Those can just use the constructor, which will be faster than deserializing.




# Macro functions:
- Returns Kotlin source(?) code.
- Must be able to accept and produce:
	- Kotlin expressions
		- Knows the return type of the expression given.
		- Knows the return type of all expressions composing it.
	- Kotlin statements 
      - (How do we get a function to accept a statement? Maybe an annotation? Are statement annotations visible in IR?)
	- Kotlin class declarations
		- Knows the name of the class.
		- Knows the members of the class.
		- Knows the supertype(s) of the class.
        - Knows the annotations of the class.
		- May not mutate these directly.
		- May emit a transformed version of the class declaration to outright _replace it_, however.
	- Kotlin property declarations
		- Knows the name of the property.
		- Knows the return type of the property.
		- Knows the class metadata of the declaring class (if any) of the property.
		- Knows the body of the initializer, if any.
			- Knows the return type of the initializer.
		- Knows the body of the getter, if any.
		- Knows the body of the setter, if any.
		- Knows the expression delegated to, if any.
			- Knows the return type of the delegate.
			- Knows the class metadata for said type.
		- May not modify the given property directly.
		- Able to emit one or multiple members to replace the property given.
	- Kotlin function declarations
		- Knows the name of the function.
		- Knows the return type of the function.
		- Knows the class metadata of the owner of the property.
        - Knows the parameters of the function.
        - Knows the annotations of the function.
		- Knows the statements comprising the body of the function.
	- Kotlin getter/setter declarations
	- Inline function blocks containing instructions to be executed
		- Said inline functions may not be evaluated within the body of the method.
	- Types
	- [Constant values](#Constant-Values)
	- Names that do not exist yet
		- These must be able to be referenced later in the function once used in as part of a generated expression.
	- Names that do exist
	- Input that does not conform to Kotlin syntax (via string + language injection, maybe?)
    - To support type-safe function composition, must be able to use _variable-arity generics_.
	    - Example: `Macro(::SomeFunction, <immediately displays the argument types for said function>)`
    - Does it need to be able to modify its own signature???
- Must appear to _accept_ actual values.
  - Actual values accepted may not be directly accessed.
  - Actual values accepted may have their _producing expression_ parsed.
- Must appear to _return_ actual values.
  - May not produce actual values.
  - Must return CODE that produces a value of said type.
- Users should be able to write:
	- Unchecked Kotlin source code as a String. (e.g. `class ${name} { ... }`)
	- Type-safe code blocks in Kotlin. (e.g. `return execute { someAction(someConstantToBeInlined) }`)
		- Must only enclose references to properties holding constant values computed inside the macro function.
		- Must have said references inlined to the constant value their properties hold after being computed.
	- Code created from nested "Code Representation" objects.
		- e.g. `return GetValueExpression(This, Name("Foo"))`
		- Potentially, we _only_ work with these? Essentially just construct IR representations and skip actual source-code construction altogether?
	- Declarations:
		- Function declarations
		- Class declarations
			- Object
			- Interface
			- Enum class
			- Value class
			- Annotation class
		- Property declarations
			- Initialized
			- Delegated
			- Getter/setter-based
			- Getter/setter
			- Lateinit
- Return value:
	- Must be valid Kotlin code.
	- Must be compilable into bytecode.
	- May contain other macro invocations.
      - (If we just use a loose IR-like representation, we could just stay in that form and check the code produced by the macro for any new macros that need to be evaluated, and then just give the same tree to that, without needing to undergo any actual "compilation" between macro evaluations.)
- Final result after the tool is done must be directly-compilable Kotlin code that does not contain any macro invocations.
  - Final result may contain constexpr functions.
- Calling:
	- Must be overloadable (polymorphic wrt call signature, same name but different argument types)
	- Must be chainable (example: `foo.someMacro().anotherMacro()`)
	- Must work with a receiver (example: `foo.someMacro()` -> `someMacro(foo)`)
	- ~~Must accept the _results_ of constexpr functions, not the literal constexpr function call (or is there even a difference?)~~
	- Caller must see it as producing a value of a given type.
    - Must be evaluated before constexpr functions.
    - May restrict input to certain expression types. (e.g. `@MacroRequires(receiverExprType=KtFunctionCall::class)`)
      - Something needs to emit FIR errors based on this requirement.
- Interacting with other functions:
	- Same stipulations as [ConstExpr functions](#constexpr-functions).
	- May evaluate constexpr functions on its argument _if said argument is a constant value_.
- Generation:
	- Must be able to parse its input into its subexpressions.
	- Must be able to embed an input code fragment into preexisting source code directly.
      - This _will_ require a parsing step to construct expression objects from the source code.
	- Must be independent of the backing compiler API.
    - Macros applied to declarations must be able to add new declarations to the surrounding scope.
- Examples:
	- SQL code
		- `DROP foo` -> `Drop("foo")`
		- `SELECT x FROM foo WHERE x AND y UNION (SELECT zz FROM bar WHERE ...)"` -> `Union(Select("x", "foo").where(And("x", "y"), Select("zz").from("bar").where("..."))`,
			- (Or whatever is the most efficient at runtime, since you don't need to worry about syntax anymore.)
	- Stdlib inline functions
    - Sequence syntax compiling to a `for` loop
    - New code constructs
    - Inlined property delegates 
    - Compiletime-polymorphic functions (e.g. `inline fun <reified T> foo(input: T) { when (T::class) { X -> ; Y -> ; else -> }` branches directly inlined based on known type of input)
      - I can't figure out if this should be constexpr or macro.
	- Structural polymorphism without interfaces (this feels like it'll end up being macros...)


# Supporting responsibilities
Determine if functions from external dependencies are constexpr-valid
- Scan JVM bytecode for const-correctness

Representation for all kotlin IR expressions and declarations