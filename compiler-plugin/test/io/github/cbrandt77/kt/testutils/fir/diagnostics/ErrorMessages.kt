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

package io.github.cbrandt77.kt.testutils.fir.diagnostics

import io.github.cbrandt77.kt.inlinestreams.fir.diagnostics.FirErrorTypes_InlineStreams
import org.jetbrains.kotlin.diagnostics.AbstractKtDiagnosticFactory
import org.junit.jupiter.api.assertAll
import kotlin.reflect.full.declaredMemberProperties
import kotlin.test.Test
import kotlin.test.assertNotNull

class ErrorMessages {
	
	@Test
	fun allMessagesHaveRenderers() {
		val rendererFactory = FirErrorTypes_InlineStreams.getRendererFactory()
		val executables: List<() -> Unit> = FirErrorTypes_InlineStreams::class
			.declaredMemberProperties
			.mapNotNull { prop ->
				val errorType = prop.get(FirErrorTypes_InlineStreams) as? AbstractKtDiagnosticFactory ?: return@mapNotNull null
				{ assertNotNull(rendererFactory.MAP[errorType], "${prop.name} does not have a message") }
			}
		
		assertAll(executables)
	}
}