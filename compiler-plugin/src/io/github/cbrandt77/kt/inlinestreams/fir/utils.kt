package io.github.cbrandt77.kt.inlinestreams.fir

import io.github.cbrandt77.kt.inlinestreams.NamesAndIds
import io.github.cbrandt77.kt.inlinestreams.util.getReceiverClass
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirFunction
import org.jetbrains.kotlin.fir.declarations.utils.isExtension

context(_: FirSession)
val FirFunction.isInlineStreamExtension: Boolean
	get() = this.isExtension && getReceiverClass()?.classId == NamesAndIds.InlineStreamClassId