param(
    [string]$JavaHome = "C:\Program Files\Java\jdk-25.0.2",
    [string]$ModelPath = "C:\Users\mahaj\Downloads\GIS\Models\Feature Exraction\best_model_multiclass.pth",
    [string]$PythonExecutable = "C:\Users\mahaj\varlyq\env\Scripts\python.exe",
    [switch]$SkipWarmup,
    [switch]$ShowLogs,
    [int]$SplashTimeoutSeconds = 180
)

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if (-not (Test-Path $JavaHome)) {
    throw "JAVA_HOME path not found: $JavaHome"
}
if (-not (Test-Path $PythonExecutable)) {
    throw "Python executable not found: $PythonExecutable"
}
if (-not (Test-Path $ModelPath)) {
    throw "Model file not found: $ModelPath"
}

$env:JAVA_HOME = $JavaHome
$env:Path = "$JavaHome\bin;$env:Path"
$env:TERRAGIS_MODEL_PATH = $ModelPath
$env:TERRAGIS_PYTHON_EXECUTABLE = $PythonExecutable

$splashScript = @'
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

[System.Windows.Forms.Application]::EnableVisualStyles()

$form = New-Object System.Windows.Forms.Form
$form.Text = "TerraGIS"
$form.StartPosition = "CenterScreen"
$form.FormBorderStyle = "FixedSingle"
$form.MaximizeBox = $false
$form.MinimizeBox = $false
$form.ClientSize = New-Object System.Drawing.Size(640, 380)
$form.Padding = New-Object System.Windows.Forms.Padding(20)
$form.BackColor = [System.Drawing.Color]::FromArgb(13, 36, 57)
$form.TopMost = $true

$content = New-Object System.Windows.Forms.TableLayoutPanel
$content.Dock = [System.Windows.Forms.DockStyle]::Fill
$content.ColumnCount = 1
$content.RowCount = 4
$content.BackColor = [System.Drawing.Color]::Transparent
$content.Margin = New-Object System.Windows.Forms.Padding(0)
$content.Padding = New-Object System.Windows.Forms.Padding(0)
$content.ColumnStyles.Add((New-Object System.Windows.Forms.ColumnStyle([System.Windows.Forms.SizeType]::Percent, 100)))
$content.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, 112)))
$content.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, 44)))
$content.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, 52)))
$content.RowStyles.Add((New-Object System.Windows.Forms.RowStyle([System.Windows.Forms.SizeType]::Absolute, 30)))

$title = New-Object System.Windows.Forms.Label
$title.Text = "TerraGIS"
$title.AutoSize = $true
$title.Font = New-Object System.Drawing.Font("Segoe UI", 36, [System.Drawing.FontStyle]::Bold)
$title.ForeColor = [System.Drawing.Color]::FromArgb(221, 241, 255)
$title.Anchor = [System.Windows.Forms.AnchorStyles]::None
$title.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter

$subtitle = New-Object System.Windows.Forms.Label
$subtitle.Text = "Professional GIS Mapping and Analysis"
$subtitle.AutoSize = $true
$subtitle.Font = New-Object System.Drawing.Font("Segoe UI", 12)
$subtitle.ForeColor = [System.Drawing.Color]::FromArgb(160, 206, 230)
$subtitle.Anchor = [System.Windows.Forms.AnchorStyles]::None
$subtitle.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter

$progress = New-Object System.Windows.Forms.ProgressBar
$progress.Style = "Marquee"
$progress.MarqueeAnimationSpeed = 24
$progress.Width = 460
$progress.Height = 14
$progress.Anchor = [System.Windows.Forms.AnchorStyles]::None

$loading = New-Object System.Windows.Forms.Label
$loading.Text = "Loading TerraGIS..."
$loading.AutoSize = $true
$loading.Font = New-Object System.Drawing.Font("Segoe UI", 10)
$loading.ForeColor = [System.Drawing.Color]::FromArgb(150, 195, 220)
$loading.Anchor = [System.Windows.Forms.AnchorStyles]::None
$loading.TextAlign = [System.Drawing.ContentAlignment]::MiddleCenter

$content.Controls.Add($title, 0, 0)
$content.Controls.Add($subtitle, 0, 1)
$content.Controls.Add($progress, 0, 2)
$content.Controls.Add($loading, 0, 3)
$form.Controls.Add($content)
[System.Windows.Forms.Application]::Run($form)
'@

$tempSplashScript = Join-Path $env:TEMP ("terragis-splash-" + [guid]::NewGuid().ToString() + ".ps1")
Set-Content -Path $tempSplashScript -Value $splashScript -Encoding UTF8

