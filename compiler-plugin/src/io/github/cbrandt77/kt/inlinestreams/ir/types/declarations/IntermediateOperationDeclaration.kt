package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.util.filter
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.statements
import kotlin.sequences.filter

/**
 * The dsl function, like `x.map{}.flatMap{}.forEach{}`
 */
@JvmInline value class StreamOperationChainFunction(val ir: IrFunction) {
	init {
		require(ir.isTopLevel) { "Expected to be top-level declaration" }
	}
	
	val receiver get() = ir.parameters.first { it.kind == IrParameterKind.ExtensionReceiver }
	
	context(data: StreamOpContext)
	val inputElementType: IrType
		get() = data.getInlineStreamTypeParameterT(receiver.type) ?: error("doesn't act on inline stream!")
	
	context(data: StreamOpContext)
	val preamble: Sequence<IrStatement> // TODO test this!!!
		get() = with (data) {
			ir.body?.statements?.asSequence()
				.orEmpty()
				.takeWhile { it !is IrReturnImpl && (it as? IrCall)?.callsInlineStreamFunction != true }
		}
	
	val arguments
		get() = ir.parameters.asSequence().filter { it.kind != IrParameterKind.ExtensionReceiver }
}

@JvmInline value class IntermediateOperationChainFunction(val internal: StreamOperationChainFunction) {
	val ir get() = internal.ir
	
	val receiver get() = internal.receiver
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	context(data: StreamOpContext)
	val outputElementType: IrType
		get() = data.getInlineStreamTypeParameterT(ir.returnType) ?: error("doesn't return InlineStream!")
	
	context(data: StreamOpContext)
	val preamble get() = internal.preamble
	
	val arguments get() = internal.arguments
	
}



class IrMalformedStreamOperationLoopFunction(message: String) : RuntimeException(message)

/**
 * The inline function actually executed inside the loop, e.g. `map(el) { flatMap(el) { forEach(el) } }
 */
@JvmInline value class StreamOperationLoopFunction private constructor(val ir: IrFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(func: IrFunction): Result<StreamOperationLoopFunction> = with (data) {
			when {
				!func.isLoopOperation -> return Result.failure(IllegalArgumentException("Item is not a loop operation function."))
				!func.isInline -> return Result.failure(IrMalformedStreamOperationLoopFunction("Loop operation function must be inline."))
				!func.isTopLevel -> return Result.failure(IrMalformedStreamOperationLoopFunction("Loop operation function must be top level."))
				else -> return Result.success(StreamOperationLoopFunction(func))
			}
		}
	}
	
	context(data: StreamOpContext)
	val inputElementType: IrType
		get() = TODO()
	
	val parameters: Sequence<IrValueParameter>
		get() = ir.parameters.asSequence().filter { it.kind != IrParameterKind.ExtensionReceiver }
	
	/**
	 * arguments passed to the original dsl function, e.g. `map({ it -> it.foo })`
	 *
	 * These will always be the first parameters in the sequence.
	 */
	context(data: StreamOpContext)
	val dslFunArgs
		get() = with (data) { parameters.takeWhile { it.isDslFunParameter } }
	
	/** `el`, `breakLoop`, `downstreamAction`. These are actually the only parameters that don't have special names. */
	val standardArgs: Sequence<IrValueParameter>
		get() = parameters.dropWhile { it.name.isSpecial }.takeWhile { !it.name.isSpecial }
	
	context(data: StreamOpContext)
	val gettersByStateName
		get() = with (data) { parameters.mapNotNull { item -> item.name.nameThisIsGetterFor?.let { it to item } } }
	
	context(data: StreamOpContext)
	val settersByStateName
		get() = with (data) { parameters.mapNotNull { item -> item.name.nameThisIsSetterFor?.let { it to item } } }
}

class IntermediateOperationLoopFunction private constructor(val internal: StreamOperationLoopFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(func: IrFunction): Result<IntermediateOperationLoopFunction> = with (data) {
	        return StreamOperationLoopFunction(func).filter {
				Result.success(IntermediateOperationLoopFunction(it))
	        }
	    }
	}
	
	val ir get() = internal.ir
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	context(_: StreamOpContext)
	val outputElementType: IrType
		get() = TODO()
	
	val parameters get() = internal.parameters
	
	
	/**
	 * arguments passed to the original dsl function, e.g. `map({ it -> it.foo })`
	 *
	 * These will always be the first parameters in the sequence.
	 */
	context(_: StreamOpContext)
	val dslFunArgs get() = internal.dslFunArgs
	
	/** `el`, `breakLoop`, `downstreamAction`. These are actually the only parameters that don't have special names. */
	val standardArgs get() = internal.standardArgs
	
	context(_: StreamOpContext)
	val gettersByStateName get() = internal.gettersByStateName
	
	context(data: StreamOpContext)
	val settersByStateName get() = internal.settersByStateName
}