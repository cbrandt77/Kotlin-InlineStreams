package io.github.cbrandt77.kt.inlinestreams.ir.visitors

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isIntermediateOperation
import io.github.cbrandt77.kt.inlinestreams.NamesAndIds.isTerminalOperation
import io.github.cbrandt77.kt.inlinestreams.ir.StreamOpContext
import io.github.cbrandt77.kt.inlinestreams.ir.types.annotations.InlineStreamFunctionReferenceAnnotation
import io.github.cbrandt77.kt.inlinestreams.ir.types.declarations.chain.StreamOperationChainFunction
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.descriptors.DescriptorVisibilities
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.builders.declarations.UNDEFINED_PARAMETER_INDEX
import org.jetbrains.kotlin.ir.builders.declarations.addTypeParameter
import org.jetbrains.kotlin.ir.builders.declarations.addValueParameter
import org.jetbrains.kotlin.ir.builders.declarations.buildFun
import org.jetbrains.kotlin.ir.declarations.DelicateIrParameterIndexSetter
import org.jetbrains.kotlin.ir.declarations.IrDeclarationContainer
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrTypeParameter
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrFunctionExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.IrReturn
import org.jetbrains.kotlin.ir.expressions.IrSetValue
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.IrValueAccessExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrConstructorCallImpl
import org.jetbrains.kotlin.ir.expressions.impl.IrGetValueImpl
import org.jetbrains.kotlin.ir.symbols.IrValueParameterSymbol
import org.jetbrains.kotlin.ir.symbols.IrVariableSymbol
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.defaultType
import org.jetbrains.kotlin.ir.types.typeWith
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.deepCopyWithSymbols
import org.jetbrains.kotlin.ir.util.invokeFun
import org.jetbrains.kotlin.ir.util.irError
import org.jetbrains.kotlin.ir.util.isFunction
import org.jetbrains.kotlin.ir.util.parents
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.remapTypeParameters
import org.jetbrains.kotlin.ir.util.statements
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrTypeTransformerVoid
import org.jetbrains.kotlin.ir.visitors.IrVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.resolve.inline.InlineUtil.isInline

object StreamOpGeneratedLoopActionDeclarationKey : GeneratedDeclarationKey() {
	val origin = IrDeclarationOrigin.GeneratedByPlugin(this)
}



/**
 * From the `fun InlineStream<T>.someOperation` function the user writes,
 * generate the `inline fun someOperation(el: T, downstream: () -> Unit): Unit`
 * that actually gets called at runtime under the same parent as the `InlineStream.someOperation` function.
 */
class StreamOpRuntimeVariantGenerator(
	val context: IrPluginContext,
	val data: StreamOpContext
) : IrElementTransformerVoid() {
	override fun visitElement(element: IrElement): IrElement {
		element.transformChildrenVoid(this)
		return element;
	}
	
	override fun visitFunction(declaration: IrFunction): IrStatement = with (data) {
		if (declaration.hasInlineStreamReceiver)
			FunctionVisitor().visitFunction(declaration)
		
		return super.visitFunction(declaration)
	}
	
	
	/**
	 * Visits `InlineStream.something` functions
	 */
	inner class FunctionVisitor : IrVisitorVoid() {
		lateinit var func: IrFunction
		
		override fun visitFunction(declaration: IrFunction) {
			func = declaration
			
			for (statement in declaration.body?.statements ?: TODO("custom error message")) {
				val callsFunction = statement.let {
					when (it) {
						is IrReturn -> it.value
						else -> it
					}
				} as? IrCall
	                    ?: continue;
				
				if (callsFunction.isIntermediateOpBlock || callsFunction.isTerminalOpBlock) {
					LoopFunctionGenerator(context, data, context (data) { StreamOperationChainFunction(declaration).getOrThrow() }, IntermediateOperationDefFuncInvocation(callsFunction)).buildLoopActionFun()
					break;
				}
			}
		}
	}
}

/**
 * Create the inline function to be executed within the loop
 * - Replace GET_VAR variableDeclaredOutsideBlock with call to getter from parameters
 *   - Add getter to parameters also
 * - Replace SET_VAR variableDeclaredOutsideBlock with call to setter from parameters
 *   - Add setter to parameters also
 */
class LoopFunctionGenerator(val context: IrPluginContext, val data: StreamOpContext, val dslFunction: StreamOperationChainFunction, call: IntermediateOperationDefFuncInvocation) {
	val loopAction: IrFunction = call.action.ir
	
