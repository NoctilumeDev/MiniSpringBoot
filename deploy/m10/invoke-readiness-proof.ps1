param(
    [string]$BaseUri = 'http://127.0.0.1:9080',
    [string]$ContainerName = 'minispring-mysql',
    [string]$EvidenceDirectory
)

. (Join-Path $PSScriptRoot 'cluster-common.ps1')

if ([string]::IsNullOrWhiteSpace($EvidenceDirectory)) {
    $evidenceDirectory = Join-Path $script:RuntimeDirectory 'evidence'
} elseif ([System.IO.Path]::IsPathRooted($EvidenceDirectory)) {
    $evidenceDirectory = [System.IO.Path]::GetFullPath($EvidenceDirectory)
} else {
    $evidenceDirectory = [System.IO.Path]::GetFullPath((Join-Path $script:RepositoryRoot $EvidenceDirectory))
}
New-Item -ItemType Directory -Force -Path $evidenceDirectory | Out-Null

function Get-ApiAccountSnapshot {
    $account1 = Invoke-RestMethod -Uri "$BaseUri/api/accounts/1" -TimeoutSec 5
    $account2 = Invoke-RestMethod -Uri "$BaseUri/api/accounts/2" -TimeoutSec 5
    return [ordered]@{
        account1 = [ordered]@{ id = [long]$account1.id; balance = [decimal]$account1.balance }
        account2 = [ordered]@{ id = [long]$account2.id; balance = [decimal]$account2.balance }
    }
}

function Test-AccountSnapshotsEqual([object]$Left, [object]$Right) {
    return [long]$Left.account1.id -eq [long]$Right.account1.id -and
        [decimal]$Left.account1.balance -eq [decimal]$Right.account1.balance -and
        [long]$Left.account2.id -eq [long]$Right.account2.id -and
        [decimal]$Left.account2.balance -eq [decimal]$Right.account2.balance
}

function Get-HttpFailure([string]$Uri, [int]$TimeoutSec) {
    $watch = [System.Diagnostics.Stopwatch]::StartNew()
    try {
        $unexpected = Invoke-WebRequest -Uri $Uri -TimeoutSec $TimeoutSec
        $watch.Stop()
        return [ordered]@{
            status = [int]$unexpected.StatusCode
            elapsedMs = [long]$watch.ElapsedMilliseconds
            body = [string]$unexpected.Content
        }
    } catch {
        $watch.Stop()
        if ($null -eq $_.Exception.Response) { throw }
        return [ordered]@{
            status = [int]$_.Exception.Response.StatusCode
            elapsedMs = [long]$watch.ElapsedMilliseconds
            body = $_.ErrorDetails.Message
        }
    }
}

$containerState = docker inspect --format '{{.State.Status}}/{{if .State.Health}}{{.State.Health.Status}}{{end}}' $ContainerName 2>$null
if ($LASTEXITCODE -ne 0 -or $containerState -ne 'running/healthy') {
    throw "$ContainerName 必须在演练前处于 running/healthy，实际为 $containerState。"
}

$baseline = Get-ApiAccountSnapshot
$stateBefore = Get-M10State
$timeline = [System.Collections.Generic.List[object]]::new()
$proofError = $null
$liveDuringOutage = $null
$readyDuringOutage = $null
$readyAfterRecovery = $null
$after = $null

