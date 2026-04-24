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

import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

object NamesAndIds {
	@JvmField val InlineStreamClassId: ClassId = ClassId.fromString("io/github/cbrandt77/kt/inlinestreams/InlineStream")
	@JvmField val InlineStreamClassIdFqName: FqName = InlineStreamClassId.asSingleFqName()
	
	@JvmField val STREAM_PACKAGE = FqName("io.github.cbrandt77.kt.inlinestreams")
	@JvmField val inlineStreamKt = ClassId(STREAM_PACKAGE, Name.identifier("InlineStreamKt"))
	
	@JvmField val intermediateOperation: CallableId = CallableId(inlineStreamKt, Name.identifier("intermediateOperation"))
	@JvmField val terminalOperation: CallableId = CallableId(inlineStreamKt, Name.identifier("terminalOperation"))
}