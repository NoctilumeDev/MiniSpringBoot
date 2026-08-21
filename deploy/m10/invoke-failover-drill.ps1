param(
    [int]$Concurrency = 24,
    [int]$WarmupSeconds = 5,
    [int]$DurationSeconds = 30,
    [int]$KillAfterSeconds = 10,
    [string]$TargetInstance = 'msb-2',
    [string]$EvidenceDirectory
)

. (Join-Path $PSScriptRoot 'cluster-common.ps1')

function Get-AccountSnapshot {
    $account1 = Invoke-RestMethod -Uri 'http://127.0.0.1:9080/api/accounts/1' -TimeoutSec 5
    $account2 = Invoke-RestMethod -Uri 'http://127.0.0.1:9080/api/accounts/2' -TimeoutSec 5
    return [ordered]@{
        account1 = [ordered]@{ id = [long]$account1.id; balance = [decimal]$account1.balance }
        account2 = [ordered]@{ id = [long]$account2.id; balance = [decimal]$account2.balance }
    }
}

function Test-AccountSnapshotsEqual([object]$Before, [object]$After) {
    return [long]$Before.account1.id -eq [long]$After.account1.id -and
        [decimal]$Before.account1.balance -eq [decimal]$After.account1.balance -and
        [long]$Before.account2.id -eq [long]$After.account2.id -and
        [decimal]$Before.account2.balance -eq [decimal]$After.account2.balance
}

if ($Concurrency -lt 1 -or $WarmupSeconds -lt 0 -or $DurationSeconds -lt 10 -or
    $KillAfterSeconds -lt 1 -or $KillAfterSeconds -ge ($WarmupSeconds + $DurationSeconds - 3)) {
    throw '故障演练参数非法：并发需 >=1，持续时间 >=10s，故障时点必须落在压测结束至少 3s 之前。'
}

$state = Get-M10State
if ($null -eq $state) { throw 'M10 集群未启动。' }
$target = $state.instances | Where-Object { $_.id -eq $TargetInstance } | Select-Object -First 1
if ($null -eq $target) { throw "运行状态中不存在实例 $TargetInstance。" }
Get-M10DemoProcess -ProcessId ([int]$target.pid) -Port ([int]$target.port) | Out-Null

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $evidenceDirectory = Join-Path $script:RuntimeDirectory 'evidence'
} elseif ([System.IO.Path]::IsPathRooted($EvidenceDirectory)) {
    $evidenceDirectory = [System.IO.Path]::GetFullPath($EvidenceDirectory)
} else {
    $evidenceDirectory = [System.IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $EvidenceDirectory))
}
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$loadStdout = Join-Path $evidenceDirectory "$stamp-failover-load.out.log"
$loadStderr = Join-Path $evidenceDirectory "$stamp-failover-load.err.log"
$nodeScript = Join-Path $PSScriptRoot 'load\bounded-load.mjs'
$baseline = Get-AccountSnapshot
$timeline = [System.Collections.Generic.List[object]]::new()
$replacement = $null
$load = $null

