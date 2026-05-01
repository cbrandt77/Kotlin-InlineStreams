package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.util.filter
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.typeOrFail
import org.jetbrains.kotlin.types.model.getArgument

@JvmInline value class IntermediateOperationLoopFunction private constructor(val internal: StreamOperationLoopFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(func: IrFunction): Result<IntermediateOperationLoopFunction> = with (data) {
	        return StreamOperationLoopFunction(func).filter {
				// TODO verify has downstream action
				Result.success(IntermediateOperationLoopFunction(it))
	        }
	    }
		
		context(data: StreamOpContext)
		operator fun invoke(func: StreamOperationLoopFunction): Result<IntermediateOperationLoopFunction> = with (data) {
			return Result.success(func).filter {
				// TODO verify has downstream action
				Result.success(IntermediateOperationLoopFunction(it))
			}
		}
	}
	
	val ir get() = internal.ir
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	/**
	 * This will be the type accepted by the `downstreamAction` function `Function1<U, Unit>` `U`
	 */
	context(_: StreamOpContext)
	val outputElementType: IrType get() = (parameters.first { it.name == NamesAndIds.IntermediateOperations.NAME_downstreamAction }.type as IrSimpleType).arguments[0].typeOrFail
	
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