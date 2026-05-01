package io.github.cbrandt77.kt.inlinestreams.ir

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isEntranceFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.IntermediateOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.StreamOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.IntermediateOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.StreamOperationLoopFunction
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.IrExceptionBuilder
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.irError
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
	
	val IrCall.callsInlineStreamFunction: Boolean
		get() = this.symbol.owner.actsOnInlineStream
	
	val IrCall.callsInlineStreamEntranceFunction: Boolean // TODO allow custom entrance functions
		get() = this.symbol.owner.callableId.isEntranceFunction
	
	
	fun getInlineStreamTypeParameterT(type: IrType): IrType? {
		return (type as? IrSimpleType)?.takeIf {
			it.classOrNull == inlineStreamClassSymbol
		}?.arguments?.getOrNull(0)?.typeOrNull
	}
	
	val IrCall.extensionReceiver_
		get() = symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.let { arguments[it] }
	
	private inline fun getAllLoopOpFunctions(chainFunc: StreamOperationChainCall, disambiguate: (Collection<IrSimpleFunctionSymbol>) -> StreamOperationLoopFunction): StreamOperationLoopFunction {
		val allFunctions = chainFunc.ir.symbol.owner.let { calledFunc ->
			context.referenceFunctions(calledFunc.callableId.let {
				it.copy(getLoopOpFunctionName(calledFunc))
			})
		}
		
		when (allFunctions.size) {
			0 -> irError("No 'action' function found for chainable operation.") {
				withIrEntry("stream operation", chainFunc.ir)
			}
			1 -> return StreamOperationLoopFunction(allFunctions.first().owner).getOrThrow()
			else -> return disambiguate(allFunctions)
		}
	}
	
	fun IntermediateOperationChainCall.getLoopOpFunction(): StreamOperationLoopFunction {
		// TODO we need IntermediateOperationChainFunctionDefinition too
		/**
		 * How do we disambiguate:
		 * - Check the function's own parameters
		 * - Check the input type
		 * - Check the output type
		 */
		return getAllLoopOpFunctions(internal) { items ->
			val allValidIntermediateOperations = items.mapNotNull { IntermediateOperationLoopFunction(it.owner).getOrNull() }.ifEmpty {
				TODO("throw error explaining what makes each item not qualify as an intermediate operation")
			}
			allValidIntermediateOperations.singleOrNull()?.let {
				return@getAllLoopOpFunctions it.internal;
			}
			
//
//			val withSameInputType = allValidIntermediateOperations.filter {
//
//			}
			TODO()
		}
	}
	
	
	
	fun getLoopOpFunctionName(chainFunc: IrFunction): Name {
		return Name.identifier("loopAction-" + chainFunc.name.asString())
	} // TODO make this actually robust
	
	val IrFunction.isLoopOperation
		get() = this.name.asString().startsWith("loopAction-")
	
	
	
	/**
	 * Make the version of a dslFunction parameter that is used in a loopFunction
	 */
	fun getDslFunParameterName(originalName: Name): Name {
		return Name.special("<arg-" + originalName.asStringStripSpecialMarkers() + ">")
	}
	
	val IrValueParameter.isDslFunParameter: Boolean
		get() = name.isSpecial && name.asString().startsWith("<arg-")
	
	
	
	
	private val getterRegex = Regex("^<get-(.*)>$")
	
	fun makeGetterNameForState(originalName: Name): Name {
		return Name.special("<get-" + originalName.asStringStripSpecialMarkers() + ">")
	}
	
	val Name.nameThisIsGetterFor: Name?
		get() = this.takeIf { it.isSpecial }
			?.asString()
			?.let { getterRegex.find(it, 5)?.groupValues?.firstOrNull() }
			?.let { Name.identifier(it) }
	
	
	
	private val setterRegex = Regex("<set-(.*)>")
	
	fun makeSetterNameForState(originalName: Name): Name {
		return Name.special("<set-" + originalName.asStringStripSpecialMarkers() + ">")
	}
	
	val Name.nameThisIsSetterFor: Name?
		get() = this.takeIf { it.isSpecial }
			?.asString()
			?.let { setterRegex.find(it, 5)?.groupValues?.firstOrNull() }
			?.let { Name.identifier(it) }
}