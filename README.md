# Fidesmo Android Tutorial

1. [Description](#description)
2. [Usage](#usage)
4. [Screenshots](#screenshots)

# Description
This repository contains a simple Android app written for the Android NFC
tutorial at
[Fidesmo Developer Portal](https://developer.fidesmo.com/tutorials/android).
Extensive information about how this app works can be found in that tutorial.
On the other hand this app can be tested, installed and run as any typical
Android application.

# Usage
This project can be built and ran on an Android device like any typical Android
app.

## Build instructions using command line
- Clone this repository
- In the project's root directory, type `./gradlew build`
- To install the app onto a connected phone or an emulator, type
`./gradlew installDebug` or
`adb install build/apk/android-service-delivery-demo-debug-unaligned.apk`

## Build instructions using Android Studio IDE
- Clone this repository
- In Android Studio init window choose `Import project`, or if Android Studio
main window is already open go to menu `File -> Import Project`.
- In the dialog box `Select Gradle Project Import`, find this project's root
directory in your filesystem and click `OK`
- To install the app onto a connected phone or an emulator, press the `install`
button.
