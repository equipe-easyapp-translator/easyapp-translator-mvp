@echo off
setlocal
set "APP_HOME=%~dp0"
set "CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar"

if not exist "%CLASSPATH%" (
  echo Gradle Wrapper JAR not found: "%CLASSPATH%" 1>&2
  exit /b 1
)

java -classpath "%CLASSPATH%" org.gradle.wrapper.GradleWrapperMain %*
exit /b %ERRORLEVEL%
