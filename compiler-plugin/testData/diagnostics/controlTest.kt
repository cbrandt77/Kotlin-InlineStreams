// RUN_PIPELINE_TILL: BACKEND

// MODULE: lib
// FILE: foo.kt
package foo;

class MyClass {

}


// MODULE: main(lib)
// FILE: bar.kt
package bar;

import foo.MyClass

fun test() {

}