$splashProc = Start-Process -FilePath "powershell" -ArgumentList @(
    "-NoProfile",
    "-ExecutionPolicy", "Bypass",
    "-STA",
    "-File", ('"' + $tempSplashScript + '"')
) -PassThru

$runLog = Join-Path $env:TEMP ("terragis-run-" + [guid]::NewGuid().ToString() + ".out.log")
$runErrLog = Join-Path $env:TEMP ("terragis-run-" + [guid]::NewGuid().ToString() + ".err.log")

try {
    if (-not $SkipWarmup) {
        if ($ShowLogs) {
            Write-Host "[launcher] Warmup build step (dependency/cache prep) with logs..."
            & .\mvnw.cmd -DskipTests process-classes
        } else {
            Write-Host "[launcher] Warmup build step (quiet mode)..."
            & .\mvnw.cmd -q -DskipTests process-classes
        }

        if ($LASTEXITCODE -ne 0) {
            throw "Warmup build failed with exit code $LASTEXITCODE."
        }
    }

    if ($ShowLogs) {
        Write-Host "[launcher] Starting TerraGIS with Maven logs..."
    } else {
        Write-Host "[launcher] Starting TerraGIS in quiet mode (pass -ShowLogs to see Maven logs)..."
    }

    $closeSplashBlock = {
        param($SplashPid, $TimeoutSeconds)
        $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
        while ((Get-Date) -lt $deadline) {
            $appWindow = Get-Process | Where-Object { $_.MainWindowTitle -like "TerraGIS*" } | Select-Object -First 1
            if ($appWindow) {
                Stop-Process -Id $SplashPid -Force -ErrorAction SilentlyContinue
                return
            }
            Start-Sleep -Milliseconds 300
        }
        Stop-Process -Id $SplashPid -Force -ErrorAction SilentlyContinue
    }

    if ($ShowLogs) {
        $watcher = Start-Job -ScriptBlock $closeSplashBlock -ArgumentList $splashProc.Id, $SplashTimeoutSeconds
        try {
            & .\mvnw.cmd javafx:run
            if ($LASTEXITCODE -ne 0) {
                throw "Maven run failed with exit code $LASTEXITCODE."
            }
        }
        finally {
            if ($watcher) {
                Remove-Job -Id $watcher.Id -Force -ErrorAction SilentlyContinue
            }
        }
    } else {
        $mvnProc = Start-Process -FilePath "cmd.exe" -ArgumentList "/c .\\mvnw.cmd -q javafx:run" -WorkingDirectory $PSScriptRoot -PassThru -NoNewWindow -RedirectStandardOutput $runLog -RedirectStandardError $runErrLog

        $closedSplash = $false
        $deadline = (Get-Date).AddSeconds($SplashTimeoutSeconds)

        while (-not $mvnProc.HasExited) {
            if (-not $closedSplash) {
                $appWindow = Get-Process | Where-Object { $_.MainWindowTitle -like "TerraGIS*" } | Select-Object -First 1
                if ($appWindow) {
                    try {
                        Stop-Process -Id $splashProc.Id -Force -ErrorAction SilentlyContinue
                    } catch {
                    }
                    $closedSplash = $true
                } elseif ((Get-Date) -gt $deadline) {
                    try {
                        Stop-Process -Id $splashProc.Id -Force -ErrorAction SilentlyContinue
                    } catch {
                    }
                    $closedSplash = $true
                }
            }

            Start-Sleep -Milliseconds 300
        }

        if (-not $closedSplash -and $splashProc -and -not $splashProc.HasExited) {
            try {
                Stop-Process -Id $splashProc.Id -Force -ErrorAction SilentlyContinue
            } catch {
            }
        }

        if ($mvnProc.ExitCode -ne 0) {
            Write-Host "[launcher] Maven run failed; showing last 120 log lines:"
            if (Test-Path $runLog) {
                Get-Content -Path $runLog -Tail 80
            }
            if (Test-Path $runErrLog) {
                Get-Content -Path $runErrLog -Tail 40
            }
            throw "Maven run failed with exit code $($mvnProc.ExitCode)."
        }
    }
}
finally {
    if ($splashProc -and -not $splashProc.HasExited) {
        try {
            Stop-Process -Id $splashProc.Id -Force
        } catch {
        }
    }

    if (Test-Path $tempSplashScript) {
        Remove-Item $tempSplashScript -Force -ErrorAction SilentlyContinue
    }

    if (Test-Path $runLog) {
        Remove-Item $runLog -Force -ErrorAction SilentlyContinue
    }

    if (Test-Path $runErrLog) {
        Remove-Item $runErrLog -Force -ErrorAction SilentlyContinue
    }
}
