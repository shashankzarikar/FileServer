@echo off
echo Starting FileServer Client...
java --module-path "%USERPROFILE%\.m2\repository\org\openjfx\javafx-controls\17.0.6;%USERPROFILE%\.m2\repository\org\openjfx\javafx-graphics\17.0.6;%USERPROFILE%\.m2\repository\org\openjfx\javafx-base\17.0.6;%USERPROFILE%\.m2\repository\org\openjfx\javafx-fxml\17.0.6" --add-modules javafx.controls,javafx.fxml -jar target\FileServer-client.jar
pause