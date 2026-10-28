@echo off
rem Build the DpMt (double precision, multithreaded) Windows DLL from a Libbulletjme source tree.
rem
rem Usage: build-libbulletjme-dpmt.bat <libbulletjme-source-root> <output-dir>
rem
rem Requires: Visual Studio 2022 with the C++ workload, a JDK 21 pointed to by JAVA_HOME.
rem The Libbulletjme Gradle build (gradle cpp plugin) needs vswhere.exe or the VS registry
rem entries to find the toolchain; this script drives cl.exe through VsDevCmd instead, so it
rem also works on machines where that detection fails.

setlocal
if "%~1"=="" (
  echo usage: %~nx0 ^<libbulletjme-source-root^> ^<output-dir^>
  exit /b 2
)
set "SRCROOT=%~1"
for %%I in ("%~1") do set "SRCROOT=%%~fI"
if "%~2"=="" ( set "OUT=%CD%\out" ) else ( for %%I in ("%~2") do set "OUT=%%~fI" )
set "NATIVE=%SRCROOT%\src\main\native"

if not defined JAVA_HOME (
  echo JAVA_HOME is not set
  exit /b 2
)
if not exist "%NATIVE%\bullet3" (
  echo no bullet3 sources under "%NATIVE%"
  exit /b 2
)

call "C:\Program Files\Microsoft Visual Studio\2022\Community\Common7\Tools\VsDevCmd.bat" -arch=x64 -no_logo >nul
if errorlevel 1 (
  echo VsDevCmd failed
  exit /b 1
)

if not exist "%OUT%" mkdir "%OUT%"
if not exist "%OUT%\obj" mkdir "%OUT%\obj"
if not exist "%OUT%\jni" mkdir "%OUT%\jni"

dir /s /b "%NATIVE%\bullet3\*.cpp" > "%OUT%\cpp.txt"
dir /s /b "%NATIVE%\glue\*.cpp" >> "%OUT%\cpp.txt"
dir /s /b "%NATIVE%\v-hacd\src\*.cpp" >> "%OUT%\cpp.txt"
dir /s /b "%SRCROOT%\src\main\java\*.java" > "%OUT%\java.txt"
dir /s /b "%SRCROOT%\buildSrc\src\main\java\*.java" >> "%OUT%\java.txt"

echo === javac -h ===
"%JAVA_HOME%\bin\javac" -encoding UTF-8 -h "%OUT%\jni" -d "%OUT%\classes" @"%OUT%\java.txt"
if errorlevel 1 (
  echo javac failed
  exit /b 1
)

echo === cl ===
rem /MT: static CRT, matching the prebuilt Libbulletjme DLLs (they import no MSVCP140)
cl /nologo /c /MP /EHsc /MT /O2 /Ob3 /openmp /DWIN32 /DNDEBUG /DBT_USE_DOUBLE_PRECISION /DBT_THREADSAFE /DBT_USE_OPENMP /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" /I"%NATIVE%\bullet3" /I"%NATIVE%\bullet3\BulletDynamics\Featherstone" /I"%NATIVE%\bullet3\LinearMath" /I"%NATIVE%\v-hacd\inc" /I"%NATIVE%\v-hacd\public" /I"%OUT%\jni" /Fo%OUT%\obj\ /Fd%OUT%\obj\ @"%OUT%\cpp.txt"
if errorlevel 1 (
  echo cl failed
  exit /b 1
)

echo === link ===
link /nologo /DLL /OUT:"%OUT%\bulletjme.dll" "%OUT%\obj\*.obj" ws2_32.lib
if errorlevel 1 (
  echo link failed
  exit /b 1
)

echo === done: %OUT%\bulletjme.dll ===
