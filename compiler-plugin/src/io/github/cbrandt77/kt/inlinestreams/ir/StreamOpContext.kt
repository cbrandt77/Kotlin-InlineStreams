package io.github.cbrandt77.kt.inlinestreams.ir

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isEntranceFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.IntermediateOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.IntermediateOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.StreamOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.util.flatMap
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.impl.IrReturnImpl
import org.jetbrains.kotlin.ir.symbols.IrFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.types.IrSimpleType
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classOrNull
import org.jetbrains.kotlin.ir.types.typeOrNull
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds
import kotlin.error

class StreamOpContext(val context: IrPluginContext) {
	val inlineStreamClassSymbol by lazy {
		context.referenceClass(NamesAndIds.InlineStreamClassId) ?: error("Unable to find InlineStream class symbol.")
	}
	
	val linkingAnnotationClassSymbol by lazy {
		context.referenceClass(NamesAndIds.linkingAnnotationClassId) ?: error("Unable to find InlineStreamFunctionReference class symbol.")
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
	
	val IrCall.callsInlineStreamEntranceFunction: Boolean // TODO inputs X returns InlineStream<Y>
		get() = this.symbol.owner.callableId.isEntranceFunction
	
	
	fun getInlineStreamTypeParameterT(type: IrType): IrType? {
		return (type as? IrSimpleType)?.takeIf {
			it.classOrNull == inlineStreamClassSymbol
		}?.arguments?.getOrNull(0)?.typeOrNull
	}
	
	val IrCall.extensionReceiver_
		get() = symbol.owner.parameters.firstOrNull { it.kind == IrParameterKind.ExtensionReceiver }?.let { arguments[it] }
	
	fun StreamOperationChainFunction.getLoopFunction(): Result<StreamOperationLoopFunction> {
		val ourCallableId = this.ir.callableId
		val allWithName = context.referenceFunctions(ourCallableId.copy(getLoopOpFunctionName(this.ir.name)))
			.ifEmpty {
				return Result.failure(InlineStreamFunctionResolutionException(ourCallableId, listOf(), "No functions found for that name."));
			}.toList()
		
		val allAsStreamOperations = allWithName.map {
			StreamOperationLoopFunction.invoke(it.owner)
		}
		
		if (allAsStreamOperations.all(Result<*>::isFailure)) {
			return Result.failure(InlineStreamFunctionResolutionException(ourCallableId, allWithName, "No stream operations found.", allAsStreamOperations.map { it.exceptionOrNull()?.stackTraceToString() ?: "" }));
		}
		
		
		val allValidIntermediateOperations = allAsStreamOperations
			.mapNotNull { it.getOrNull() }
			.also {
				if (it.size == 1)
					return Result.success(it[0]);
			}
		
		// disambiguate by identifier
		val ourIdentifier = identifier
		
		val disambiguatedByIdentifier = allValidIntermediateOperations.filter { it.identifier == ourIdentifier }
		
		return disambiguatedByIdentifier.singleOrNull()?.let { Result.success(it) }
		       ?: Result.failure(InlineStreamFunctionResolutionException(ourCallableId, disambiguatedByIdentifier.map { it.ir.symbol }, "Expected 1 function with matching identifier, found ${disambiguatedByIdentifier.size}."))
	}
	
	fun IntermediateOperationChainFunction.getLoopFunction(): Result<IntermediateOperationLoopFunction> {
		return internal.getLoopFunction().flatMap { IntermediateOperationLoopFunction(it) }
	}
	
	
	
	fun getLoopOpFunctionName(chainFunc: Name): Name {
		return Name.identifier("loopAction-" + chainFunc.asString())
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
	
	val Name.nameThisIsGetterFor: String?
		get() = this.takeIf { it.isSpecial }
			?.asString()
			?.let { getterRegex.find(it, 5)?.groupValues?.firstOrNull() }
	
	
	
	private val setterRegex = Regex("<set-(.*)>")
	
	fun makeSetterNameForState(originalName: Name): Name {
		return Name.special("<set-" + originalName.asStringStripSpecialMarkers() + ">")
	}
	
	val Name.nameThisIsSetterFor: String?
		get() = this.takeIf { it.isSpecial }
			?.asString()
			?.let { setterRegex.find(it, 5)?.groupValues?.firstOrNull() }
	
	
//	/**
//	 * For a function `X.inlineStream(): InlineStream<Y>`, get the function to create an iterator from X.
//	 *
//	 * It has to return `InlineStream(someIterator)`, so we just grab the
//	 */
//	fun getIteratorFunctionForEntranceFunction(entranceFunction: IrFunction): IrSimpleFunctionSymbol? {
//		val body = entranceFunction.body ?: error("Entrance function doesn't have a body.")
//		val returnStatement = body.statements.firstOrNull { it is IrReturn } as? IrReturn ?: error("Entrance function doesn't return.")
//
//		val ctorCall = (returnStatement.value as? IrConstructorCall)?.takeIf { it.type.classOrNull == inlineStreamClassSymbol }
//		               ?: error("entrance function doesn't return InlineStream")
//
//		val x = ctorCall.arguments[0]
//		when (x) {
//			is IrCall -> return x.symbol
//			is IrGetValue -> // I might need to have the InlineStream functions generate a function themselves to get the iterator
//		}
//	}
}

open class InlineStreamTranslationException(message: String? = null, cause: Throwable? = null) : RuntimeException(message, cause)

class InlineStreamFunctionResolutionException(val callableId: CallableId, val possibleFunctions: List<IrFunctionSymbol>, val reason: String = "", val additionalInfo: List<String> = emptyList()) : RuntimeException() {
	override val message get() = "Unable to disambiguate between overloads of '${callableId.asFqNameForDebugInfo()}': ${reason}\n" +
	                             "Possible targets:\n" +
	                             possibleFunctions.asSequence().let { if (additionalInfo.size == possibleFunctions.size) it.zip(additionalInfo.asSequence()) else it.map { it to "" } }.joinToString("\n- ") { (func, add) -> func.owner.render() + " - " + add }
}
