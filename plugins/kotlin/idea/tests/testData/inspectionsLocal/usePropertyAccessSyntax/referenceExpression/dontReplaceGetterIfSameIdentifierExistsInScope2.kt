// PROBLEM: Use of getter method instead of property access syntax
// FIX: Use property access syntax
// WITH_STDLIB
// IGNORE_K2
import java.io.File

fun test(file: File) {
    with(file) {
        val parentFile = <caret>getParentFile()
    }
}