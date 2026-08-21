param(
    [decimal]$Amount = 1.00,
    [string]$BaseUri = 'http://127.0.0.1:9080',
    [string]$EvidenceDirectory
)

. (Join-Path $PSScriptRoot 'cluster-common.ps1')

if ($Amount -le 0) { throw '证明金额必须大于 0。' }

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

function Get-DatabaseAccountSnapshot {
    $lines = @(docker exec minispring-mysql mysql `
        --user=minispring --password=minispring_123 --database=minispring_demo `
        --batch --skip-column-names `
        --execute='SELECT id, CAST(balance AS CHAR) FROM accounts WHERE id IN (1, 2) ORDER BY id;' 2>$null)
    if ($LASTEXITCODE -ne 0 -or $lines.Count -ne 2) {
        throw '无法从 minispring-mysql 读取两个基准账户。'
    }
    $parsed = @{}
    foreach ($line in $lines) {
        $columns = $line -split "`t"
        if ($columns.Count -ne 2) { throw "无法解析 MySQL 账户行：$line" }
        $parsed[[long]$columns[0]] = [decimal]$columns[1]
    }
    return [ordered]@{
        account1 = [ordered]@{ id = 1; balance = $parsed[[long]1] }
        account2 = [ordered]@{ id = 2; balance = $parsed[[long]2] }
    }
}

function Test-AccountSnapshotsEqual([object]$Left, [object]$Right) {
    return [long]$Left.account1.id -eq [long]$Right.account1.id -and
        [decimal]$Left.account1.balance -eq [decimal]$Right.account1.balance -and
        [long]$Left.account2.id -eq [long]$Right.account2.id -and
        [decimal]$Left.account2.balance -eq [decimal]$Right.account2.balance
}

function Assert-ApiMatchesDatabase([string]$Stage, [object]$Api, [object]$Database) {
    if (-not (Test-AccountSnapshotsEqual -Left $Api -Right $Database)) {
        throw "$Stage 的 API 与 MySQL 账户快照不一致。"
    }
}

$timeline = [System.Collections.Generic.List[object]]::new()
$baselineApi = Get-ApiAccountSnapshot
$baselineDatabase = Get-DatabaseAccountSnapshot
Assert-ApiMatchesDatabase -Stage '基线' -Api $baselineApi -Database $baselineDatabase
$committed = $false
$proofError = $null
$restoredApi = $null
$restoredDatabase = $null

try {
    $commitUri = "$BaseUri/api/accounts/transfer?from=1&to=2&amount=$Amount"
    $commitResponse = Invoke-RestMethod -Method Post -Uri $commitUri -TimeoutSec 10
    $committed = $true
    $afterCommitApi = Get-ApiAccountSnapshot
    $afterCommitDatabase = Get-DatabaseAccountSnapshot
    Assert-ApiMatchesDatabase -Stage '正常提交后' -Api $afterCommitApi -Database $afterCommitDatabase
    if ([decimal]$afterCommitApi.account1.balance -ne ([decimal]$baselineApi.account1.balance - $Amount) -or
        [decimal]$afterCommitApi.account2.balance -ne ([decimal]$baselineApi.account2.balance + $Amount)) {
        throw '正常转账后的余额变化不等于证明金额。'
    }
    $timeline.Add([ordered]@{
        at = (Get-Date).ToString('o')
        event = 'COMMIT_VERIFIED'
        response = $commitResponse
        api = $afterCommitApi
        database = $afterCommitDatabase
    })

    $rollbackUri = "$BaseUri/api/accounts/transfer-fail?from=1&to=2&amount=$Amount"
    $rollbackStatus = $null
    $rollbackMessage = $null
    try {
        $unexpected = Invoke-WebRequest -Method Post -Uri $rollbackUri -TimeoutSec 10
        $rollbackStatus = [int]$unexpected.StatusCode
        $rollbackMessage = [string]$unexpected.Content
    } catch {
        if ($null -eq $_.Exception.Response) { throw }
        $rollbackStatus = [int]$_.Exception.Response.StatusCode
        $rollbackMessage = $_.ErrorDetails.Message
    }
    if ($rollbackStatus -ne 500) {
        throw "刻意失败转账应返回 HTTP 500，实际为 $rollbackStatus。"
    }

    $afterRollbackApi = Get-ApiAccountSnapshot
    $afterRollbackDatabase = Get-DatabaseAccountSnapshot
    Assert-ApiMatchesDatabase -Stage '刻意回滚后' -Api $afterRollbackApi -Database $afterRollbackDatabase
    if (-not (Test-AccountSnapshotsEqual -Left $afterCommitApi -Right $afterRollbackApi)) {
        throw '刻意失败转账改变了账户余额，回滚不完整。'
    }
    $timeline.Add([ordered]@{
        at = (Get-Date).ToString('o')
        event = 'ROLLBACK_VERIFIED'
        httpStatus = $rollbackStatus
        message = $rollbackMessage
        api = $afterRollbackApi
        database = $afterRollbackDatabase
    })
} catch {
    $proofError = $_
} finally {
    if ($committed) {
        try {
            $restoreUri = "$BaseUri/api/accounts/transfer?from=2&to=1&amount=$Amount"
            $restoreResponse = Invoke-RestMethod -Method Post -Uri $restoreUri -TimeoutSec 10
            $restoredApi = Get-ApiAccountSnapshot
            $restoredDatabase = Get-DatabaseAccountSnapshot
            Assert-ApiMatchesDatabase -Stage '恢复基线后' -Api $restoredApi -Database $restoredDatabase
            if (-not (Test-AccountSnapshotsEqual -Left $baselineApi -Right $restoredApi)) {
                throw '反向转账后未恢复原始账户基线。'
            }
            $timeline.Add([ordered]@{
                at = (Get-Date).ToString('o')
                event = 'BASELINE_RESTORED'
                response = $restoreResponse
                api = $restoredApi
                database = $restoredDatabase
            })
        } catch {
            if ($null -eq $proofError) { $proofError = $_ }
            else { $proofError = [System.Management.Automation.ErrorRecord]::new(
                    [System.AggregateException]::new('事务证明失败，且恢复基线也失败。', @($proofError.Exception, $_.Exception)),
                    'M10TransactionProofAndRestoreFailed',
                    [System.Management.Automation.ErrorCategory]::InvalidResult,
                    $null
                ) }
        }
    }
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$result = [ordered]@{
    schemaVersion = 1
    capturedAt = (Get-Date).ToString('o')
    baseUri = $BaseUri
    amount = $Amount
    passed = $null -eq $proofError
    baselineApi = $baselineApi
    baselineDatabase = $baselineDatabase
    restoredApi = $restoredApi
    restoredDatabase = $restoredDatabase
    timeline = $timeline
    error = if ($null -eq $proofError) { $null } else { $proofError.Exception.Message }
}
$resultPath = Join-Path $evidenceDirectory "$stamp-transaction-proof.json"
$result | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $resultPath -Encoding utf8

if ($null -ne $proofError) { throw $proofError }
Write-Host "[OK] 正常提交、刻意回滚、API/MySQL 对账与基线恢复均通过。"
Write-Host "[OK] 证据：$resultPath"