try {
    $arguments = @(
        $nodeScript,
        "--stages=$Concurrency",
        "--warmup=$WarmupSeconds",
        "--duration=$DurationSeconds",
        '--rest=0',
        '--label=failover',
        "--output-dir=$evidenceDirectory"
    )
    $load = Start-Process -FilePath 'node' -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $loadStdout -RedirectStandardError $loadStderr
    $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'LOAD_STARTED'; pid = $load.Id; concurrency = $Concurrency })

    Start-Sleep -Seconds $KillAfterSeconds
    $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'INSTANCE_STOP_REQUESTED'; instance = $target.id; pid = $target.pid })
    Stop-M10Instance $target | Out-Null
    $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'INSTANCE_STOPPED'; instance = $target.id })

    $proxyChecks = 0
    $proxyFailures = 0
    $checkDeadline = (Get-Date).AddSeconds(5)
    do {
        $proxyChecks += 1
        try {
            $response = Invoke-RestMethod -Uri 'http://127.0.0.1:9080/health' -TimeoutSec 2
            $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'PROXY_OK_DURING_OUTAGE'; instance = $response.instance })
        } catch {
            $proxyFailures += 1
            $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'PROXY_FAILURE_DURING_OUTAGE'; message = $_.Exception.Message })
        }
        Start-Sleep -Milliseconds 250
    } while ((Get-Date) -lt $checkDeadline)

    $replacement = Start-M10Instance -InstanceId ([string]$target.id) -Port ([int]$target.port)
    $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'INSTANCE_RESTARTED'; instance = $replacement.id; pid = $replacement.pid })

    $rejoined = $false
    $rejoinDeadline = (Get-Date).AddSeconds(15)
    do {
        $response = Invoke-RestMethod -Uri 'http://127.0.0.1:9080/health' -TimeoutSec 2
        if ($response.instance -eq $TargetInstance) {
            $rejoined = $true
            $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'INSTANCE_REJOIN_OBSERVED'; instance = $response.instance })
            break
        }
        Start-Sleep -Milliseconds 200
    } while ((Get-Date) -lt $rejoinDeadline)
    if (-not $rejoined) { throw "$TargetInstance 恢复后 15s 内未重新收到 Nginx 流量。" }

    $load.WaitForExit()
    $load.Refresh()
    if ($load.ExitCode -ne 0) {
        throw "故障窗口压测器退出码 $($load.ExitCode)。详见 $loadStdout / $loadStderr"
    }

    $after = Get-AccountSnapshot
    if (-not (Test-AccountSnapshotsEqual -Before $baseline -After $after)) {
        throw '只读故障演练前后账户快照发生变化。'
    }

    $updatedInstances = @($state.instances | Where-Object { $_.id -ne $TargetInstance }) + @([pscustomobject]$replacement)
    $state.instances = @($updatedInstances | Sort-Object port)
    Save-M10State $state

    $summary = [ordered]@{
        schemaVersion = 1
        startedAt = $timeline[0].at
        finishedAt = (Get-Date).ToString('o')
        targetInstance = $TargetInstance
        oldPid = $target.pid
        newPid = $replacement.pid
        proxyChecksDuringOutage = $proxyChecks
        proxyFailuresDuringOutage = $proxyFailures
        rejoined = $rejoined
        accountsBefore = $baseline
        accountsAfter = $after
        timeline = $timeline
        loadStdout = Split-Path -Leaf $loadStdout
        loadStderr = Split-Path -Leaf $loadStderr
    }
    $summaryPath = Join-Path $evidenceDirectory "$stamp-failover-summary.json"
    $summary | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $summaryPath -Encoding utf8
    Write-Host "[OK] $TargetInstance 故障切换闭环；窗口检查 $proxyChecks 次，失败 $proxyFailures 次，新 PID $($replacement.pid)。"
    Write-Host "[OK] 账户快照未变；证据：$summaryPath"
} catch {
    if ($null -ne $load -and -not $load.HasExited) {
        $loadProcess = Get-CimInstance Win32_Process -Filter "ProcessId = $($load.Id)" -ErrorAction SilentlyContinue
        if ($null -ne $loadProcess -and ([string]$loadProcess.CommandLine).Contains('bounded-load.mjs')) {
            Stop-Process -Id $load.Id
        }
    }
    if ($null -eq $replacement -and (Test-M10PortFree ([int]$target.port))) {
        try {
            $replacement = Start-M10Instance -InstanceId ([string]$target.id) -Port ([int]$target.port)
            $updatedInstances = @($state.instances | Where-Object { $_.id -ne $TargetInstance }) + @([pscustomobject]$replacement)
            $state.instances = @($updatedInstances | Sort-Object port)
            Save-M10State $state
            Write-Warning "$TargetInstance 已在失败清理中恢复为 PID $($replacement.pid)。"
        } catch {
            Write-Warning "未能自动恢复 $TargetInstance：$($_.Exception.Message)"
        }
    }
    if ($null -ne $replacement) {
        $updatedInstances = @($state.instances | Where-Object { $_.id -ne $TargetInstance }) + @([pscustomobject]$replacement)
        $state.instances = @($updatedInstances | Sort-Object port)
        Save-M10State $state
    }
    throw
}
