# APiX Movies Engine

This directory is the embedded Movies subsystem of APiX TV.

- `MovieEngine/` = complete CloudStream-based Movies UI + Player + Plug engine, isolated as an Android library module.
- `CoreLibrary/` = complete CloudStream API/common Android/JVM library dependency used by Movies/Player/Plug.

The main APiX live-TV app remains outside this module and is not replaced by the Movies player.
