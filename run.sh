#!/usr/bin/env bash
set -e

DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" >/dev/null 2>&1 && pwd)"
cd "$DIR"

JAVA_BIN=$(which java 2>/dev/null || echo "/usr/local/buildtools/java/jdk/bin/java")

if [ "$1" == "test" ]; then
    "$JAVA_BIN" -cp bin src.TestRunner
elif [ "$1" == "cli" ]; then
    "$JAVA_BIN" -cp bin src.App
elif [ "$1" == "benchmark" ]; then
    "$JAVA_BIN" -cp bin src.App --benchmark
else
    "$JAVA_BIN" -jar solari-assistant.jar "$@"
fi
