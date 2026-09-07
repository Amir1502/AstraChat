#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
BUILD=core/build/offline-checks
mkdir -p "$BUILD"
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -d "$BUILD" \
    core/src/main/java/com/folzi/astrachat/core/SseReader.java \
    core/src/main/java/com/folzi/astrachat/core/TokenMath.java \
    core/src/test/java/com/folzi/astrachat/core/OfflineChecks.java
java -cp "$BUILD" com.folzi.astrachat.core.OfflineChecks
