# 2.2 - BreakContinueInInlineLambdas
Pre-2.2: can't use break/continue inside inline lambdas

Solution: change the `breakLoop` function to be `return@someRunTarget` instead. It does the same thing and is cross-compatible.
```kotlin
for (el in items) {
	run inlineStream2@ {
		map({ it.toCharArray() }, el, { return@inlineStream2 }) {
			flatMap({ it.toList() }, it, { return@inlineStream2 }, {
				map({ it.code }, it, { return@inlineStream2 }) {
					firstOrNull(it, { return@inlineStream2 }, { out = it })
				}
			})
		}
	}
}
```