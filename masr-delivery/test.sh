#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
./build.sh
mkdir -p build/test-classes
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -cp build/classes -d build/test-classes src/test/java/masr/delivery/*.java
java -cp build/classes:build/test-classes masr.delivery.PlatformTest
