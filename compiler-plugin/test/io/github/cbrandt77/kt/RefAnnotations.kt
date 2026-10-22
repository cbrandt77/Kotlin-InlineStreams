package io.github.cbrandt77.kt

import kotlin.reflect.KClass
import kotlin.reflect.KFunction

object NoneType

annotation class TypeParam(
	val name: String,
	val upperBound: TypeRef = TypeRef(),
	val isNullable: Boolean = true
)

annotation class ComplexType(
	val classType: KClass<*>,
	val typeArguments: Array<TypeRef> = []
)

/**
 * Union of these things
 */
annotation class TypeRef(
	val classType: KClass<*> = NoneType::class,
	val typeParam: String = "",
	val complexType: ComplexType = ComplexType(NoneType::class),
	val lambdaType: LambdaType = LambdaType()
)

annotation class LambdaType(
	val rTypeThenParamTypes: Array<TypeRef> = []
)

annotation class FunctionReference(
	val qualifiedName: String,
	val typeParameters: Array<TypeParam> = [],
	val returnType: TypeRef = TypeRef(Unit::class),
	val receiver: TypeRef = TypeRef(NoneType::class),
	val valueParams: Array<TypeRef> = [],
	val contextParams: Array<TypeRef> = []
)

// describing `map` below:
@FunctionReference(
	qualifiedName="io.github.cbrandt77.kt.map",
	typeParameters=[TypeParam("T"), TypeParam("U")],
	returnType=TypeRef(complexType=ComplexType(Sequence::class, [TypeRef(typeParam="U")])),
	valueParams = [
		TypeRef(complexType=ComplexType(KFunction::class, []))
	]
)
inline fun <T, U> Sequence<T>.map(functor: (T) -> U): Sequence<U> { // Macro functions can accept real args, but they can't access their members. They can only use them as arguments to other macros, since really it's working with their expressions
	TODO()
}