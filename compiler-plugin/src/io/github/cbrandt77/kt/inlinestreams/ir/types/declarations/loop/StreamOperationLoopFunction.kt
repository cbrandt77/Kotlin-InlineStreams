package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.annotations.InlineStreamFunctionReferenceAnnotation
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.MalformedInlineStreamGeneratedFunctionException
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.render

/**
 * The inline function actually executed inside the loop, e.g. `map(el) { flatMap(el) { forEach(el) } }
 */
@JvmInline value class StreamOperationLoopFunction private constructor(val ir: IrSimpleFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(func: IrSimpleFunction): Result<StreamOperationLoopFunction> = with (data) {
			return when {
				!func.isLoopOperation -> Result.failure(IllegalArgumentException("Item is not a loop operation function."))
				!func.isInline -> Result.failure(MalformedInlineStreamGeneratedFunctionException("Loop operation function must be inline."))
				!func.isTopLevel -> Result.failure(MalformedInlineStreamGeneratedFunctionException("Loop operation function must be top level.")) // TODO remove this when we allow nested inlinestream functions
				func.parameters.any { it.kind == IrParameterKind.ExtensionReceiver } -> Result.failure(MalformedInlineStreamGeneratedFunctionException("Loop operation function must not have receiver."))
				else -> Result.success(StreamOperationLoopFunction(func))
			}
		}
	}
	
	/**
	 * This will be the type of the element parameter
	 */
	val inputElementType: IrType
		get() = parameters.first { it.name == NamesAndIds.IntermediateOperations.NAME_element }.type
	
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
	
	val stateGSArgs get() = parameters.dropWhile { it.name.isSpecial }.dropWhile { !it.name.isSpecial }
	
	context(_: StreamOpContext)
	val identifier
		get() = (InlineStreamFunctionReferenceAnnotation(ir) ?: error("No function identifier on generated loop function!")).identifier
	
	override fun toString() = ir.render()
}