. (Join-Path $PSScriptRoot 'cluster-common.ps1')

$state = Get-M10State
if ($null -eq $state) {
    Write-Host '[DOWN] 未找到 M10 运行状态。'
    exit 1
}

$rows = foreach ($instance in $state.instances) {
    $process = Get-M10DemoProcess -ProcessId ([int]$instance.pid) -Port ([int]$instance.port)
    $ready = $null
    try { $ready = Invoke-RestMethod -Uri "http://127.0.0.1:$($instance.port)/health" -TimeoutSec 2 } catch {}
    [pscustomobject]@{
        Instance = $instance.id
        Port = $instance.port
        PID = $instance.pid
        Process = if ($null -eq $process) { 'DOWN' } else { 'UP' }
        Ready = if ($null -eq $ready) { 'DOWN' } else { "$($ready.status)/$($ready.database)" }
    }
}
$rows | Format-Table -AutoSize

$proxy = 'DOWN'
try {
    $proxyReady = Invoke-RestMethod -Uri 'http://127.0.0.1:9080/health' -TimeoutSec 2
    $proxy = "UP -> $($proxyReady.instance)"
} catch {}
Write-Host "Nginx :9080  $proxy"
Get-M10ResourceSnapshot | ConvertTo-Json