try {
    docker stop $ContainerName | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "停止 $ContainerName 失败。" }
    $timeline.Add([ordered]@{ at = (Get-Date).ToString('o'); event = 'MYSQL_STOPPED' })

    $liveWatch = [System.Diagnostics.Stopwatch]::StartNew()
    $liveDuringOutage = Invoke-RestMethod -Uri "$BaseUri/health/live" -TimeoutSec 5
    $liveWatch.Stop()
    if ($liveDuringOutage.status -ne 'UP') { throw 'MySQL 下线期间进程存活探针未返回 UP。' }
    $timeline.Add([ordered]@{
        at = (Get-Date).ToString('o')
        event = 'LIVENESS_REMAINED_UP'
        elapsedMs = [long]$liveWatch.ElapsedMilliseconds
        response = $liveDuringOutage
    })

    $readyDuringOutage = Get-HttpFailure -Uri "$BaseUri/health" -TimeoutSec 45
    if ($readyDuringOutage.status -ne 500) {
        throw "MySQL 下线期间就绪探针应返回 HTTP 500，实际为 $($readyDuringOutage.status)。"
    }
    $timeline.Add([ordered]@{
        at = (Get-Date).ToString('o')
        event = 'READINESS_FAILED_CLOSED'
        response = $readyDuringOutage
    })
} catch {
    $proofError = $_
} finally {
    $running = docker inspect --format '{{.State.Running}}' $ContainerName 2>$null
    if ($LASTEXITCODE -eq 0 -and $running -ne 'true') {
        docker start $ContainerName | Out-Null
        if ($LASTEXITCODE -ne 0 -and $null -eq $proofError) {
            $proofError = [System.Management.Automation.ErrorRecord]::new(
                [System.InvalidOperationException]::new("无法重新启动 $ContainerName。"),
                'M10MysqlRestartFailed',
                [System.Management.Automation.ErrorCategory]::ResourceUnavailable,
                $ContainerName
            )
        }
    }

    $healthy = $false
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $health = docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{end}}' $ContainerName 2>$null
        if ($LASTEXITCODE -eq 0 -and $health -eq 'healthy') { $healthy = $true; break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)

    if (-not $healthy) {
        if ($null -eq $proofError) {
            $proofError = [System.Management.Automation.ErrorRecord]::new(
                [System.TimeoutException]::new("$ContainerName 在 90 秒内未恢复 healthy。"),
                'M10MysqlRecoveryTimeout',
                [System.Management.Automation.ErrorCategory]::OperationTimeout,
                $ContainerName
            )
        }
    } else {
        try {
            $readyDeadline = (Get-Date).AddSeconds(45)
            do {
                try {
                    $readyAfterRecovery = Invoke-RestMethod -Uri "$BaseUri/health" -TimeoutSec 5
                    if ($readyAfterRecovery.status -eq 'UP' -and $readyAfterRecovery.database -eq 'UP') { break }
                } catch {}
                Start-Sleep -Seconds 1
            } while ((Get-Date) -lt $readyDeadline)
            if ($null -eq $readyAfterRecovery -or $readyAfterRecovery.database -ne 'UP') {
                throw 'MySQL 恢复后就绪探针未在 45 秒内恢复 UP/UP。'
            }
            $after = Get-ApiAccountSnapshot
            if (-not (Test-AccountSnapshotsEqual -Left $baseline -Right $after)) {
                throw 'MySQL 故障演练前后账户快照发生变化。'
            }
            $stateAfter = Get-M10State
            if (($stateBefore.instances.pid -join ',') -ne ($stateAfter.instances.pid -join ',')) {
                throw 'MySQL 故障演练意外重启了应用实例。'
            }
            $timeline.Add([ordered]@{
                at = (Get-Date).ToString('o')
                event = 'READINESS_RECOVERED'
                response = $readyAfterRecovery
                accounts = $after
            })
        } catch {
            if ($null -eq $proofError) { $proofError = $_ }
        }
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$result = [ordered]@{
    schemaVersion = 1
    capturedAt = (Get-Date).ToString('o')
    baseUri = $BaseUri
    container = $ContainerName
    passed = $null -eq $proofError
    baselineAccounts = $baseline
    livenessDuringOutage = $liveDuringOutage
    readinessDuringOutage = $readyDuringOutage
    readinessAfterRecovery = $readyAfterRecovery
    accountsAfterRecovery = $after
    timeline = $timeline
    error = if ($null -eq $proofError) { $null } else { $proofError.Exception.Message }
}
$resultPath = Join-Path $evidenceDirectory "$stamp-readiness-proof.json"
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resultPath -Encoding utf8

if ($null -ne $proofError) { throw $proofError }
Write-Host "[OK] MySQL 下线期间 live=UP、ready=500；恢复后 ready=UP/UP，账户快照未变。"
Write-Host "[OK] 证据：$resultPath"
