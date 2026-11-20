param(
    [string]$PolarionHome = 'C:/Polarion/polarion',
    [string]$JavaHome = 'C:/jdk-21.0.11.10-hotspot',
    [switch]$EnableForTest,
    [switch]$EnableCoordinator,
    [switch]$EnableManagedCommitTest
)
$ErrorActionPreference = 'Stop'
if ($EnableManagedCommitTest -and (-not $EnableForTest -or -not $EnableCoordinator)) { throw 'Managed commit test requires a dedicated test build and shared coordinator' }
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$outputRoot = Join-Path $workspaceRoot 'build/polarion-store/extension'
$classesPath = Join-Path $workspaceRoot 'build/polarion-store/probe-classes'
$bundlePath = Join-Path $outputRoot 'com.example.sysarch.m0/eclipse/plugins/com.example.sysarch.m0_1.0.0'
New-Item -ItemType Directory -Path $classesPath,(Join-Path $bundlePath 'META-INF') -Force | Out-Null
$sdkJars = @(Get-ChildItem (Join-Path $PolarionHome 'plugins') -Recurse -File -Filter '*.jar')
if ($sdkJars.Count -eq 0) { throw 'Polarion SDK jars not found' }
$classpath = ($sdkJars.FullName -join [IO.Path]::PathSeparator).Replace('\','/')
$sourcePath = (Join-Path $PSScriptRoot 'src/com/example/sysarch/probe/ProbeServlet.java').Replace('\','/')
$argFile = Join-Path $workspaceRoot 'build/polarion-store/probe-javac.args'
$gateSourcePath = (Join-Path $PSScriptRoot 'src/com/example/sysarch/probe/JdbcCommandGate.java').Replace('\','/')
$compilerArgs = @('--release','21','-proc:none','-encoding','UTF-8','-classpath',('"'+$classpath+'"'),'-d',('"'+$classesPath.Replace('\','/')+'"'),('"'+$sourcePath+'"'),('"'+$gateSourcePath+'"'))
[IO.File]::WriteAllLines($argFile,$compilerArgs,[Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin/javac.exe') ('@'+$argFile)
if ($LASTEXITCODE -ne 0) { throw 'Probe extension compilation failed' }
$enabled = if ($EnableForTest) { 'true' } else { 'false' }
$gateSettings = if ($EnableCoordinator) { "gateRequired=true`ngateFile="+(Join-Path $workspaceRoot 'build/private/m0-command-gate.properties').Replace('\','/')+"`n" } else { "gateRequired=false`n" }
if ($EnableCoordinator -and -not (Test-Path (Join-Path $workspaceRoot 'build/private/m0-command-gate.properties'))) { throw 'Prepare protected coordination credentials first' }
$managedCommit = if ($EnableManagedCommitTest) { 'true' } else { 'false' }
[IO.File]::WriteAllText((Join-Path $classesPath 'probe.properties'),"enabled=$enabled`nmanagedCommit=$managedCommit`nprojects=sysarch_probe_a,sysarch_probe_b`n$gateSettings",[Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin/jar.exe') --create --file (Join-Path $bundlePath 'sysarch-m0.jar') -C $classesPath .
if ($LASTEXITCODE -ne 0) { throw 'Probe extension packaging failed' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'extension/plugin.xml') -Destination $bundlePath -Force
$webInfPath = Join-Path $bundlePath 'webapp/WEB-INF'
New-Item -ItemType Directory -Path $webInfPath -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'extension/webapp/WEB-INF/web.xml') -Destination $webInfPath -Force
$manifest = @'
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: SysArch M0 dedicated test probe
Bundle-SymbolicName: com.example.sysarch.m0;singleton:=true
Bundle-Version: 1.0.0
Bundle-RequiredExecutionEnvironment: JavaSE-21
Bundle-ClassPath: sysarch-m0.jar
Require-Bundle: com.polarion.platform,com.polarion.alm.tracker,
 com.polarion.core.util,com.polarion.platform.persistence,
 com.polarion.portal.tomcat
Import-Package: javax.servlet,javax.servlet.http
Eclipse-AutoStart: true
Eclipse-BundleShape: dir

'@
if ($EnableCoordinator) {
    $driverRoot = 'C:/Users/Administrator/.gradle/caches/modules-2/files-2.1/org.postgresql/postgresql/42.7.12'
    $driver = @(Get-ChildItem $driverRoot -Recurse -File -Filter '*.jar')
    if ($driver.Count -ne 1) { throw 'Expected the existing pinned PostgreSQL JDBC driver' }
    New-Item -ItemType Directory -Path (Join-Path $bundlePath 'lib') -Force | Out-Null
    Copy-Item -LiteralPath $driver[0].FullName -Destination (Join-Path $bundlePath 'lib/postgresql.jar') -Force
    $manifest = $manifest.Replace('Bundle-ClassPath: sysarch-m0.jar','Bundle-ClassPath: sysarch-m0.jar,lib/postgresql.jar')
}
[IO.File]::WriteAllText((Join-Path $bundlePath 'META-INF/MANIFEST.MF'),$manifest,[Text.UTF8Encoding]::new($false))
[pscustomobject]@{Bundle=$bundlePath;EnabledForTest=[bool]$EnableForTest;JarSha256=(Get-FileHash (Join-Path $bundlePath 'sysarch-m0.jar') -Algorithm SHA256).Hash;Installed=$false} | ConvertTo-Json
