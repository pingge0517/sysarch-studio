param(
    [string]$PolarionRoot = 'C:/Polarion',
    [Parameter(Mandatory=$true)][string]$ExpectedSha256,
    [switch]$DiscardRebuildableCache
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$sourcePath = (Resolve-Path (Join-Path $workspaceRoot 'build/polarion-store/extension/com.example.sysarch.m0')).Path
$rootPath = (Resolve-Path -LiteralPath $PolarionRoot).Path
$extensionsPath = (Resolve-Path (Join-Path $rootPath 'polarion/extensions')).Path
$targetPath = [IO.Path]::GetFullPath((Join-Path $extensionsPath 'com.example.sysarch.m0'))
$cachePath = [IO.Path]::GetFullPath((Join-Path $rootPath 'data/workspace/.config'))
foreach ($checkedPath in @($targetPath,$cachePath)) {
    if (-not $checkedPath.StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Target escapes Polarion root' }
    if ((Test-Path -LiteralPath $checkedPath) -and ((Get-Item -LiteralPath $checkedPath -Force).Attributes -band [IO.FileAttributes]::ReparsePoint)) { throw 'Refusing linked installation target' }
}
$jarPath = Join-Path $sourcePath 'eclipse/plugins/com.example.sysarch.m0_1.0.0/sysarch-m0.jar'
if ((Get-FileHash -LiteralPath $jarPath -Algorithm SHA256).Hash -ne $ExpectedSha256) { throw 'Probe JAR differs from reviewed artifact' }
$service = Get-CimInstance Win32_Service -Filter "Name='Polarion'"
if (-not $service -or -not $service.PathName.Replace('"','').StartsWith($rootPath+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Polarion service does not match the target installation' }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backupPath = [IO.Path]::GetFullPath((Join-Path $workspaceRoot "build/private/m0-deployment-$stamp"))
if (-not $backupPath.StartsWith($workspaceRoot+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)) { throw 'Backup escapes workspace' }
New-Item -ItemType Directory -Path $backupPath -Force | Out-Null
$acl = New-Object Security.AccessControl.DirectorySecurity
$acl.SetAccessRuleProtection($true,$false)
foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544') | Select-Object -Unique) {
    $identity = New-Object Security.Principal.SecurityIdentifier($sid)
    $rule = New-Object Security.AccessControl.FileSystemAccessRule($identity,'FullControl','ContainerInherit,ObjectInherit','None','Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $backupPath -AclObject $acl
Copy-Item -LiteralPath (Join-Path $rootPath 'polarion/configuration/polarion.properties') -Destination (Join-Path $backupPath 'polarion.properties.backup')
$wasRunning = $service.State -eq 'Running'
$installed = $false
try {
    if ($wasRunning) {
        Stop-Service -Name Polarion -Force -WarningAction SilentlyContinue
        (Get-Service Polarion).WaitForStatus('Stopped',[TimeSpan]::FromSeconds(30))
    }
    if (Test-Path -LiteralPath $targetPath) { Move-Item -LiteralPath $targetPath -Destination (Join-Path $backupPath 'previous-extension') }
    if (Test-Path -LiteralPath $cachePath) {
        if ($DiscardRebuildableCache) {
            # $cachePath was resolved and checked against the exact Polarion root above; the service is stopped.
            if ($cachePath -ne [IO.Path]::GetFullPath((Join-Path $rootPath 'data/workspace/.config'))) { throw 'Unexpected cache path' }
            Remove-Item -LiteralPath $cachePath -Recurse -Force
        } else { Move-Item -LiteralPath $cachePath -Destination (Join-Path $backupPath 'previous-osgi-cache') }
    }
    Copy-Item -LiteralPath $sourcePath -Destination $extensionsPath -Recurse
    $installed = $true
    if ($wasRunning) { Start-Service -Name Polarion }
    [ordered]@{Phase='INSTALLED';Target=$targetPath;Backup=$backupPath;JarSha256=$ExpectedSha256;ServiceRestarted=$wasRunning;RebuildableCacheDiscarded=[bool]$DiscardRebuildableCache} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $backupPath 'installation.json') -Encoding UTF8
    [pscustomobject]@{Phase='INSTALLED';Backup=$backupPath;ServiceRestarted=$wasRunning;HealthCheckPending=$true} | ConvertTo-Json
} catch {
    # Restore only paths checked above; preserve failed artifacts rather than recursively deleting them.
    if ($installed -and (Test-Path -LiteralPath $targetPath)) {
        Stop-Service -Name Polarion -Force -WarningAction SilentlyContinue
        Move-Item -LiteralPath $targetPath -Destination (Join-Path $backupPath 'failed-extension')
    }
    $oldExtension = Join-Path $backupPath 'previous-extension'
    if ((Test-Path -LiteralPath $oldExtension) -and -not (Test-Path -LiteralPath $targetPath)) { Move-Item -LiteralPath $oldExtension -Destination $targetPath }
    $oldCache = Join-Path $backupPath 'previous-osgi-cache'
    if ((Test-Path -LiteralPath $oldCache) -and -not (Test-Path -LiteralPath $cachePath)) { Move-Item -LiteralPath $oldCache -Destination $cachePath }
    if ($wasRunning) { Start-Service -Name Polarion }
    throw
}
