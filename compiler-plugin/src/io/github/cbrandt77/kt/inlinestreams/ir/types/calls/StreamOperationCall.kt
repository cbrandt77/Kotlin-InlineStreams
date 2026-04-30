package io.github.cbrandt77.kt.inlinestreams.ir.types.calls

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.substitute

@JvmInline value class StreamOperationCall private constructor(val ir: IrCall) {
	companion object {
		context(data: StreamOpContext)
		operator fun invoke(call: IrCall): Result<StreamOperationCall> = with(data) {
			call.extensionReceiver_.let {
				when (it) {
					null -> return Result.failure(IllegalArgumentException("Does not have an extension receiver."))
					!is IrCall -> return Result.failure(IllegalArgumentException("Extension receiver is not an IrCall."))
					else if !call.symbol.owner.actsOnInlineStream -> return Result.failure(IllegalArgumentException("Extension receiver is not InlineStream."))
				}
			}
			
			return Result.success(StreamOperationCall(call))
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
	val previousOperation: IntermediateOperationCall?
		get() = (receiver as? IrCall)?.let { IntermediateOperationCall(it).getOrNull() }
	
	context(data: StreamOpContext)
	val inputElementType: IrType
		get() = with (data) {
			return (receiver as? IrCall)?.type?.substitute(ir.symbol.owner.typeParameters, ir.typeArguments.filterNotNull())?.let { getInlineStreamTypeParameterT(it) } ?: error("Stream operation receiver does not have type argument.")
		}
}