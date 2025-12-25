#!/bin/bash
# Pekko Cluster Modules Build Script for WSL
# This script calls PowerShell to handle Gradle correctly
# Usage: ./build-cluster.sh [build|test|clean]

ACTION=${1:-build}
PROJECT_DIR="C:\\Users\\pekko-kotlin-demo"

powershell.exe -NoProfile -Command "\$env:GRADLE_USER_HOME='C:\\gradle-home'; Set-Location '$PROJECT_DIR'; .\gradlew.bat :spring-boot-cluster:$ACTION :quarkus-cluster:$ACTION --no-daemon"
