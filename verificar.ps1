# Verifica el proyecto: trae lo ultimo de GitHub, compila y corre todos los tests del backend.
# Uso (PowerShell, desde la raiz del repositorio):  .\verificar.ps1
# El resultado completo queda en resultado-tests.txt (en la carpeta DISE*OOO de Descargas si existe).
$raiz = $PSScriptRoot
Set-Location $raiz
git pull --ff-only
Set-Location (Join-Path $raiz "backend")
$destino = Get-ChildItem "$HOME\Downloads" -Directory -Filter 'DISE*OOO' -ErrorAction SilentlyContinue | Select-Object -First 1
$archivo = if ($destino) { Join-Path $destino.FullName "resultado-tests.txt" } else { Join-Path $raiz "resultado-tests.txt" }
mvn -B test 2>&1 | Tee-Object -FilePath $archivo
Write-Host "`nResultado guardado en: $archivo"
