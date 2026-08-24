param(
    [switch]$SkipBuild,
    [switch]$RefreshFrontendDependencies
)

. (Join-Path $PSScriptRoot 'cluster-common.ps1')

$startedInstances = @()
try {
    foreach ($port in @(9080, 9091, 9092, 9093)) {
        Assert-M10PortFree $port
    }

    $mysql = docker inspect -f '{{.State.Health.Status}}' minispring-mysql 2>$null
    if ($LASTEXITCODE -ne 0 -or $mysql -ne 'healthy') {
        throw 'MySQL 容器 minispring-mysql 未处于 healthy；先恢复数据库再启动 M10。'
    }

    if (-not $SkipBuild) {
        Push-Location $script:RepositoryRoot
        try {
            & mvn -q -DskipTests install
            if ($LASTEXITCODE -ne 0) { throw 'Maven 构建失败。' }
            & mvn -q -pl mini-spring-demo dependency:build-classpath '-Dmdep.outputFile=target/m10-runtime-classpath.txt'
            if ($LASTEXITCODE -ne 0) { throw '生成运行时 classpath 失败。' }
        } finally {
            Pop-Location
        }

        Push-Location (Join-Path $script:RepositoryRoot 'demo-frontend')
        try {
            # Windows 上正在运行的 Vite 会锁住 Rolldown 原生模块。已有依赖时只构建，
            # 避免为启动生产集群破坏 9010 开发预览；干净环境仍会自动 npm ci。
            if ($RefreshFrontendDependencies -or -not (Test-Path -LiteralPath 'node_modules')) {
                & npm ci --no-audit --no-fund
                if ($LASTEXITCODE -ne 0) { throw '前端 npm ci 失败。' }
            }
            & npm run build
            if ($LASTEXITCODE -ne 0) { throw '前端生产构建失败。' }
        } finally {
            Pop-Location
        }
    }

    foreach ($spec in @(
        @{ id = 'msb-1'; port = 9091 },
        @{ id = 'msb-2'; port = 9092 },
        @{ id = 'msb-3'; port = 9093 }
    )) {
        $startedInstances += Start-M10Instance -InstanceId $spec.id -Port $spec.port
        Write-Host "[UP] $($spec.id) :$($spec.port)"
    }

    & docker compose -f $script:ComposeFile up -d
    if ($LASTEXITCODE -ne 0) { throw 'Nginx 容器启动失败。' }

    $proxyReady = Wait-M10Endpoint -Uri 'http://127.0.0.1:9080/health' -TimeoutSeconds 30
    # Windows PowerShell 5.1 otherwise invokes the retired IE parser and can
    # return a null response body on machines where that engine is unavailable.
    $index = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:9080/' -TimeoutSec 5
    if ($null -eq $index -or $index.StatusCode -ne 200 -or
        [string]::IsNullOrWhiteSpace([string]$index.Content) -or
        -not ([string]$index.Content).Contains('MiniSpringBoot')) {
        throw 'Nginx 未能提供前端生产构建。'
    }

    $state = [ordered]@{
        schemaVersion = 1
        startedAt = (Get-Date).ToString('o')
        proxy = 'http://127.0.0.1:9080'
        instances = $startedInstances
        baseline = Get-M10ResourceSnapshot
    }
    Save-M10State $state
    Write-Host "[UP] nginx :9080 -> $($proxyReady.instance)"
    Write-Host '[OK] M10 三实例集群已就绪：http://127.0.0.1:9080/'
} catch {
    # Write-Error 会在 ErrorActionPreference=Stop 下再次抛出，导致清理永远到不了；
    # 直接写 stderr，确保任何失败都先回收本脚本启动的精确进程。
    [Console]::Error.WriteLine("[ERROR] $($_.Exception.Message)")
    # Docker writes ordinary progress messages to stderr. Windows PowerShell
    # turns those into NativeCommandError records; under Stop that used to abort
    # cleanup before the JVMs were reached. Keep cleanup best-effort and ordered.
    $previousErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        & docker compose -f $script:ComposeFile down --remove-orphans 2>&1 | Out-Null
    } catch {
        Write-Warning "Nginx cleanup failed: $($_.Exception.Message)"
    } finally {
        $ErrorActionPreference = $previousErrorActionPreference
    }
    foreach ($instance in $startedInstances) {
        try { Stop-M10Instance $instance | Out-Null } catch { Write-Warning $_ }
    }
    exit 1
}
