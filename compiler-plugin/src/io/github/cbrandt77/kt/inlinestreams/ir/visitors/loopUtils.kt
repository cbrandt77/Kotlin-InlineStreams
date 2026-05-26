package io.github.cbrandt77.kt.inlinestreams.ir.visitors

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.IntermediateOperations.NAME_element
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.IntermediateOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.StreamOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.calls.TerminalOperationChainCall
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.IntermediateOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.TerminalOperationChainFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.IntermediateOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.StreamOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.loop.TerminalOperationLoopFunction
import io.github.cbrandt77.kt.inlinestreams.ir.visitors.LoopCreator.Companion.INLINE_STREAM_OPERATION
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.declarations.IrDeclarationParent
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin.Companion.LAMBDA
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrFunctionExpressionImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrValueSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.irError
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


sealed class OperationWithState(
	/**
	 * Map of "variables declared inside the dsl function" to "their new location outside the loop"
	 */
	val stateVariablesMap: MutableMap<IrValueSymbol, IrVariableSymbol> = mutableMapOf()
) {
	operator fun component4() = stateVariablesMap
	
	val isTerminal get() = this is TerminalOperationWithState
	
	context(_: StreamOpContext)
	val preamble get() = when (this) {
		is TerminalOperationWithState -> chainFunction.preamble
		is IntermediateOperationWithState -> chainFunction.preamble
	}
	
	val chainCall_general: StreamOperationChainCall get() = when (this) {
		is IntermediateOperationWithState -> chainCall.internal
		is TerminalOperationWithState -> chainCall.internal
	}
	
	val chainFunc_general: StreamOperationChainFunction get() = when (this) {
		is IntermediateOperationWithState -> chainFunction.internal
		is TerminalOperationWithState -> chainFunction.internal
	}
	
	val loopFunc_general: StreamOperationLoopFunction get() = when (this) {
		is IntermediateOperationWithState -> loopFunction.internal
		is TerminalOperationWithState -> loopFunction.internal
	}
}

class IntermediateOperationWithState(
	val chainCall: IntermediateOperationChainCall,
	val chainFunction: IntermediateOperationChainFunction,
	val loopFunction: IntermediateOperationLoopFunction,
) : OperationWithState() {
	operator fun component1() = chainCall
	operator fun component2() = chainFunction
	operator fun component3() = loopFunction
}
class TerminalOperationWithState(
	val chainCall: TerminalOperationChainCall,
	val chainFunction: TerminalOperationChainFunction,
	val loopFunction: TerminalOperationLoopFunction,
) : OperationWithState() {
	operator fun component1() = chainCall
	operator fun component2() = chainFunction
	operator fun component3() = loopFunction
}


/**
 * Given a list of [IntermediateOperationWithState]s ending with a [TerminalOperationWithState], such as `[flatMap, map, drop, firstOrNull]`, nest them like this:
 * ```kotlin
 * flatMap(item, { return@loop }, ...) { el ->
 *   map(el, { return@loop }, ...) { el2 ->
 *      drop(el2, { return@loop }, ...) { el3 ->
 *          firstOrNull(el3, { return@loop })
 *      }
 *   }
 * }
 * ```
 *
 * @param parentScope The function containing this loop call. Used for function parenting.
 * @param parentElSymbol The symbol representing the "element" in the iterator that the first function should receive.  e.g. `for (el in ...)`
 * @param operations An iterator over a list of operations: `[(...Intermediate), Terminal]
 * @param makeBreakLoopArg Factory for a `() -> Unit` lambda that'll break the loop. See [io.github.cbrandt77.kt.inlinestreams.ir.visitors.LoopCreator.FunctionChecker.remappedInlineStream]
 */
