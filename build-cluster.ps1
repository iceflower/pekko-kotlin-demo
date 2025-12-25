# Pekko Cluster Modules Build Script
# Usage: .\build-cluster.ps1 [build|test|clean]

param(
    [string]$action = "build"
)

$env:GRADLE_USER_HOME = "C:\gradle-home"

switch ($action) {
    "build" {
        Write-Host "Building spring-boot-cluster and quarkus-cluster modules..." -ForegroundColor Cyan
        .\gradlew.bat :spring-boot-cluster:build :quarkus-cluster:build --no-daemon
    }
    "test" {
        Write-Host "Running tests for cluster modules..." -ForegroundColor Cyan
        .\gradlew.bat :spring-boot-cluster:test :quarkus-cluster:test --no-daemon
    }
    "clean" {
        Write-Host "Cleaning cluster modules..." -ForegroundColor Cyan
        .\gradlew.bat :spring-boot-cluster:clean :quarkus-cluster:clean --no-daemon
    }
    default {
        Write-Host "Unknown action: $action" -ForegroundColor Red
        Write-Host "Usage: .\build-cluster.ps1 [build|test|clean]"
    }
}
