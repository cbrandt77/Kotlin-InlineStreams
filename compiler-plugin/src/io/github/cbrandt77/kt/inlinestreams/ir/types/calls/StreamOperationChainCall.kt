package io.github.cbrandt77.kt.inlinestreams.ir.types.calls

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.substitute

@JvmInline value class StreamOperationChainCall private constructor(val ir: IrCall) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(call: IrCall): Result<StreamOperationChainCall> = with(data) {
			call.extensionReceiver_.let {
				when (it) {
					null -> return Result.failure(IllegalArgumentException("Does not have an extension receiver."))
					!is IrCall -> return Result.failure(IllegalArgumentException("Extension receiver is not an IrCall."))
					else if !call.symbol.owner.hasInlineStreamReceiver -> return Result.failure(IllegalArgumentException("Extension receiver is not InlineStream."))
				}
			}
			
			return Result.success(StreamOperationChainCall(call))
		}
	}
	
	context(data: StreamOpContext)
	val receiver: IrExpression
		get() = with (data) { ir.extensionReceiver_!! }
	
	/**
	 * Returns the previous step in the chain.
	 *
	 * Will not be the entrance function, and terminal operations cannot chain off of other terminals, so it must be an intermediate.
	 */
	context(_: StreamOpContext)
	val previousOperation: IntermediateOperationChainCall?
		get() = (receiver as? IrCall)?.let { IntermediateOperationChainCall(it).getOrNull() }
	
	context(data: StreamOpContext)
	val inputElementType: IrType
		get() = with (data) {
			return (receiver as? IrCall)?.type?.substitute(ir.symbol.owner.typeParameters, ir.typeArguments.filterNotNull())?.let { getInlineStreamTypeParameterT(it) } ?: error("Stream operation receiver does not have type argument.")
		}
	
	context(_: StreamOpContext)
	val target: StreamOperationChainFunction
		get() = StreamOperationChainFunction(ir.symbol.owner).getOrThrow()
}