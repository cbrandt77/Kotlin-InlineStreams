package io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain

import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.isTopLevel
import org.jetbrains.kotlin.ir.util.statements

/**
 * The dsl function, like `x.map{}.flatMap{}.forEach{}`
 */
@JvmInline value class StreamOperationChainFunction(val ir: IrFunction) {
	companion object {
		context(data: StreamOpContext)
	    operator fun invoke(ir: IrFunction): Result<StreamOperationChainFunction> = data.runCatching {
			require(ir.isTopLevel) { "Expected to be top-level declaration" }
			require(ir.hasInlineStreamReceiver) { "Must take an InlineStream as a receiver" }
			
			StreamOperationChainFunction(ir)
	    }
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