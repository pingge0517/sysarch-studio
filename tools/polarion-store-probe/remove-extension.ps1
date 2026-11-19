param(
    [Parameter(Mandatory=$true)][string]$DeploymentBackup,
    [string]$PolarionRoot = 'C:/Polarion',
    [switch]$LeaveStopped
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$backupPath = (Resolve-Path -LiteralPath $DeploymentBackup).Path
$privateRoot = [IO.Path]::GetFullPath((Join-Path $workspaceRoot 'build/private'))
if (-not $backupPath.StartsWith($privateRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Backup is outside the private deployment directory' }
$manifestPath = Join-Path $backupPath 'installation.json'
$manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
$rootPath = (Resolve-Path -LiteralPath $PolarionRoot).Path
$targetPath = [IO.Path]::GetFullPath((Join-Path $rootPath 'polarion/extensions/com.example.sysarch.m0'))
$cachePath = [IO.Path]::GetFullPath((Join-Path $rootPath 'data/workspace/.config'))
if ($manifest.Target -ne $targetPath -or $manifest.Phase -ne 'INSTALLED') { throw 'Deployment manifest is not an active matching installation' }
foreach ($path in @($targetPath,$cachePath)) {
    if (-not $path.StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Target escapes Polarion root' }
    if ((Test-Path -LiteralPath $path) -and ((Get-Item -LiteralPath $path -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Refusing linked target' }
}
$jarPath = Join-Path $targetPath 'eclipse/plugins/com.example.sysarch.m0_1.0.0/sysarch-m0.jar'
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -ne $manifest.JarSha256) { throw 'Installed artifact has changed; refusing to remove a different deployment' }
$service = Get-CimInstance Win32_Service -Filter "Name='Polarion'"
if (-not $service.PathName.Replace('"','').StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Service belongs to another installation' }
$wasRunning = $service.State -eq 'Running'
if ($wasRunning) { Stop-Service -Name Polarion -Force -WarningAction SilentlyContinue; (Get-Service Polarion).WaitForStatus('Stopped',[TimeSpan]::FromSeconds(30)) }
try {
    Move-Item -LiteralPath $targetPath -Destination (Join-Path $backupPath 'removed-probe-extension')
    if (Test-Path -LiteralPath $cachePath) { Move-Item -LiteralPath $cachePath -Destination (Join-Path $backupPath 'probe-osgi-cache') }
    $oldCache = Join-Path $backupPath 'previous-osgi-cache'
    if (Test-Path -LiteralPath $oldCache) { Move-Item -LiteralPath $oldCache -Destination $cachePath }
    $oldExtension = Join-Path $backupPath 'previous-extension'
    if (Test-Path -LiteralPath $oldExtension) { Move-Item -LiteralPath $oldExtension -Destination $targetPath }
    $manifest.Phase = 'REMOVED'
    $manifest | Add-Member -NotePropertyName RemovedAt -NotePropertyValue ([DateTime]::UtcNow.ToString('o')) -Force
    $manifest | ConvertTo-Json | Set-Content -LiteralPath $manifestPath -Encoding UTF8
} finally { if ($wasRunning -and -not $LeaveStopped) { Start-Service -Name Polarion } }
[pscustomobject]@{Phase='REMOVED';Backup=$backupPath;ConfigurationChanged=$false;ServiceRestarted=($wasRunning -and -not $LeaveStopped)} | ConvertTo-Json
