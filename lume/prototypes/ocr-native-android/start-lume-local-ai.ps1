param(
    [string]$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe",
    [string]$Ollama = "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe"
)

$ErrorActionPreference = 'Stop'
if (-not (Test-Path -LiteralPath $Adb)) { throw "ADB não encontrado em $Adb" }
if (-not (Test-Path -LiteralPath $Ollama)) { throw "Ollama não encontrado em $Ollama" }

try { Invoke-RestMethod -Uri 'http://127.0.0.1:11434/api/tags' -TimeoutSec 2 | Out-Null }
catch {
    Start-Process -FilePath $Ollama -ArgumentList 'serve' -WindowStyle Hidden
    Start-Sleep -Seconds 3
    Invoke-RestMethod -Uri 'http://127.0.0.1:11434/api/tags' -TimeoutSec 5 | Out-Null
}

# Comparador de sentido multilíngue usado pelo app (só ordena fontes e compara o assunto; não decide sozinho).
# O Qwen3.5-4B deixou de ser usado: nos testes com pares reais aceitou fatos diferentes e inventou trechos.
$installed = & $Ollama list
if ($LASTEXITCODE -ne 0 -or -not ($installed | Select-String 'paraphrase-multilingual')) {
    Write-Host 'Baixando o modelo local gratuito paraphrase-multilingual (aprox. 560 MB)...'
    & $Ollama pull paraphrase-multilingual
    if ($LASTEXITCODE -ne 0) { throw 'Não foi possível baixar o modelo.' }
}

& $Adb reverse tcp:11434 tcp:11434
if ($LASTEXITCODE -ne 0) { throw 'Conecte o emulador ou celular via ADB e tente novamente.' }
Write-Host 'IA local do Lume pronta. Mantenha o Ollama aberto durante a demonstração.'