	private val newFun = context.irFactory.createSimpleFunction(
		symbol = data.getLoopFunctionSymbol(dslFunction),
		startOffset = loopAction.startOffset,
		endOffset = loopAction.endOffset,
		name = data.getLoopOpFunctionName(dslFunction.ir.name),
		returnType = context.irBuiltIns.unitType,
		isInline = true,
//			originalDeclaration = dslFunction,
		origin = StreamOpGeneratedLoopActionDeclarationKey.origin,
		visibility = DescriptorVisibilities.PUBLIC,
		modality = Modality.FINAL,
		isTailrec = false,
		isExpect = false,
		isOperator = false,
		isSuspend = false,
		isInfix = false,
		isExternal = false,
		isFakeOverride = false
	).apply {
		body = loopAction.body?.deepCopyWithSymbols(this)
	}
	
	private val IrVariable.isDeclaredOutsideLoopAction: Boolean
		get() = newFun !in this.parents
	
	private val IrValueAccessExpression.takeTargetIfNeedsRemapping: IrVariable?
		get() = (symbol as? IrVariableSymbol)
			?.owner
			?.takeIf { it.isDeclaredOutsideLoopAction }
	
	
	
	private val getters = object {
		private val functions: Object2ObjectOpenHashMap<IrVariable, IrValueParameter> = Object2ObjectOpenHashMap()
		
		private val getterType by lazy {
			context.irBuiltIns.functionN(0)
		}
		private val getterInvokeFun by lazy {
			getterType.invokeFun!!.symbol
		}
		
		operator fun contains(param: IrValueParameter): Boolean {
			return param in functions.values
		}
		
		private fun getOrCreateGetterParameter(variable: IrVariable): IrValueParameter {
			return functions.computeIfAbsent(variable) { it: IrVariable ->
				newFun.addValueParameter {
					name = data.makeGetterNameForState(it.name)
					type = getterType.typeWith(it.type)
					origin = StreamOpGeneratedLoopActionDeclarationKey.origin
				}
			}
		}
		
		fun remapToGetterIfNeeded(value: IrGetValue): IrExpression {
			val targetVariable = value.takeTargetIfNeedsRemapping
			                     ?: return value;
			
			return IrCallImpl(value.startOffset, value.endOffset, value.type, getterInvokeFun).also {
				it.dispatchReceiver = IrGetValueImpl(value.startOffset, value.endOffset, getOrCreateGetterParameter(targetVariable).symbol, IrStatementOrigin.VARIABLE_AS_FUNCTION)
			}
		}
	}
	
	private val setters = object {
		private val functions = Object2ObjectOpenHashMap<IrVariable, IrValueParameter>()
		
		private val setterType by lazy {
			context.irBuiltIns.functionN(1)
		}
		private val setterInvokeFun by lazy {
			setterType.invokeFun!!.symbol
		}
		
		operator fun contains(param: IrValueParameter): Boolean {
			return param in functions.values
		}
		
		private fun getOrCreateParameter(variable: IrVariable): IrValueParameter {
			return functions.computeIfAbsent(variable) { it: IrVariable ->
				newFun.addValueParameter {
					name = data.makeSetterNameForState(it.name)
					type = setterType.typeWith(it.type, context.irBuiltIns.unitType)
					origin = StreamOpGeneratedLoopActionDeclarationKey.origin
				}
			}
		}
		
		/** If [setValue] sets a variable declared outside the loopAction, remap it to a call to a setter parameter. */
		fun remapToSetterIfNeeded(setValue: IrSetValue): IrExpression {
			val variableBeingSet = setValue.takeTargetIfNeedsRemapping ?: return setValue;
			
			return IrCallImpl(setValue.startOffset, setValue.endOffset, context.irBuiltIns.unitType, setterInvokeFun, origin = IrStatementOrigin.INVOKE).also {
				it.dispatchReceiver = IrGetValueImpl(setValue.startOffset, setValue.endOffset, getOrCreateParameter(variableBeingSet).symbol, origin = IrStatementOrigin.VARIABLE_AS_FUNCTION) // TODO replace with arguments.add
				it.arguments[1] = setValue.value
			};
		}
	}
	
