package io.github.cbrandt77.kt.inlinestreams.ir.transformers

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid

class IrStreamInliner(val context: IrPluginContext) : IrElementTransformerVoid() {
	override fun visitCall(expression: IrCall): IrExpression {
		if (expression.symbol.owner.name.asString() == "intermediateOperation") {
			println()
		}
		return super.visitCall(expression)
	}
}