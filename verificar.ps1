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
$csv = Join-Path $raiz "backend\target\site\jacoco\jacoco.csv"
if (Test-Path $csv) {
    $filas = Import-Csv $csv
    $cubiertas = ($filas | Measure-Object -Property LINE_COVERED -Sum).Sum
    $perdidas = ($filas | Measure-Object -Property LINE_MISSED -Sum).Sum
    $porcentaje = [math]::Round(100 * $cubiertas / ($cubiertas + $perdidas), 1)
    $resumen = "Cobertura JaCoCo de lineas: $porcentaje % ($cubiertas de $($cubiertas + $perdidas))"
    Write-Host $resumen
    Add-Content -Path $archivo -Value $resumen
    foreach ($paquete in ($filas | Group-Object PACKAGE)) {
        $c = ($paquete.Group | Measure-Object -Property LINE_COVERED -Sum).Sum
        $m = ($paquete.Group | Measure-Object -Property LINE_MISSED -Sum).Sum
        $linea = "  {0}: {1} %" -f $paquete.Name, [math]::Round(100 * $c / [math]::Max(1, $c + $m), 1)
        Write-Host $linea
        Add-Content -Path $archivo -Value $linea
    }
}
Write-Host "`nResultado guardado en: $archivo"
