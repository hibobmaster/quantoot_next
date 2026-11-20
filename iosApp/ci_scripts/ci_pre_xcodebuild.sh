#!/bin/bash
set -euo pipefail

case "${CI_XCODEBUILD_ACTION:-}" in
    build|archive)
        compile_task=:shared:compileKotlinIosArm64
        ;;
    build-for-testing)
        compile_task=:shared:compileKotlinIosSimulatorArm64
        ;;
    *)
        exit 0
        ;;
esac

cd "$(dirname "$0")"
source ./java-home.sh
JAVA_HOME=$(quantoot_java_home)
export JAVA_HOME

# Resolve and compile Kotlin dependencies before Xcode builds the native app.
cd ../..
exec ./gradlew "$compile_task"
