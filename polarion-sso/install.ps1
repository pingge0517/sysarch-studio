<#
.SYNOPSIS
  Installs (or with -Uninstall removes) the SysArch single sign-on extension in a Polarion installation (P4 T2).

.DESCRIPTION
  Install:
    1. backs up polarion.properties (polarion.properties.bak-sysarch-<time>);
    2. copies the extension folder com.example.sysarch.sso into <Polarion>\polarion\extensions (replacing an older one);
    3. creates the key file <Polarion>\polarion\configuration\sysarch-sso.key if it is missing (64 random hex characters);
    4. writes two properties between marker comments, replacing an earlier block or earlier sysarch.sso.* lines:
         sysarch.sso.secretFile=<key file>   (never the key itself: Polarion logs every property at startup)
         sysarch.sso.url=<SysArch address>
    5. deletes <Polarion>\data\workspace\.config (Polarion rebuilds its plugin cache on start);
    6. checks Apache's Content-Security-Policy for frame-src and prints the edit if SysArch is missing (it does not
       change Apache's configuration);
    7. with -Restart, restarts the Polarion service; otherwise prints the command.
  Uninstall removes the extension, the block, the key file and .config (after the same backup) and prints what to undo in
  Apache. The script never prints the key; SysArch's SYSARCH_POLARION_SSO_SECRET must be set to the key file's content.

.EXAMPLE
  ./install.ps1 -SysArchUrl https://sysarch.example.com
  ./install.ps1 -SysArchUrl http://localhost:3000 -PolarionHome D:\Polarion -Restart
  ./install.ps1 -Uninstall
#>
[CmdletBinding()]
param(
    [string]$PolarionHome = 'C:\Polarion',
    [string]$SysArchUrl,
    [string]$Extension = (Join-Path $PSScriptRoot 'build\extension\com.example.sysarch.sso'),
    [switch]$Uninstall,
    [switch]$Restart
)

$ErrorActionPreference = 'Stop'
$begin = '# >>> SysArch Studio single sign-on (managed by polarion-sso/install.ps1)'
$end = '# <<< SysArch Studio single sign-on'
$config = Join-Path $PolarionHome 'polarion\configuration'
$properties = Join-Path $config 'polarion.properties'
$extensions = Join-Path $PolarionHome 'polarion\extensions'
$target = Join-Path $extensions 'com.example.sysarch.sso'
$keyFile = Join-Path $config 'sysarch-sso.key'
$cache = Join-Path $PolarionHome 'data\workspace\.config'
$apache = Join-Path $PolarionHome 'bundled\apache\conf\extra\polarion.conf'
$latin1 = [Text.Encoding]::GetEncoding(28591)   # polarion.properties is a Java properties file (ISO-8859-1)

foreach ($path in @($properties, $extensions)) {
    if (-not (Test-Path $path)) { throw "Not a Polarion installation: $path is missing (use -PolarionHome)." }
}

function Write-Properties([string]$text) {
    [IO.File]::WriteAllText($properties, $text, $latin1)
}

# The file without our block and without stray sysarch.sso.* lines; keeps its line endings.
function Remove-Block([string]$text) {
    $newline = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
    $kept = New-Object System.Collections.Generic.List[string]
    $inside = $false
    foreach ($line in ($text -split "\r?\n")) {
        if ($line -eq $begin) { $inside = $true; continue }
        if ($inside) { if ($line -eq $end) { $inside = $false }; continue }
        if ($line -match '^\s*sysarch\.sso\.(secret|secretFile|url)\s*=') { continue }
        $kept.Add($line)
    }
    while ($kept.Count -gt 0 -and $kept[$kept.Count - 1] -eq '') { $kept.RemoveAt($kept.Count - 1) }
    return @{ Text = ($kept -join $newline); Newline = $newline }
}

# Every argument is checked before any file is touched.
if (-not $Uninstall) {
    if (-not $SysArchUrl) { throw 'Give -SysArchUrl, the SysArch address as the browser reaches it (e.g. https://sysarch.example.com).' }
    $SysArchUrl = $SysArchUrl.TrimEnd('/')
    if ($SysArchUrl -notmatch '^https?://[A-Za-z0-9.-]+(:\d{1,5})?$') { throw "-SysArchUrl must be an origin like https://sysarch.example.com, not '$SysArchUrl'." }
    if (-not (Test-Path (Join-Path $Extension 'eclipse\plugins'))) { throw "No built extension at $Extension (run ./gradlew :polarion-sso:polarionExtension)." }
}

