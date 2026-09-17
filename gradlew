#!/bin/sh

# Minimal POSIX Gradle wrapper launcher (offline-friendly).
# Delegates to the Gradle wrapper jar; the distribution itself is resolved
# from gradle/wrapper/gradle-wrapper.properties (cached in GRADLE_USER_HOME).

APP_HOME=$(cd "$(dirname "$0")" && pwd)

if [ -n "$JAVA_HOME" ]; then
    JAVACMD="$JAVA_HOME/bin/java"
else
    JAVACMD="java"
fi

CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"

exec "$JAVACMD" \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
