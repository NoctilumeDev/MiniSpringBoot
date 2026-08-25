$ErrorActionPreference = 'Stop'

. (Join-Path $PSScriptRoot 'cluster-common.ps1')

$testDirectory = Join-Path ([System.IO.Path]::GetTempPath()) (
    'minispring-m10-process-exit-' + [System.Guid]::NewGuid().ToString('N')
)
New-Item -ItemType Directory -Path $testDirectory | Out-Null
$artifacts = [System.Collections.Generic.List[string]]::new()

try {
    foreach ($expectedExitCode in @(0, 7)) {
        $stdout = Join-Path $testDirectory "$expectedExitCode.out.log"
        $stderr = Join-Path $testDirectory "$expectedExitCode.err.log"
        $artifacts.Add($stdout)
        $artifacts.Add($stderr)
        $arguments = @(
            '/d',
            '/c',
            "ping 127.0.0.1 -n 2 > nul & exit $expectedExitCode"
        )
        $process = Start-M10RedirectedProcess -FilePath 'cmd.exe' -ArgumentList $arguments `
            -StandardOutputPath $stdout -StandardErrorPath $stderr

        if ($expectedExitCode -eq 0) {
            $observedExitCode = Wait-M10SuccessfulProcess -Process $process -Description 'zero-exit test process'
            if ($observedExitCode -ne 0) {
                throw "Expected exit code 0, observed $observedExitCode."
            }
            continue
        }

        $failedClosed = $false
        try {
            Wait-M10SuccessfulProcess -Process $process -Description 'nonzero-exit test process' | Out-Null
        } catch {
            if ($_.Exception.Message -notmatch [System.Text.RegularExpressions.Regex]::Escape([string]$expectedExitCode)) {
                throw
            }
            $failedClosed = $true
        }
        if (-not $failedClosed) {
            throw "Nonzero exit code $expectedExitCode did not fail closed."
        }
    }

    Write-Host "[OK] Windows PowerShell $($PSVersionTable.PSVersion) redirected child exits: zero readable, nonzero fails closed."
} finally {
    foreach ($artifact in $artifacts) {
        Remove-Item -LiteralPath $artifact -Force -ErrorAction SilentlyContinue
    }
    Remove-Item -LiteralPath $testDirectory -Force -ErrorAction SilentlyContinue
}
