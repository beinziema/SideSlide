@ECHO OFF
SETLOCAL
SET DIR=%~dp0
IF "%JAVA_HOME%"=="" (
  SET JAVACMD=java.exe
) ELSE (
  SET JAVACMD=%JAVA_HOME%\bin\java.exe
)
"%JAVACMD%" -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
ENDLOCAL