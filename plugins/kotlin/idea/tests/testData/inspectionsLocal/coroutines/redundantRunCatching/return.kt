// WITH_STDLIB
// PROBLEM: none

fun foo(a: Int) = kotlin.runCatching<caret> {
    if (a % 2 == 0) return
    5
}.getOrThrow()