@OptIn(ExperimentalContracts::class)
tailrec fun StreamOpContext.callLoopOperationRecursive(parentScope: IrFunction, parentElSymbol: IrValueSymbol, operations: Iterator<OperationWithState>, makeBreakLoopArg: (IrDeclarationParent) -> IrExpression, commitStatement: (IrStatement) -> Unit) {
	contract {
		callsInPlace(commitStatement, InvocationKind.EXACTLY_ONCE)
	}
	
	val operation = operations.next()
	
	val chainCallGeneral = operation.chainCall_general
	val chainFunGeneral = operation.chainFunc_general
	val loopFunGeneral = operation.loopFunc_general
	
	val loopActionCall = IrCallImpl(chainCallGeneral.ir.startOffset, chainCallGeneral.ir.endOffset, unitType, loopFunGeneral.ir.symbol, origin=INLINE_STREAM_OPERATION).apply {
		assert(loopFunGeneral.ir.typeParameters.size == chainFunGeneral.ir.typeParameters.size) { "Expected generated loop-action function to have exact same type parameters as the original chain function." }
		
		chainCallGeneral.ir.typeArguments.forEachIndexed { index, type ->
			typeArguments[index] = type
		}
	}
	
	
	var ourDownstreamActionLambdaElementSymbol: IrValueSymbol? = null
	var ourDownstreamActionLambda: IrFunction? = null
	val downstreamActionArg = when (operation) {
		is IntermediateOperationWithState -> {
			val uType = operation.chainCall.outputElementType
			val downstreamActionLambda = makeLambdaFunction {
				parent = parentScope
				ourDownstreamActionLambdaElementSymbol = addValueParameter(NAME_element, uType).symbol
			}
			ourDownstreamActionLambda = downstreamActionLambda
			sequenceOf(IrFunctionExpressionImpl(-1, -1, irBuiltIns.functionN(1).typeWith(uType, unitType), downstreamActionLambda, LAMBDA))
		}
		else -> emptySequence()
	}
	
	// add parameters: [...chainFunArgs], el, breakloop, (downstreamAction), [...state functions]
	var numArgsBeforeState = 0
	sequenceOf(
		chainFunGeneral.parameters.map { param ->
			operation.stateVariablesMap[param.symbol]?.let { IrGetValueImpl(-1, -1, it) } // non-inline parameters get remapped to variables outside the loop
				?: chainCallGeneral.ir.arguments[param.indexInParameters]?.deepCopyWithSymbols(parentScope) // inline function parameters are just transplanted raw
		},
		sequenceOf(
			IrGetValueImpl(-1, -1, parentElSymbol),
			makeBreakLoopArg(parentScope)
		),
		downstreamActionArg
	).flatten().forEachIndexed { index, expression ->
		++numArgsBeforeState
		loopActionCall.arguments[index] = expression
	}
	
	
	val `needs -etters` = loopFunGeneral.parameters.drop(numArgsBeforeState).toList()
	if (`needs -etters`.isNotEmpty()) {
		val currentVariablesByName: Map<String, IrVariableSymbol> = operation.stateVariablesMap.mapKeys {
			it.key.owner.name.asString()
		}
		
		`needs -etters`.forEach { param ->
			param.name.nameThisIsGetterFor?.let {
				val correspondingVar = currentVariablesByName[it] ?: irError("No variable found for requested getter.") {
					withIrEntry("loop_function", loopFunGeneral.ir)
					withIrEntry("getter_parameter", param)
				}
				loopActionCall.arguments[param] = makeGetterLambda(correspondingVar.owner.type, correspondingVar.owner, parentScope)
			} ?: param.name.nameThisIsSetterFor?.let {
				val correspondingVar = currentVariablesByName[it]
				                       ?: irError("No variable found for requested setter.") {
					                       withIrEntry("loop_function", loopFunGeneral.ir)
					                       withIrEntry("setter_parameter", param)
				                       }
				loopActionCall.arguments[param] = makeSetterLambda(correspondingVar.owner.type, correspondingVar.owner, parentScope)
			} ?: irError("Parameter is not a getter nor setter") {
				withIrEntry("parameter", param)
				withIrEntry("loop_function", loopFunGeneral.ir)
			}
		}
	}
	
	commitStatement(loopActionCall)
	
	if (operations.hasNext())
		callLoopOperationRecursive(ourDownstreamActionLambda!!, ourDownstreamActionLambdaElementSymbol!!, operations, makeBreakLoopArg) { statement ->
			ourDownstreamActionLambda.body = irFactory.createBlockBody(-1, -1).also {
				it.statements += statement
			}
		}
}
