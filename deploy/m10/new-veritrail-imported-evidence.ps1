param(
    [Parameter(Mandatory = $true)]
    [string]$ProofDirectory,
    [string]$ManifestPath,
    [string]$OutputPath
)

$ErrorActionPreference = 'Stop'

# Windows PowerShell 5.1 evaluates parameter defaults before $PSScriptRoot is
# reliably available. Resolve repository-relative defaults only after binding.
if ([string]::IsNullOrWhiteSpace($ManifestPath)) {
    $ManifestPath = Join-Path $PSScriptRoot '..\..\docs\evidence\m10\m10-evidence-manifest.json'
}
if ([string]::IsNullOrWhiteSpace($OutputPath)) {
    $OutputPath = Join-Path $PSScriptRoot '..\..\docs\evidence\m10\veritrail\evidence.json'
}

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$manifestFile = (Resolve-Path -LiteralPath $ManifestPath).Path
$proofRoot = (Resolve-Path -LiteralPath $ProofDirectory).Path
$outputFile = [System.IO.Path]::GetFullPath($OutputPath)

function Get-CanonicalTextDigest([string]$Path) {
    $text = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
    $canonical = $text.Replace("`r`n", "`n").Replace("`r", "`n")
    $bytes = (New-Object System.Text.UTF8Encoding($false)).GetBytes($canonical)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [ordered]@{
            sizeBytes = [int64]$bytes.Length
            sha256 = ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString('x2') }) -join ''
        }
    } finally {
        $sha.Dispose()
    }
}

function Get-OnlyProof([string[]]$Filters) {
    $matches = @($Filters | ForEach-Object {
        Get-ChildItem -LiteralPath $proofRoot -File -Filter $_
    } | Sort-Object FullName -Unique)
    if ($matches.Count -ne 1) {
        throw "Proof directory must resolve exactly one file for [$($Filters -join ', ')]; found $($matches.Count)."
    }
    return $matches[0]
}

function Test-AccountSnapshotsEqual([object]$Left, [object]$Right) {
    return [long]$Left.account1.id -eq [long]$Right.account1.id -and
        [decimal]$Left.account1.balance -eq [decimal]$Right.account1.balance -and
        [long]$Left.account2.id -eq [long]$Right.account2.id -and
        [decimal]$Left.account2.balance -eq [decimal]$Right.account2.balance
}

$manifest = Get-Content -LiteralPath $manifestFile -Raw | ConvertFrom-Json
if ($manifest.schemaVersion -ne 1 -or $manifest.hashMode -ne 'UTF8_LF_CANONICAL') {
    throw 'M10 evidence manifest does not use the supported schema and canonical hash mode.'
}
if ($manifest.status -ne 'SELF_VERIFIED') {
    throw "M10 evidence manifest status must remain SELF_VERIFIED; actual: $($manifest.status)."
}
if ($manifest.externalVerification -notin @('VERITRAIL_M12_PENDING', 'VERITRAIL_IMPORTED_EVIDENCE_PASS')) {
    throw "Unsupported external verification state: $($manifest.externalVerification)."
}
if ([string]$manifest.sourceCommit -notmatch '^[0-9a-f]{40}$') {
    throw 'M10 evidence manifest sourceCommit is not a full Git commit ID.'
}
& git -C $repositoryRoot cat-file -e "$($manifest.sourceCommit)^{commit}"
if ($LASTEXITCODE -ne 0) {
    throw 'M10 evidence manifest sourceCommit is not present in this repository.'
}

$manifestHashesValid = $true
foreach ($entry in @($manifest.files)) {
    $relative = [string]$entry.path
    if (-not $relative.StartsWith('docs/evidence/m10/', [System.StringComparison]::Ordinal) -or
        $relative.Contains('..') -or $relative.Contains('\')) {
        throw "Manifest contains an unsafe evidence path: $relative"
    }
    $candidate = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot $relative))
    if (-not $candidate.StartsWith($repositoryRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase) -or
        -not (Test-Path -LiteralPath $candidate -PathType Leaf)) {
        throw "Manifest evidence file is missing or outside the repository: $relative"
    }
    $digest = Get-CanonicalTextDigest -Path $candidate
    if ([int64]$entry.sizeBytes -ne [int64]$digest.sizeBytes -or
        [string]$entry.sha256 -ne [string]$digest.sha256) {
        $manifestHashesValid = $false
        break
    }
}

$failoverLoadFile = Get-OnlyProof -Filters @('failover-load.json', '*-failover.json')
$failoverSummaryFile = Get-OnlyProof -Filters @('failover-summary.json', '*-failover-summary.json')
$transactionFile = Get-OnlyProof -Filters @('transaction-proof.json', '*-transaction-proof.json')
$readinessFile = Get-OnlyProof -Filters @('readiness-proof.json', '*-readiness-proof.json')

