package io.github.cbrandt77.kt.inlinestreams.annotations

/**
 * Anything annotated with this is solely a _compile-time construct_, and cannot be treated as a real object.
 *
 * [NotARealObject]s should never be allowed to escape the bounds of the scope they're created in, as they're just some kind of macro.
 *
 * TODO if I add more of these, like true multi-variable value classes, make this a library plugin to standardize "allowed to escape the scope" FIR diagnostics
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class NotARealObject