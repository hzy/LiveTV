#!/bin/sh

#
# Gradle start up script for POSIX
#

APP_NAME="Gradle"
APP_BASE_NAME="${0##*/}"

APP_HOME=$( cd "${0%"${0##*/}"}" && pwd -P ) || exit

CLASSPATH=$APP_HOME/gradle/wrapper/gradle-wrapper.jar

exec java $JAVA_OPTS $GRADLE_OPTS \
    -classpath "$CLASSPATH" \
    org.gradle.wrapper.GradleWrapperMain "$@"