# Polarion keeps files under .config open while it runs, so its service is stopped first (with -Restart) or the script
# refuses before changing anything. Only the service of this installation counts (its program path is under -PolarionHome).
$service = Get-CimInstance Win32_Service -Filter "Name='Polarion'" -ErrorAction SilentlyContinue
$ours = $service -and $service.PathName -and $service.PathName.Replace('"', '').StartsWith((Resolve-Path $PolarionHome).Path, [StringComparison]::OrdinalIgnoreCase)
$running = $ours -and $service.State -eq 'Running'
if ($running -and -not $Restart) { throw 'Polarion is running: stop it first (Stop-Service Polarion) or add -Restart.' }
if ($running) {
    Stop-Service Polarion -Force -WarningAction SilentlyContinue
    Write-Host 'Stopped the Polarion service.'
}

$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$backup = "$properties.bak-sysarch-$stamp"
Copy-Item $properties $backup
Write-Host "Backed up polarion.properties to $backup"
$current = Remove-Block ([IO.File]::ReadAllText($properties, $latin1))

if ($Uninstall) {
    Write-Properties ($current.Text + $current.Newline)
    Write-Host 'Removed the sysarch.sso.* properties.'
    if (Test-Path $target) { Remove-Item -Recurse -Force $target; Write-Host "Removed $target" }
    if (Test-Path $keyFile) { Remove-Item -Force $keyFile; Write-Host "Removed $keyFile" }
    if (Test-Path $cache) { Remove-Item -Recurse -Force $cache; Write-Host "Removed $cache (Polarion rebuilds it on start)" }
    Write-Host "Apache: remove the frame-src entries for SysArch from the Content-Security-Policy in $apache if you added them, then restart Apache2Polarion."
} else {

    if (Test-Path $target) { Remove-Item -Recurse -Force $target }
    Copy-Item -Recurse $Extension $target
    Write-Host "Copied the extension to $target"

    if (-not (Test-Path $keyFile)) {
        $bytes = New-Object byte[] 32
        $rng = [Security.Cryptography.RandomNumberGenerator]::Create()
        $rng.GetBytes($bytes)
        $rng.Dispose()
        $hex = -join ($bytes | ForEach-Object { $_.ToString('x2') })
        [IO.File]::WriteAllText($keyFile, $hex + "`n", [Text.Encoding]::ASCII)
        Write-Host "Created the key file $keyFile (give its content to SysArch as SYSARCH_POLARION_SSO_SECRET; restrict who can read it)"
    } else {
        Write-Host "Kept the existing key file $keyFile"
    }

    $nl = $current.Newline
    $block = @($begin, ('sysarch.sso.secretFile=' + ($keyFile -replace '\\', '/')), "sysarch.sso.url=$SysArchUrl", $end) -join $nl
    Write-Properties ($current.Text + $nl + $nl + $block + $nl)
    Write-Host "Set sysarch.sso.secretFile and sysarch.sso.url=$SysArchUrl in polarion.properties"

    if (Test-Path $cache) { Remove-Item -Recurse -Force $cache; Write-Host "Removed $cache (Polarion rebuilds it on start)" }

    if (Test-Path $apache) {
        $csp = Select-String -Path $apache -Pattern 'Content-Security-Policy' | Select-Object -First 1
        if ($csp -and $csp.Line -notmatch [regex]::Escape($SysArchUrl)) {
            Write-Host ''
            Write-Host "Apache: Polarion pages may not frame SysArch yet. In $apache (line $($csp.LineNumber)) add, before frame-ancestors:"
            Write-Host "    frame-src 'self' $SysArchUrl <Sirius Web address>;"
            Write-Host '  then check with bin\httpd.exe -t and restart the Apache2Polarion service. (This script does not change Apache.)'
        } elseif ($csp) {
            Write-Host 'Apache: the Content-Security-Policy already allows framing SysArch.'
        }
    }
    Write-Host ''
    Write-Host 'SysArch: set SYSARCH_POLARION_SSO_SECRET to the key file''s content, SYSARCH_SSO_ORIGINS and SYSARCH_FRAME_ANCESTORS to the Polarion origin, and POLARION_SYNC_TOKEN (service account).'
}

if ($Restart -and $ours) {
    Start-Service Polarion
    Write-Host 'Started the Polarion service.'
} elseif ($Restart) {
    Write-Host 'No Polarion service of this installation was found; start Polarion yourself.'
} else {
    Write-Host 'Start Polarion to apply: Start-Service Polarion'
}
