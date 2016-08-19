#!/bin/bash

cd ~/Apps/Cluckburg/
javac -classpath .:classes:/opt/pi4j/lib/'*' -d . CluckburgControl.java
sudo java -classpath .:classes:/opt/pi4j/lib/'*' CluckburgControljava p