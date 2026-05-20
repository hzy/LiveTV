#!/bin/sh
#
# Gradle start up script for POSIX
#

APP_NAME="Gradle"
APP_BASE_NAME="${0##*/}"
DEFAULT_JVM_OPTS='"-Xmx64m" "-Xms64m"'

APP_HOME=$( cd "${0%"${0##*/}"}" && pwd -P ) || exit

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java $DEFAULT_JVM_OPTS $JAVA_OPTS $GRADLE_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
