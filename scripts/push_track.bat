@echo off
set /p VIDEO_ID="Inserisci il YouTube Video ID: "
python "%~dp0push_track.py" %VIDEO_ID%
pause
