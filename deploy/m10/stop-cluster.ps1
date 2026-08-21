. (Join-Path $PSScriptRoot 'cluster-common.ps1')

$state = Get-M10State
& docker compose -f $script:ComposeFile down --remove-orphans

if ($null -ne $state) {
    foreach ($instance in $state.instances) {
        try {
            if (Stop-M10Instance $instance) {
                Write-Host "[DOWN] $($instance.id) :$($instance.port) PID $($instance.pid)"
            }
        } catch {
            Write-Warning $_
        }
    }
    if (Test-Path -LiteralPath $script:StateFile) {
        Remove-Item -LiteralPath $script:StateFile
    }
}
Write-Host '[OK] M10 集群已停止。MySQL 数据卷未改动。'
