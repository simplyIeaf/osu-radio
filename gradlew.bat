@rem
@rem Gradle startup script for Windows
@rem
@echo off

set APP_HOME=%~dp0

set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

set JAVA_EXE=java.exe

set CMD_LINE_ARGS=
set _SKIP=2

:setupArgs
if "x%~1" == "x" goto execute
set CMD_LINE_ARGS=%*
goto execute

:execute
@rem Setup the command line
set JAVA_OPTS="-Xmx64m" "-Xms64m"

"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %GRADLE_OPTS% "-Dorg.gradle.appname=%APP_BASE_NAME%" -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %CMD_LINE_ARGS%

:end
