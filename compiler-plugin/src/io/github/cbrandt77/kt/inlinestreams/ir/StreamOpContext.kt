package io.github.cbrandt77.kt.inlinestreams.ir

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isEntranceFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.IntermediateOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.StreamOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.IntermediateOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.StreamOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.util.typesMatch
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.irError
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.cast

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
	
	val IrFunction.hasInlineStreamReceiver: Boolean
		get() = parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.type?.classOrNull == inlineStreamClassSymbol
	
	val IrCall.callsInlineStreamFunction: Boolean
		get() = this.symbol.owner.hasInlineStreamReceiver
	
	val IrCall.callsInlineStreamEntranceFunction: Boolean // TODO allow custom entrance functions
		get() = this.symbol.owner.callableId.isEntranceFunction
	
	
	fun getInlineStreamTypeParameterT(type: IrType): IrType? {
		return (type as? IrSimpleType)?.takeIf {
			it.classOrNull == inlineStreamClassSymbol
		}?.arguments?.getOrNull(0)?.typeOrNull
	}
	
	val IrCall.extensionReceiver_
		get() = symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.let { arguments[it] }
	
	
	fun StreamOperationChainCall.basicDisambiguation(): Result<List<StreamOperationLoopFunction>> {
		val allFunctions = ir.symbol.owner.let { calledFunc ->
			context.referenceFunctions(calledFunc.callableId.let {
				it.copy(getLoopOpFunctionName(calledFunc))
			})
		}
		
		when (allFunctions.size) {
			0 -> return runCatching { irError("No 'action' function found for chainable operation.") {
				withIrEntry("chain function", ir)
			} }
			1 -> return StreamOperationChainFunction.invoke(allFunctions.first().owner).map { listOf(it.ir) }
		}
		
		/**
		 * How do we disambiguate:
		 * - Check the function's own parameters
		 * - Check the input type
		 * - Check the output type
		 */
		
		val allValidIntermediateOperations = allFunctions
			.mapNotNull { StreamOperationLoopFunction(it.owner).getOrNull() }
			.ifEmpty {
				return Result.failure(InlineStreamFunctionResolutionException(target.ir.callableId, allFunctions.toList(), "Not an intermediate operation."))
			}.also {
				if (it.size == 1)
					return Result.success(it);
			}
		
		
		val ourTarget = this.target
		
		val targetParameterTypes = target.arguments.map { it.type }.toList()
		
		val withSameParameters = allValidIntermediateOperations.filter { op ->
			op.dslFunArgs.zip(targetParameterTypes.asSequence()).all { typesMatch(it.first.type, it.second) }
		}.ifEmpty {
			return Result.failure(InlineStreamFunctionResolutionException(target.ir.callableId, allValidIntermediateOperations.map { it.ir.symbol }, "Required parameter types '${targetParameterTypes.map { it.render() }}'"));
		}.also {
			if (it.size == 1)
				return Result.success(it);
		}
		
		
		val targetInputType = ourTarget.inputElementType
		
		val withSameInputType = withSameParameters.filterTo(ArrayList(withSameParameters.size)) {
			typesMatch(targetInputType, it.inputElementType)
		}.ifEmpty {
			return Result.failure(InlineStreamFunctionResolutionException(target.ir.callableId, withSameParameters.map { it.ir.symbol }, "Required input type '${targetInputType.render()}'"));
		}.also {
			if (it.size == 1)
				return Result.success(it);
		}
		
	}
	
	@OptIn(UnsafeCastFunction::class)
	fun IntermediateOperationChainCall.getLoopOpFunction(): Result<IntermediateOperationLoopFunction> {
		val fromBasic = this.internal.basicDisambiguation().also {
			if (it.isFailure)
				return it.cast()
		}.getOrNull()!!.map { IntermediateOperationLoopFunction.invoke(it) }
		
		val ourTarget = target
		val targetOutputType = ourTarget.outputElementType
		
		val withSameOutputType = fromBasic.filterTo(ArrayList(fromBasic.size)) {
			typesMatch(targetOutputType, it.outputElementType)
		}.ifEmpty {
			return Result.failure(InlineStreamFunctionResolutionException(target.ir.callableId, withSameInputType.map { it.ir.symbol }, "Required output type '${targetOutputType.render()}'"));
		}.also {
			if (it.size == 1)
				return Result.success(it[0]);
		}
		
		return Result.failure(InlineStreamFunctionResolutionException(target.ir.callableId, withSameOutputType.map { it.ir.symbol }, "Too many functions found"))
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

open class InlineStreamTranslationException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

class InlineStreamFunctionResolutionException(val callableId: CallableId, val possibleFunctions: List<IrFunctionSymbol>, val reason: String? = null, val additionalInfo: List<String> = emptyList()) : RuntimeException() {
	override val message get() = "Unable to disambiguate between overloads of '${callableId.asFqNameForDebugInfo()}': ${reason.toString()}\n" +
	                             "Possible targets:\n" +
	                             possibleFunctions.asSequence().let { if (additionalInfo.size == possibleFunctions.size) it.zip(additionalInfo.asSequence()) else it.map { it to "" } }.joinToString("\n- ") { (func, add) -> func.owner.render() + " - " + add }
}
