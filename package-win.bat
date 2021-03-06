:: 清空文件夹
rm -rf out/Launcher
rm -rf out/Main
rm -rf out/RubberTranslator

:: Launcher package
jpackage --name Launcher --input out/artifacts/Launcher_jar  --main-jar Launcher.jar  ^
  --dest out ^
  --type app-image ^
  --module-path "C:\Program Files\Java\javafx-sdk-11.0.2\jmods" ^
  --main-class Launcher ^
  --add-modules javafx.controls,javafx.graphics,javafx.fxml ^
  --vendor raven

:: Main package
jpackage --name Main --input out/artifacts/Main_jar  --main-jar Main.jar  ^
  --dest out ^
  --type app-image ^
  --module-path "C:\Program Files\Java\javafx-sdk-11.0.2\jmods" ^
  --main-class com.rubbertranslator.App ^
  --add-modules javafx.controls,javafx.graphics,javafx.fxml ^
  --vendor raven

:: 移动
mv out/Launcher/app/* out/Main/app
mv out/Launcher/Launcher* out/Main

:: 改名
mv out/Main out/RubberTranslator