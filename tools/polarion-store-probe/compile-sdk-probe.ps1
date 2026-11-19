param(
    [string]$PolarionHome = 'C:/Polarion/polarion',
    [string]$JavaHome = 'C:/jdk-21.0.11.10-hotspot'
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$buildRoot = [IO.Path]::GetFullPath((Join-Path $workspaceRoot 'build'))
$outputPath = [IO.Path]::GetFullPath((Join-Path $buildRoot 'polarion-store/sdk-probe'))
if (-not $outputPath.StartsWith($buildRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Probe output must stay within the workspace build directory'
}
$pluginRoot = Join-Path (Resolve-Path -LiteralPath $PolarionHome).Path 'plugins'
$platformDirs = @(Get-ChildItem -LiteralPath $pluginRoot -Directory | Where-Object Name -Match '^com\.polarion\.platform_')
$utilityDirs = @(Get-ChildItem -LiteralPath $pluginRoot -Directory | Where-Object Name -Match '^com\.polarion\.core\.util_')
if ($platformDirs.Count -ne 1 -or $utilityDirs.Count -ne 1) { throw 'Expected exactly one target platform and utility SDK bundle' }
$platformJar = Join-Path $platformDirs[0].FullName 'platform.jar'
$utilityJars = @(Get-ChildItem -LiteralPath $utilityDirs[0].FullName -Filter '*.jar' -File)
$manifestPath = Join-Path $platformDirs[0].FullName 'META-INF/MANIFEST.MF'
$manifest = Get-Content -LiteralPath $manifestPath -Raw
$requiredRuntime = [regex]::Match($manifest, '(?m)^Bundle-RequiredExecutionEnvironment:\s*([^\r\n]+)').Groups[1].Value.Trim()
if ($requiredRuntime -ne 'JavaSE-21') { throw 'Review target SDK Java runtime before compiling this probe' }
$compiler = Join-Path $JavaHome 'bin/javac.exe'
if (-not (Test-Path -LiteralPath $compiler)) { throw 'Target javac was not found' }
New-Item -ItemType Directory -Path $outputPath -Force | Out-Null
$compileClasspath = (@($platformJar) + @($utilityJars.FullName)) -join [IO.Path]::PathSeparator
& $compiler --release 21 -encoding UTF-8 -classpath $compileClasspath -d $outputPath (Join-Path $PSScriptRoot 'SdkCompileProbe.java')
if ($LASTEXITCODE -ne 0) { throw 'SDK signature compilation failed' }
$result = [ordered]@{
    performedAt = [DateTime]::UtcNow.ToString('o')
    platformBundle = $platformDirs[0].Name
    requiredRuntime = $requiredRuntime
    signatureCompilation = 'passed'
    transactionExecuted = $false
    extensionInstalled = $false
    atomicityVerified = $false
    writesAllowed = $false
}
$result | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputPath 'result.json') -Encoding UTF8
$result | ConvertTo-Json
