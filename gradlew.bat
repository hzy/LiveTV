@rem Gradle startup script for Windows
@if "%OS%"=="Windows_NT" setlocal
set DEFAULT_JVM_OPTS="-Xmx64m" "-Xms64m"
set CLASSPATH=%~dp0gradle\wrapper\gradle-wrapper.jar
java %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
