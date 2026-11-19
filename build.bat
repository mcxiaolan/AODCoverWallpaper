@echo off
rem Build the Xposed module APK with Android SDK tools only (no Gradle).
rem Module type: libxposed Modern API 102 (LSPosed 2.2.x).
rem Output: build\AODCoverWallpaper.apk
setlocal

set WS=%~dp0
set SDK=%WS%tools\sdk
set JDK=%WS%tools\jdk17\jdk-17.0.21+9
set BT=%SDK%\build-tools\34.0.0
set PLATFORM=%SDK%\platforms\android-34\android.jar
set XPOSE=%WS%tools\libxposed-ext\classes.jar
set SRC=%WS%module\src\main
set OUT=%WS%build

set JAVA_HOME=%JDK%
set PATH=%JDK%\bin;%PATH%

if not exist "%JDK%\bin\javac.exe" (
  echo [ERROR] JDK not found: %JDK%
  exit /b 1
)
if not exist "%BT%\aapt2.exe" (
  echo [ERROR] build-tools not found: %BT%
  exit /b 1
)
if not exist "%XPOSE%" (
  echo [ERROR] libxposed api jar not found: %XPOSE%
  exit /b 1
)

if exist "%OUT%" rmdir /s /q "%OUT%"
mkdir "%OUT%\classes" "%OUT%\dex" "%OUT%\gen" 2>nul

echo [1/6] aapt2 compile
"%BT%\aapt2.exe" compile --dir "%SRC%\res" -o "%OUT%\res.zip" || exit /b 1

echo [2/6] aapt2 link
"%BT%\aapt2.exe" link -o "%OUT%\base.apk" ^
  -I "%PLATFORM%" ^
  --manifest "%WS%module\AndroidManifest.xml" ^
  --java "%OUT%\gen" ^
  --min-sdk-version 29 --target-sdk-version 34 ^
  "%OUT%\res.zip" || exit /b 1

echo [3/6] javac
dir /s /b "%OUT%\gen\*.java" > "%OUT%\gen-sources.txt"
dir /s /b "%SRC%\java\*.java" > "%OUT%\src-sources.txt"
"%JDK%\bin\javac.exe" -source 17 -target 17 -encoding UTF-8 -nowarn -Xlint:-options ^
  -classpath "%PLATFORM%;%XPOSE%" ^
  -d "%OUT%\classes" ^
  "@%OUT%\gen-sources.txt" "@%OUT%\src-sources.txt" || exit /b 1

echo [4/6] d8
dir /s /b "%OUT%\classes\*.class" > "%OUT%\class-list.txt"
call "%BT%\d8.bat" --min-api 29 --lib "%PLATFORM%" --classpath "%XPOSE%" ^
  --output "%OUT%\dex" "@%OUT%\class-list.txt" || exit /b 1

echo [5/6] package
copy /y "%OUT%\base.apk" "%OUT%\AODCoverWallpaper-unsigned.apk" >nul
pushd "%OUT%\dex"
"%JDK%\bin\jar.exe" uf "%OUT%\AODCoverWallpaper-unsigned.apk" classes.dex || (popd & exit /b 1)
popd
pushd "%SRC%\assets"
"%JDK%\bin\jar.exe" uf "%OUT%\AODCoverWallpaper-unsigned.apk" META-INF/xposed || (popd & exit /b 1)
popd

echo [6/6] sign
if not exist "%WS%tools\keystore.jks" (
  "%JDK%\bin\keytool.exe" -genkeypair -keystore "%WS%tools\keystore.jks" ^
    -storepass android -keypass android -alias aodcoverwallpaper ^
    -keyalg RSA -keysize 2048 -validity 10000 ^
    -dname "CN=AODCoverWallpaper" || exit /b 1
)
"%BT%\zipalign.exe" -f 4 "%OUT%\AODCoverWallpaper-unsigned.apk" "%OUT%\AODCoverWallpaper-aligned.apk" || exit /b 1
call "%BT%\apksigner.bat" sign --ks "%WS%\tools\keystore.jks" ^
  --ks-pass pass:android --key-pass pass:android ^
  --ks-key-alias aodcoverwallpaper ^
  --out "%OUT%\AODCoverWallpaper.apk" "%OUT%\AODCoverWallpaper-aligned.apk" || exit /b 1
call "%BT%\apksigner.bat" verify "%OUT%\AODCoverWallpaper.apk" || exit /b 1

echo.
echo [OK] %OUT%\AODCoverWallpaper.apk
endlocal
