# Windows: liga o servidor local do rotulador FactNews e a ponte ADB para o emulador/aparelho.
# Uso (na pasta lume\ml\factnews):  .\scripts\start-factnews-server.ps1   [-Port 8765] [-NoAdb]
param([int]$Port = 8765, [switch]$NoAdb)
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$py = Join-Path $root '.venv\Scripts\python.exe'
if (-not (Test-Path $py)) { throw "Ambiente Python nao encontrado em $py. Siga o README (secao 'Como reproduzir')." }
if (-not (Test-Path (Join-Path $root 'models\factnews-v2\factnews_config.json'))) {
    throw "Pesos do modelo nao encontrados em models\factnews-v2. Baixe-os (README, secao 'Usar o modelo treinado') ou treine: scripts\04_context_ensemble.py --export --use-test --ctx none --seeds 42 --tag factnews-v2"
}
if (-not $NoAdb) {
    $adb = (Get-Command adb -ErrorAction SilentlyContinue).Source
    if (-not $adb) { $adb = Join-Path $env:LOCALAPPDATA 'Android\Sdk\platform-tools\adb.exe' }
    if (Test-Path $adb) {
        & $adb reverse "tcp:$Port" "tcp:$Port" 2>$null | Out-Null
        if ($LASTEXITCODE -eq 0) { Write-Host "Ponte ADB pronta (tcp:$Port)." } else { Write-Host "Aviso: nenhum emulador/aparelho no ADB; rode de novo depois de abri-lo (ou use -NoAdb)." }
    } else { Write-Host "Aviso: adb nao encontrado; pulei a ponte." }
}
$env:PYTHONIOENCODING = 'utf-8'
& $py (Join-Path $root 'scripts\serve.py') --port $Port
