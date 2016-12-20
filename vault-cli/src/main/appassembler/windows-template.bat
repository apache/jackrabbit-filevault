#LICENSE_HEADER#
@REM ----------------------------------------------------------------------------
@REM Vault Start Up Batch script
@REM
@REM Required ENV vars:
@REM JAVA_HOME - location of a JDK home dir
@REM
@REM Optional ENV vars
@REM VLT_BATCH_ECHO - set to 'on' to enable the echoing of the batch commands
@REM VLT_BATCH_PAUSE - set to 'on' to wait for a key stroke before ending
@REM VLT_OPTS - parameters passed to the Java VM when running vlt
@REM     e.g. to debug vlt itself, use
@REM set VLT_OPTS=-Xdebug -Xnoagent -Djava.compiler=NONE -Xrunjdwp:transport=dt_socket,server=y,suspend=y,address=8000
@REM ----------------------------------------------------------------------------

@REM Begin all REM lines with '@' in case VLT_BATCH_ECHO is 'on'
@echo off
@REM enable echoing my setting VLT_BATCH_ECHO to 'on'
@if "%VLT_BATCH_ECHO%" == "on"  echo %VLT_BATCH_ECHO%

@REM set %HOME% to equivalent of $HOME
if "%HOME%" == "" (set HOME=%HOMEDRIVE%%HOMEPATH%)

@REM Execute a user defined script before this one
if exist "%HOME%\vltrc_pre.bat" call "%HOME%\vltrc_pre.bat"

set ERROR_CODE=0

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set CMD_LINE_ARGS=%*
goto WinNTGetScriptDir

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto WinNTGetScriptDir

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto Win9xGetScriptDir
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

:Win9xGetScriptDir
set SAVEDIR=%CD%
%0\
cd %0\..\.. 
set VLT_HOME=%CD%
cd %SAVEDIR%
set SAVE_DIR=
goto repoSetup

:WinNTGetScriptDir
set VLT_HOME=%~dp0\..

:repoSetup
#ENV_SETUP#

if "%JAVACMD%"=="" set JAVACMD=#JAVA_BINARY#

if "%REPO%"=="" set REPO=%VLT_HOME%\#REPO#

set EXTRA_JVM_ARGUMENTS=#EXTRA_JVM_ARGUMENTS#
for /f tokens^=2-5^ delims^=.-_^" %%j in ('%JAVACMD% -fullversion 2^>^&1') do set "jver=%%j%%k%%l%%m"
if %jver% LSS 18000 set EXTRA_JVM_ARGUMENTS=#EXTRA_JVM_ARGUMENTS# -XX:PermSize=128m -XX:-UseGCOverheadLimit

set CLASSPATH=#CLASSPATH#
goto endInit

@REM Reaching here means variables are defined and arguments have been captured
:endInit

%JAVACMD% %VLT_OPTS% %EXTRA_JVM_ARGUMENTS% -classpath %CLASSPATH_PREFIX%;%CLASSPATH% -Dapp.name="#APP_NAME#" -Dapp.repo="%REPO%" -Dapp.home="%VLT_HOME%" -Dvlt.home="%VLT_HOME%" #MAINCLASS# #APP_ARGUMENTS#%CMD_LINE_ARGS%
if ERRORLEVEL 1 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
set ERROR_CODE=%ERRORLEVEL%

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set CMD_LINE_ARGS=
goto postExec

:endNT
@REM If error code is set to 1 then the endlocal was done already in :error.
if %ERROR_CODE% EQU 0 @endlocal


:postExec
if exist "%HOME%\vltrc_post.bat" call "%HOME%\vltrc_post.bat"
@REM pause the batch file if VLT_BATCH_PAUSE is set to 'on'
if "%VLT_BATCH_PAUSE%" == "on" pause


if "%FORCE_EXIT_ON_ERROR%" == "on" (
  if %ERROR_CODE% NEQ 0 exit %ERROR_CODE%
)

exit /B %ERROR_CODE%
