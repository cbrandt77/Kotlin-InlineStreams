package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.util.flatMap
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.types.IrType

@JvmInline value class TerminalOperationLoopFunction private constructor(val internal: StreamOperationLoopFunction) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(func: IrSimpleFunction): Result<TerminalOperationLoopFunction> = with (data) {
			return StreamOperationLoopFunction(func).flatMap {
				// TODO verify has only el and breakLoop, no downstream action
				Result.success(TerminalOperationLoopFunction(it))
			}
		}
		
		context(data: StreamOpContext)
		operator fun invoke(func: StreamOperationLoopFunction): Result<TerminalOperationLoopFunction> = with (data) {
			return Result.success(func).flatMap {
				// TODO verify has only el and breakLoop, no downstream action
				Result.success(TerminalOperationLoopFunction(it))
			}
		}
	}
	
	val ir get() = internal.ir
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	
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