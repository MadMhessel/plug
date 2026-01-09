$ErrorActionPreference = "Stop"
Write-Host "Building GraveMarket (skeleton)..." -ForegroundColor Cyan
& .\mvnw.cmd -DskipTests package
Write-Host "Done. JAR -> target\gravemarket-0.1.0-SNAPSHOT.jar" -ForegroundColor Green
