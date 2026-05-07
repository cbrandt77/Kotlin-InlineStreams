package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isIntermediateOperation
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.util.flatMap
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.statements

@JvmInline value class IntermediateOperationChainFunction(val internal: StreamOperationChainFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(ir: IrFunction): Result<IntermediateOperationChainFunction> = with (data) {
	        return StreamOperationChainFunction.invoke(ir).flatMap {
				if (it.ir.body!!.statements.none { it is IrReturn && it.value.let { it is IrCall && it.symbol.owner.callableId.isIntermediateOperation } }) {
					Result.failure(IllegalArgumentException("Does not return `intermediateOperation` DSL function."))
				} else {
					Result.success(IntermediateOperationChainFunction(it))
				}
			}
	    }
	}
	
	val ir get() = internal.ir
	
	val receiver get() = internal.receiver
	
	context(_: StreamOpContext)
	val inputElementType get() = internal.inputElementType
	
	context(data: StreamOpContext)
	val outputElementType: IrType
		get() = data.getInlineStreamTypeParameterT(ir.returnType) ?: error("doesn't return InlineStream!")
	
	// Intermediate operations must return the intermediateOperation block
	val preamble get() = ir.body!!.statements.asSequence().takeWhile { it !is IrReturn }
	
	val stateVariables get() = preamble.filter { it is IrVariable }
	
	val arguments get() = internal.arguments
	
	context(_: StreamOpContext)
	val identifier get() = internal.identifier
	
	override fun toString() = internal.toString()
}