$failoverLoad = Get-Content -LiteralPath $failoverLoadFile.FullName -Raw | ConvertFrom-Json
$failover = Get-Content -LiteralPath $failoverSummaryFile.FullName -Raw | ConvertFrom-Json
$transaction = Get-Content -LiteralPath $transactionFile.FullName -Raw | ConvertFrom-Json
$readiness = Get-Content -LiteralPath $readinessFile.FullName -Raw | ConvertFrom-Json

$loadStages = @($failoverLoad.stages)
$freshLoadCompleted = [bool]$failoverLoad.completedAllStages -and $loadStages.Count -eq 1 -and
    -not [bool]$loadStages[0].aborted
$freshLoadZeroErrors = $freshLoadCompleted -and [int64]$loadStages[0].unexpected -eq 0 -and
    [double]$loadStages[0].errorRatePercent -eq 0
$freshFailoverAccountsUnchanged = Test-AccountSnapshotsEqual -Left $failover.accountsBefore -Right $failover.accountsAfter
$transactionBaselineRestored = [bool]$transaction.passed -and
    (Test-AccountSnapshotsEqual -Left $transaction.baselineApi -Right $transaction.baselineDatabase) -and
    (Test-AccountSnapshotsEqual -Left $transaction.baselineApi -Right $transaction.restoredApi) -and
    (Test-AccountSnapshotsEqual -Left $transaction.restoredApi -Right $transaction.restoredDatabase)
$selfVerifiedClaimsPass = [bool]$manifest.claims.capacity.passed -and
    [bool]$manifest.claims.failover.passed -and
    [bool]$manifest.claims.transaction.passed -and
    [bool]$manifest.claims.readiness.passed

$proofDigests = @($failoverLoadFile, $failoverSummaryFile, $transactionFile, $readinessFile | ForEach-Object {
    $digest = Get-CanonicalTextDigest -Path $_.FullName
    [ordered]@{
        logicalName = $_.Name
        sizeBytes = $digest.sizeBytes
        sha256 = $digest.sha256
    }
})

$evidence = [ordered]@{
    schema_version = '0.1'
    evidence_type = 'minispringboot.m10-verification'
    source = 'MiniSpringBoot deploy/m10 imported evidence adapter/0.1'
    captured_at = (Get-Date).ToUniversalTime().ToString("yyyy-MM-dd'T'HH:mm:ss.fff'Z'")
    facts = [ordered]@{
        source_commit_matches = $true
        tracked_evidence_hashes_valid = $manifestHashesValid
        self_verified_claims_pass = $selfVerifiedClaimsPass
        fresh_failover_load_completed = $freshLoadCompleted
        fresh_failover_load_zero_errors = $freshLoadZeroErrors
        fresh_failover_proxy_failures = [int]$failover.proxyFailuresDuringOutage
        fresh_failover_rejoined = [bool]$failover.rejoined
        fresh_failover_accounts_unchanged = $freshFailoverAccountsUnchanged
        transaction_proof_passed = [bool]$transaction.passed
        transaction_baseline_restored = $transactionBaselineRestored
        readiness_proof_passed = [bool]$readiness.passed
        liveness_during_database_outage = [string]$readiness.livenessDuringOutage.status
        readiness_during_database_outage_http_status = [int]$readiness.readinessDuringOutage.status
        readiness_after_recovery = [string]$readiness.readinessAfterRecovery.status
        full_topology_lifecycle_managed = $false
    }
    observed_variables = [ordered]@{
        verification_scope = 'IMPORTED_EVIDENCE_AUDIT'
        lifecycle_ownership = 'NOT_PROVEN'
    }
    metadata = [ordered]@{
        sourceCommit = [string]$manifest.sourceCommit
        manifestHashMode = [string]$manifest.hashMode
        manifestEntryCount = @($manifest.files).Count
        proofDigests = $proofDigests
        boundary = 'VeriTrail evaluates imported M10 evidence; it does not claim ownership of Docker, Nginx, MySQL, or the three Java process lifecycles.'
    }
}

$parent = Split-Path -Parent $outputFile
New-Item -ItemType Directory -Force -Path $parent | Out-Null
$json = $evidence | ConvertTo-Json -Depth 12
[System.IO.File]::WriteAllText($outputFile, "$json`n", (New-Object System.Text.UTF8Encoding($false)))
Write-Host "[OK] VeriTrail imported Evidence: $outputFile"
