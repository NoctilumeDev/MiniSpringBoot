param(
    [string]$EvidenceDirectory = (Join-Path $PSScriptRoot '..\..\docs\evidence\m10'),
    [string]$SourceCommit
)

$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$evidenceRoot = (Resolve-Path -LiteralPath $EvidenceDirectory).Path
$manifestPath = Join-Path $evidenceRoot 'm10-evidence-manifest.json'

if ([string]::IsNullOrWhiteSpace($SourceCommit)) {
    $SourceCommit = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($SourceCommit)) {
        throw 'Unable to resolve the source commit.'
    }
}

$capacityPath = Get-ChildItem -LiteralPath $evidenceRoot -Filter '*-capacity-baseline.json' |
    Sort-Object Name | Select-Object -Last 1
$failoverPath = Get-ChildItem -LiteralPath $evidenceRoot -Filter '*-failover-summary.json' |
    Sort-Object Name | Select-Object -Last 1
$transactionPath = Get-ChildItem -LiteralPath $evidenceRoot -Filter '*-transaction-proof.json' |
    Sort-Object Name | Select-Object -Last 1
$readinessPath = Get-ChildItem -LiteralPath $evidenceRoot -Filter '*-readiness-proof.json' |
    Sort-Object Name | Select-Object -Last 1

foreach ($required in @($capacityPath, $failoverPath, $transactionPath, $readinessPath)) {
    if ($null -eq $required) {
        throw 'The canonical M10 evidence set is incomplete.'
    }
}

$capacity = Get-Content -LiteralPath $capacityPath.FullName -Raw | ConvertFrom-Json
$failover = Get-Content -LiteralPath $failoverPath.FullName -Raw | ConvertFrom-Json
$transaction = Get-Content -LiteralPath $transactionPath.FullName -Raw | ConvertFrom-Json
$readiness = Get-Content -LiteralPath $readinessPath.FullName -Raw | ConvertFrom-Json

$capacityStages = @($capacity.stages | ForEach-Object {
    [ordered]@{
        concurrency = [int]$_.concurrency
        requests = [int64]$_.requests
        throughputRps = [double]$_.throughputRps
        p95Ms = [double]$_.latencyMs.p95
        errorRatePercent = [double]$_.errorRatePercent
        cpuMaxPercent = [double]$_.resources.cpuMaxPercent
        freeMemoryMinGiB = [double]$_.resources.freeMemoryMinGiB
        passed = -not [bool]$_.aborted -and [double]$_.errorRatePercent -eq 0
    }
})

$files = @(Get-ChildItem -LiteralPath $evidenceRoot -File |
    Where-Object { $_.Name -ne 'm10-evidence-manifest.json' } |
    Sort-Object Name |
    ForEach-Object {
        [ordered]@{
            path = "docs/evidence/m10/$($_.Name)"
            sizeBytes = [int64]$_.Length
            sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        }
    })

$manifest = [ordered]@{
    schemaVersion = 1
    generatedAt = (Get-Date).ToUniversalTime().ToString('o')
    subject = 'MiniSpringBoot M10 bounded high-availability verification'
    sourceCommit = $SourceCommit
    status = 'SELF_VERIFIED'
    externalVerification = 'VERITRAIL_M12_PENDING'
    claims = [ordered]@{
        capacity = [ordered]@{
            passed = [bool]$capacity.completedAllStages -and @($capacityStages | Where-Object { -not $_.passed }).Count -eq 0
            recommendedSingleMachineConcurrency = 24
            stages = $capacityStages
        }
        failover = [ordered]@{
            passed = [bool]$failover.rejoined -and [int]$failover.proxyFailuresDuringOutage -eq 0
            targetInstance = [string]$failover.targetInstance
            proxyChecksDuringOutage = [int]$failover.proxyChecksDuringOutage
            proxyFailuresDuringOutage = [int]$failover.proxyFailuresDuringOutage
            rejoined = [bool]$failover.rejoined
        }
        transaction = [ordered]@{
            passed = [bool]$transaction.passed
            baselineRestored = $transaction.baselineApi.account1.balance -eq $transaction.restoredApi.account1.balance -and
                $transaction.baselineApi.account2.balance -eq $transaction.restoredApi.account2.balance
        }
        readiness = [ordered]@{
            passed = [bool]$readiness.passed
            livenessDuringDatabaseOutage = [string]$readiness.livenessDuringOutage.status
            readinessDuringDatabaseOutageHttpStatus = [int]$readiness.readinessDuringOutage.status
            readinessAfterRecovery = [string]$readiness.readinessAfterRecovery.status
        }
    }
    files = $files
}

$json = $manifest | ConvertTo-Json -Depth 10
[System.IO.File]::WriteAllText($manifestPath, "$json`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host "[OK] Evidence manifest: $manifestPath"