	@OptIn(DelicateIrParameterIndexSetter::class)
	private val parameters = object {
		private val oldFuncToNewFuncMap: Map<IrValueParameterSymbol, IrValueParameterSymbol> = dslFunction.arguments
			                                                                                       .associate {
				                                                                                       it.symbol to newFun.addValueParameter {
					                                                                                       updateFrom(it)
					                                                                                       name = data.getDslFunParameterName(it.name)
					                                                                                       if (it.type.isFunction() && !dslFunction.ir.isInline) {
																											   isNoinline = true
					                                                                                       }
				                                                                                       }.symbol
			                                                                                       } +
		                                                                                       loopAction.parameters
																								   .asSequence()
			                                                                                       .filter { it.kind != IrParameterKind.ExtensionReceiver }
			                                                                                       .map { it.symbol }
			                                                                                       .associateWith {
				                                                                                       newFun.addValueParameter {
					                                                                                       updateFrom(it.owner)
					                                                                                       name = it.owner.name
				                                                                                       }.symbol
			                                                                                       }
				
		
		fun remap(parameter: IrValueParameterSymbol): IrValueParameterSymbol {
			return oldFuncToNewFuncMap[parameter]
			       ?: irError("No remapped parameter found") {
					   withIrEntry("requested", parameter.owner)
			       }
		}
	}
	
	// as we encounter type parameters, add them to this function
	private val typeParameters = object : IrTypeTransformerVoid() {
		val added = Object2ObjectOpenHashMap<IrTypeParameter, IrTypeParameter>()
		
		private fun getOrCreateTypeParam(fromDslFunc: IrTypeParameter): IrTypeParameter {
			return added.computeIfAbsent(fromDslFunc) { it: IrTypeParameter ->
				newFun.addTypeParameter {
					updateFrom(it)
					index = UNDEFINED_PARAMETER_INDEX
					superTypes.addAll(it.superTypes)
					name = it.name
				}
			}
		}
		override fun <Type : IrType?> transformTypeRecursively(container: IrElement, type: Type): Type {
			if (type == null)
				return type;
			
			@Suppress("UNCHECKED_CAST")
			return type.remapTypeParameters(
				dslFunction.ir, newFun,
				Map_ComputeInsteadOfGet(added, ::getOrCreateTypeParam)
			) as Type
		}
	}
	
	inner class ElementTransformer : IrElementTransformerVoid() {
		override fun visitGetValue(expression: IrGetValue): IrExpression {
			return when (val target = expression.symbol) {
				is IrValueParameterSymbol -> {
					if (!target.owner.let { it in getters || it in setters })
						expression.symbol = parameters.remap(target)
					
					expression
				}
				is IrVariableSymbol -> getters.remapToGetterIfNeeded(expression)
			}
		}
		
		override fun visitSetValue(expression: IrSetValue): IrExpression {
			val expr = setters.remapToSetterIfNeeded(expression)
			expr.transformChildrenVoid()
			return expr;
		}
	}
	
	fun buildLoopActionFun() {
		(dslFunction.ir.parent as IrDeclarationContainer).also {
			it.declarations += newFun
			newFun.parent = it
		}
		
		newFun.body?.transformChildrenVoid(ElementTransformer())
		newFun.acceptVoid(typeParameters)
		
		with (data) {
			newFun.annotations += InlineStreamFunctionReferenceAnnotation(dslFunction.identifier).ir
		}
	}
}

class Map_ComputeInsteadOfGet<K, V>(val wrapped: MutableMap<K, V>, val computeFun: (K) -> V) : MutableMap<K, V> by wrapped {
	override fun get(key: K): V {
		return wrapped.computeIfAbsent(key, computeFun)
	}
	
	override fun getOrDefault(key: K, defaultValue: V): V {
		return get(key)
	}
}

@JvmInline value class IntermediateOperationDefFuncInvocation(val call: IrCall) {
//	/** The type of the stream it acts on. `InlineStream<T>.doAction` */
//	val inputType: IrType
//		get() = call.typeArguments[0]!! // TODO this is wrong
//
//	/** The type of the stream it returns. `doAction(): InlineStream<U>` */
//	val outputType: IrType
//		get() = call.typeArguments[1]!! // TODO this is wrong
//
	/** The thing that should actually be executed inside the loop */
	val action: IntermediateOperationAction
		get() = IntermediateOperationAction((call.arguments[1] as IrFunctionExpression).function)
}

/**
 * The lambda passed to `intermediateOperation`,
 * the action actually being performed within the loop.
 */
@JvmInline value class IntermediateOperationAction(val ir: IrFunction) {
	val pEl: IrValueParameter
		get() = ir.parameters[0]
	
	val pBreakLoop: IrValueParameter
		get() = ir.parameters[1]
	
	val pDownstreamAction: IrValueParameter
		get() = ir.parameters[2]
	
	val statements: List<IrStatement>
		get() = ir.body!!.statements
}

private val IrCall.isIntermediateOpBlock: Boolean
	get() = this.symbol.owner.callableId.isIntermediateOperation

private val IrCall.isTerminalOpBlock: Boolean
	get() = this.symbol.owner.callableId.isTerminalOperation