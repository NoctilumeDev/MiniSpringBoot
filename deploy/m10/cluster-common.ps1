Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$script:M10Directory = $PSScriptRoot
$script:RepositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$script:RuntimeDirectory = Join-Path $PSScriptRoot '.runtime'
$script:StateFile = Join-Path $script:RuntimeDirectory 'cluster.json'
$script:ComposeFile = Join-Path $PSScriptRoot 'docker-compose.yml'
$script:MainClass = 'com.minispring.demo.app.DemoApplication'

function Initialize-M10RuntimeDirectory {
    New-Item -ItemType Directory -Force -Path $script:RuntimeDirectory | Out-Null
}

function Get-M10State {
    if (-not (Test-Path -LiteralPath $script:StateFile)) {
        return $null
    }
    return Get-Content -LiteralPath $script:StateFile -Raw | ConvertFrom-Json
}

function Save-M10State([object]$State) {
    Initialize-M10RuntimeDirectory
    $State | ConvertTo-Json -Depth 6 | Set-Content -LiteralPath $script:StateFile -Encoding utf8
}

function Test-M10PortFree([int]$Port) {
    return $null -eq (Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Assert-M10PortFree([int]$Port) {
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
    if ($null -ne $listener) {
        throw "端口 $Port 已被 PID $($listener.OwningProcess) 占用；拒绝覆盖未知进程。"
    }
}

function Wait-M10Endpoint([string]$Uri, [int]$TimeoutSeconds = 30) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    do {
        try {
            return Invoke-RestMethod -Uri $Uri -TimeoutSec 3
        } catch {
            Start-Sleep -Milliseconds 500
        }
    } while ((Get-Date) -lt $deadline)
    throw "等待端点超时: $Uri"
}

function Get-M10DemoProcess([int]$ProcessId, [int]$Port) {
    $process = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if ($null -eq $process) {
        return $null
    }
    $commandLine = [string]$process.CommandLine
    if (-not $commandLine.Contains($script:MainClass) -or
        -not $commandLine.Contains("-Dserver.port=$Port")) {
        throw "PID $ProcessId 仍存在，但命令行不属于 M10 的 $Port 实例；拒绝操作。"
    }
    return $process
}

function Get-M10RuntimeClasspath {
    $classpathFile = Join-Path $script:RepositoryRoot 'mini-spring-demo\target\m10-runtime-classpath.txt'
    if (-not (Test-Path -LiteralPath $classpathFile)) {
        throw "缺少运行时 classpath: $classpathFile；请先执行 start-cluster.ps1（不要使用 -SkipBuild）。"
    }
    $dependencyClasspath = (Get-Content -LiteralPath $classpathFile -Raw).Trim()
    $classes = (Resolve-Path (Join-Path $script:RepositoryRoot 'mini-spring-demo\target\classes')).Path
    return "$classes;$dependencyClasspath"
}

function Start-M10Instance([string]$InstanceId, [int]$Port) {
    Assert-M10PortFree $Port
    Initialize-M10RuntimeDirectory
    $classpath = Get-M10RuntimeClasspath
    $stdout = Join-Path $script:RuntimeDirectory "$InstanceId.out.log"
    $stderr = Join-Path $script:RuntimeDirectory "$InstanceId.err.log"
    $arguments = @(
        '-Xms128m',
        '-Xmx256m',
        '-Dfile.encoding=UTF-8',
        "-Dserver.port=$Port",
        "-Dapp.instance-id=$InstanceId",
        '-cp',
        $classpath,
        $script:MainClass
    )
    $launcher = Start-Process -FilePath 'java' -ArgumentList $arguments -WindowStyle Hidden -PassThru `
        -RedirectStandardOutput $stdout -RedirectStandardError $stderr
    $ready = Wait-M10Endpoint -Uri "http://127.0.0.1:$Port/health" -TimeoutSeconds 30
    if ($ready.instance -ne $InstanceId -or [int]$ready.port -ne $Port -or $ready.database -ne 'UP') {
        throw "实例 $InstanceId 的就绪响应与启动参数不一致: $($ready | ConvertTo-Json -Compress)"
    }
    # Windows 的 java 命令可能先经过 javapath 转发器；Start-Process 返回的 launcher PID
    # 会退出，真正 JVM 是端口监听者。状态文件必须记录实际监听 PID，故障注入才不会失真。
    $listener = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($null -eq $listener) {
        throw "实例 $InstanceId 已响应，但未找到端口 $Port 的监听进程。"
    }
    $actualProcessId = [int]$listener.OwningProcess
    Get-M10DemoProcess -ProcessId $actualProcessId -Port $Port | Out-Null
    return [ordered]@{
        id = $InstanceId
        port = $Port
        pid = $actualProcessId
        launcherPid = $launcher.Id
        startedAt = $ready.startedAt
        stdout = $stdout
        stderr = $stderr
    }
}

function Stop-M10Instance([object]$Instance) {
    $process = Get-M10DemoProcess -ProcessId ([int]$Instance.pid) -Port ([int]$Instance.port)
    if ($null -eq $process) {
        return $false
    }
    Stop-Process -Id ([int]$Instance.pid)
    try {
        Wait-Process -Id ([int]$Instance.pid) -Timeout 10 -ErrorAction Stop
    } catch {
        $remaining = Get-M10DemoProcess -ProcessId ([int]$Instance.pid) -Port ([int]$Instance.port)
        if ($null -ne $remaining) {
            Stop-Process -Id ([int]$Instance.pid) -Force
        }
    }
    return $true
}

function Get-M10ResourceSnapshot {
    $os = Get-CimInstance Win32_OperatingSystem
    $cpu = Get-CimInstance Win32_Processor | Measure-Object -Property LoadPercentage -Average
    $memoryPerf = Get-CimInstance Win32_PerfFormattedData_PerfOS_Memory -ErrorAction SilentlyContinue
    return [ordered]@{
        capturedAt = (Get-Date).ToString('o')
        cpuPercent = [math]::Round([double]$cpu.Average, 1)
        freeMemoryMiB = [math]::Round([double]$os.FreePhysicalMemory / 1024, 1)
        pagesPerSecond = if ($null -eq $memoryPerf) { $null } else { [double]$memoryPerf.PagesPersec }
    }
}
