/*
 * Copyright (C) 2026 Caleb Brandt
 *
 * This file is part of Inline Streams.
 *
 * Inline Streams is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Inline Streams is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.github.cbrandt77.kt.inlinestreams

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object NamesAndIds {
	@JvmField val InlineStreamClassId: ClassId = ClassId.fromString("io/github/cbrandt77/kt/inlinestreams/InlineStream")
	@JvmField val InlineStreamClassIdFqName: FqName = InlineStreamClassId.asSingleFqName()
	
	@JvmField val STREAM_PACKAGE = FqName("io.github.cbrandt77.kt.inlinestreams")
	@JvmField val inlineStreamKt = ClassId(STREAM_PACKAGE, Name.identifier("InlineStreamKt"))
	
	val CallableId.isIntermediateOperation
		get() = this == intermediateOperation1 || this == intermediateOperation2
	
	val CallableId.isTerminalOperation
		get() = this == terminalOperation1 || this == terminalOperation2
	
	val CallableId.isEntranceFunction
		get() = this == entranceFunction1 || this == entranceFunction2
	
	
	
	private val intermediateOperation1: CallableId = CallableId(inlineStreamKt, Name.identifier("intermediateOperation"))
	private val intermediateOperation2: CallableId = CallableId(STREAM_PACKAGE, Name.identifier("intermediateOperation"))
	
	private val terminalOperation1: CallableId = CallableId(inlineStreamKt, Name.identifier("terminalOperation"))
	private val terminalOperation2: CallableId = CallableId(STREAM_PACKAGE, Name.identifier("terminalOperation"))
	
	private val entranceFunction1: CallableId = CallableId(inlineStreamKt, Name.identifier("inlineStream"))
	private val entranceFunction2: CallableId = CallableId(STREAM_PACKAGE, Name.identifier("inlineStream"))
	
	@JvmField val streamOperationsKt = ClassId(STREAM_PACKAGE, Name.identifier("StreamOperationsKt"))
	
	@JvmStatic fun IrPluginContext.referenceStdStreamOperationFunction(name: String): IrSimpleFunctionSymbol {
		return referenceFunctions(CallableId(STREAM_PACKAGE, Name.identifier(name)))
			.ifEmpty { referenceFunctions(CallableId(streamOperationsKt, Name.identifier(name))) }
			.first()
	}
	
	/**
	 * Annotation that links together a user-defined chainable function and its generated in-loop function
	 */
	@JvmField val linkingAnnotationClassId: ClassId = ClassId.fromString("io/github/cbrandt77/kt/inlinestreams/annotations/internal/InlineStreamFunctionReference")
	@JvmField val linkingAnnotationFqn: FqName = linkingAnnotationClassId.asSingleFqName()
	
	object IntermediateOperations {
		@JvmField val NAME_element = Name.identifier("element")
		@JvmField val NAME_breakLoop = Name.identifier("breakLoop")
		@JvmField val NAME_downstreamAction = Name.identifier("downstreamAction")
	}
}