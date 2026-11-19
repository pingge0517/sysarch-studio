param(
    [string]$PolarionHome = 'C:/Polarion/polarion',
    [string]$JavaHome = 'C:/jdk-21.0.11.10-hotspot'
)
$ErrorActionPreference = 'Stop'
$workspaceRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$buildPath = Join-Path $workspaceRoot 'build/polarion-model-store'
$classesPath = Join-Path $buildPath 'classes'
$bundlePath = Join-Path $buildPath 'extension/com.example.sysarch.model/eclipse/plugins/com.example.sysarch.model_1.0.0'
New-Item -ItemType Directory -Path $classesPath,(Join-Path $bundlePath 'META-INF'),(Join-Path $bundlePath 'webapp/WEB-INF') -Force | Out-Null
$sdkJars = @(Get-ChildItem (Join-Path $PolarionHome 'plugins') -Recurse -File -Filter '*.jar')
if ($sdkJars.Count -eq 0) { throw 'Polarion SDK jars not found' }
$classpath = ($sdkJars.FullName -join [IO.Path]::PathSeparator).Replace('\','/')
$sourcePath = (Join-Path $PSScriptRoot 'src/com/example/sysarch/polarion/model/IdentityServlet.java').Replace('\','/')
$argFile = Join-Path $buildPath 'javac.args'
$compilerArgs = @('--release','21','-proc:none','-encoding','UTF-8','-classpath',('"'+$classpath+'"'),'-d',('"'+$classesPath.Replace('\','/')+'"'),('"'+$sourcePath+'"'))
[IO.File]::WriteAllLines($argFile,$compilerArgs,[Text.UTF8Encoding]::new($false))
& (Join-Path $JavaHome 'bin/javac.exe') ('@'+$argFile)
if ($LASTEXITCODE -ne 0) { throw 'Model identity extension compilation failed' }
& (Join-Path $JavaHome 'bin/jar.exe') --create --file (Join-Path $bundlePath 'sysarch-model.jar') -C $classesPath .
if ($LASTEXITCODE -ne 0) { throw 'Model identity extension packaging failed' }
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'extension/plugin.xml') -Destination $bundlePath -Force
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'extension/webapp/WEB-INF/web.xml') -Destination (Join-Path $bundlePath 'webapp/WEB-INF') -Force
$manifest = @'
Manifest-Version: 1.0
Bundle-ManifestVersion: 2
Bundle-Name: SysArch model read identity
Bundle-SymbolicName: com.example.sysarch.model;singleton:=true
Bundle-Version: 1.0.0
Bundle-RequiredExecutionEnvironment: JavaSE-21
Bundle-ClassPath: sysarch-model.jar
Require-Bundle: com.polarion.platform,com.polarion.alm.tracker,
 com.polarion.platform.persistence,com.polarion.portal.tomcat
Import-Package: javax.servlet,javax.servlet.http
Eclipse-AutoStart: true
Eclipse-BundleShape: dir

'@
[IO.File]::WriteAllText((Join-Path $bundlePath 'META-INF/MANIFEST.MF'),$manifest,[Text.UTF8Encoding]::new($false))
[pscustomobject]@{Bundle=$bundlePath;JarSha256=(Get-FileHash (Join-Path $bundlePath 'sysarch-model.jar') -Algorithm SHA256).Hash;Installed=$false} | ConvertTo-Json
