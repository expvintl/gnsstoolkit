#!/bin/bash
set -e
here="$(cd "$(dirname "$0")" && pwd)"
tc=/sd/Ubuntu/workspace/default/.toolchain
m2="$tc/gradle-home/caches/modules-2/files-2.1"
cc="$m2/org.jetbrains.kotlin/kotlin-compiler-embeddable/2.1.20/4ef56b3316798316bfac7a0ae443391c9e900ea1/kotlin-compiler-embeddable-2.1.20.jar"
std="$m2/org.jetbrains.kotlin/kotlin-stdlib/2.1.20/aa8ca79cd50578314f6d1180c47cbe14c0fee567/kotlin-stdlib-2.1.20.jar"
coro="$m2/org.jetbrains.kotlinx/kotlinx-coroutines-core-jvm/1.10.2/4a9f78ef49483748e2c129f3d124b8fa249dafbf/kotlinx-coroutines-core-jvm-1.10.2.jar"
trove="$m2/org.jetbrains.intellij.deps/trove4j/1.0.20200330/3afb14d5f9ceb459d724e907a21145e8ff394f02/trove4j-1.0.20200330.jar"
annot="$m2/org.jetbrains/annotations/13.0/919f0dfe192fb4e063e7dacadee7f8bb9a2672a9/annotations-13.0.jar"
cd "$here"
python3 regen.py
"$JAVA_HOME/bin/java" -cp "$cc:$std:$coro:$trove:$annot" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  Bench.kt -d bench.jar -nowarn -no-stdlib -no-reflect -cp "$std" 2>&1 | grep -v '^\s*at ' | grep -v '^WARNING' | head -20
"$JAVA_HOME/bin/java" -cp "bench.jar:$std" com.gnsstoolkit.data.BenchKt
