@echo off
setlocal
if defined JAVA_HOME (set "JAVA=%JAVA_HOME%\bin\java.exe") else (set "JAVA=java")
"%JAVA%" "%~dp0tools\GradleBootstrap.java" "%~dp0." %*
exit /b %ERRORLEVEL%
