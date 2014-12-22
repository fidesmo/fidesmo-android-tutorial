fidesmo-android-tutorial
========================

This repository contains a simple Android app written for the Android NFC tutorial at [Fidesmo Developer Portal](https://developer.fidesmo.com/tutorials/android)

Build instructions: command line
------------------
- Clone this repository
- In the project's root directory, type ``./gradlew build``
- To install the app into a connected phone or an emulator, type ``./gradlew installDebug`` or ``adb install build/apk/android-service-delivery-demo-debug-unaligned.apk``

Build instructions: Android Studio IDE
------------------
- Clone this repository
- In Android Studio, go to menu File -> Import Project
- In the dialog box "Select Gradle Project Import", find this project's root directory in your filesystem and click 'OK'

Screenshots
---------
![Screenshot showing the app's startup screen](/docs/Initial_Screenshot.png "Screenshot showing the app's startup screen")
![Screenshot showing the app's final screen](/docs/Final_Screenshot.png "Screenshot showing the app's final screen")
