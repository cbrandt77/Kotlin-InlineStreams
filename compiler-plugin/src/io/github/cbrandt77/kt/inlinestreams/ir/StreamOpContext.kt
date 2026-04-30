package io.github.cbrandt77.kt.inlinestreams.ir

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.StreamOperationCall
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

class StreamOpContext(val context: IrPluginContext) {
	val inlineStreamClassSymbol by lazy {
		context.referenceClass(NamesAndIds.InlineStreamClassId)
	}
	
	val kotlinRunFunction by lazy {
		context.referenceFunctions(CallableId(StandardClassIds.BASE_KOTLIN_PACKAGE, Name.identifier("run"))).first()
	}
	
	val anonymous by lazy {
		Name.special("<anonymous>")
	}
	
	val IrFunction.actsOnInlineStream: Boolean
		get() = parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type?.classOrNull == inlineStreamClassSymbol
	
	val IrCall.isInlineStreamFunction: Boolean
		get() = this.symbol.owner.actsOnInlineStream
	
	val IrCall.isInlineStreamEntranceFunction: Boolean // TODO allow custom entrance functions
		get() = this.symbol.owner.callableId == NamesAndIds.entranceFunction
	
	
	fun getInlineStreamTypeParameterT(type: IrType): IrType? {
		return (type as? IrSimpleType)?.takeIf {
			it.classOrNull == inlineStreamClassSymbol
		}?.arguments?.getOrNull(0)?.typeOrNull
	}
	
	val IrCall.extensionReceiver_
		get() = symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.let { arguments[it] }
	
	fun getLoopOpFunction(chainFunc: StreamOperationCall): IrSimpleFunctionSymbol {
		return context.referenceFunctions(getLoopOpFunctionName(chainFunc.ir.symbol)).disambiguate(chainFunc) // TODO disambiguate
	}
	
	/**
	 * How do we disambiguate:
	 * - Check the function's own parameters
	 * - Check the input type
	 * - Check the output type
	 */
	private fun Iterable<IrSimpleFunctionSymbol>.disambiguate(chainFunc: StreamOperationCall): IrSimpleFunctionSymbol {
		TODO()
	}
	
	fun getLoopOpFunctionName(chainFunc: IrSimpleFunctionSymbol): CallableId {
		return chainFunc.owner.callableId.let {
			it.copy(Name.identifier(it.callableName.asStringStripSpecialMarkers() + "_loopAction"))
		}
	}
	
	/**
	 * Make the version of a dslFunction parameter that is used in a loopFunction
	 */
	fun getLoopOpParameterName(originalName: Name): Name {
		return Name.identifier(originalName.asStringStripSpecialMarkers() + "_inLoop")
	}
	
	val loopOpParameterNameRegex = Regex(".*?_inLoop")
	
	
	fun makeGetterNameForState(originalName: Name): Name {
		return Name.identifier("get-" + originalName.asString())
	}
	
	val regexGetterName = Regex("^get-")
}