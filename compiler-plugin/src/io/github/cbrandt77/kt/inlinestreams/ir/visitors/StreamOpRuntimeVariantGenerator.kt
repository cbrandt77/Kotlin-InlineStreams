package io.github.cbrandt77.kt.inlinestreams.ir.visitors

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrInlinedFunctionBlock
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid

/**
 * From the `fun InlineStream<T>.someOperation` function the user writes,
 * generate the `inline fun someOperation(el: T, downstream: () -> Unit): Unit`
 * that actually gets called at runtime.
 */
class StreamOpRuntimeVariantGenerator(val context: IrPluginContext) : IrVisitorVoid() {
	val inlineStreamSymbol by lazy {
		context.referenceClass(NamesAndIds.InlineStreamClassId)
	}
	
	override fun visitElement(element: IrElement) {
		element.acceptChildrenVoid(this)
	}
	
	override fun visitFunction(declaration: IrFunction) {
		if (declaration.actsOnInlineStream)
			visitInlineStreamFunction(declaration)
		
		declaration.acceptChildrenVoid(this)
	}
	
	override fun visitInlinedFunctionBlock(inlinedBlock: IrInlinedFunctionBlock) {
		super.visitInlinedFunctionBlock(inlinedBlock)
	}
	
	fun visitInlineStreamFunction(declaration: IrFunction) {
		val statements = declaration.body?.statements ?: TODO()
	}
	
	private val IrFunction.actsOnInlineStream: Boolean
		get() = dispatchReceiverParameter?.type?.classOrNull == inlineStreamSymbol
}