#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
mkdir -p build/classes dist
java -m jdk.compiler/com.sun.tools.javac.Main --release 17 -encoding UTF-8 -d build/classes src/main/java/masr/delivery/*.java
cp src/main/resources/* build/classes/
java -m jdk.jartool/sun.tools.jar.Main --create --file dist/masr-delivery.jar --main-class masr.delivery.Main -C build/classes .
printf '%s\n' 'Built dist/masr-delivery.jar'
