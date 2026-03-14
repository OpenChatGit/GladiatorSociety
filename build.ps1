# GladiatorSociety Build Script
# Compiles all Java sources and packages them into jars/GladiatorSociety.jar

$JAVAC = "C:\Program Files\Zulu\zulu-17\bin\javac.exe"
$JAR   = "C:\Program Files\Zulu\zulu-17\bin\jar.exe"

$SS_CORE = "C:\Program Files (x86)\Fractal Softworks\Starsector\starsector-core"
$SS_MODS = "C:\Program Files (x86)\Fractal Softworks\Starsector\mods"

# Classpath: Starsector API + common libs + LazyLib if present
$CP_ENTRIES = @(
    "$SS_CORE\starfarer.api.jar",
    "$SS_CORE\starfarer_obf.jar",
    "$SS_CORE\fs.common_obf.jar",
    "$SS_CORE\log4j-1.2.9.jar",
    "$SS_CORE\xstream-1.4.10.jar",
    "$SS_CORE\json.jar",
    "$SS_CORE\lwjgl.jar",
    "$SS_CORE\lwjgl_util.jar"
)

# Add LazyLib if installed
$lazylib = Get-ChildItem "$SS_MODS\LazyLib\jars\LazyLib.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (!$lazylib) {
    $lazylib = Get-ChildItem "$SS_MODS\lw_lazylib\jars\*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
}
if ($lazylib) {
    $CP_ENTRIES += $lazylib.FullName
    Write-Host "LazyLib found: $($lazylib.FullName)"
} else {
    Write-Host "WARNING: LazyLib not found in mods folder, skipping."
}

# Add RAT (required dependency)
$ratJar = Get-ChildItem "$SS_MODS\Random Assortment of Things*\jars\RandomAssortmentofThings.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if ($ratJar) {
    $CP_ENTRIES += $ratJar.FullName
    Write-Host "RAT found: $($ratJar.FullName)"
} else {
    Write-Error "ERROR: Random Assortment of Things JAR not found! RAT is a required dependency."
    exit 1
}

$CLASSPATH = $CP_ENTRIES -join ";"

$BUILD_DIR = "build\classes"
$JAR_OUT   = "jars\GladiatorSociety.jar"

# Collect all .java source files (src/ and com/)
$SOURCES = @(
    Get-ChildItem "src" -Recurse -Filter "*.java" | Where-Object { $_.Name -ne "App.java" -and $_.Name -ne "GladiatorSociety_test.java" }
    Get-ChildItem "com" -Recurse -Filter "*.java"
) | ForEach-Object { $_.FullName }

if ($SOURCES.Count -eq 0) {
    Write-Error "No source files found!"
    exit 1
}

Write-Host "Found $($SOURCES.Count) source files."

# Clean and recreate build dir
if (Test-Path $BUILD_DIR) { Remove-Item $BUILD_DIR -Recurse -Force }
New-Item -ItemType Directory -Path $BUILD_DIR | Out-Null

# Write sources to a file list (avoids command line length limits)
$SOURCES_FILE = "build\sources.txt"
New-Item -ItemType Directory -Path "build" -Force | Out-Null
$SOURCES | Set-Content $SOURCES_FILE

Write-Host "Compiling..."
& $JAVAC -source 8 -target 8 -encoding UTF-8 -cp $CLASSPATH -d $BUILD_DIR "@$SOURCES_FILE"

if ($LASTEXITCODE -ne 0) {
    Write-Error "Compilation failed!"
    exit 1
}

Write-Host "Compilation successful. Packaging JAR..."

# Package into JAR
Push-Location $BUILD_DIR
& $JAR cf "..\..\$JAR_OUT" .
Pop-Location

if ($LASTEXITCODE -ne 0) {
    Write-Error "JAR packaging failed!"
    exit 1
}

Write-Host ""
Write-Host "Build complete: $JAR_OUT"
Write-Host "Classes in JAR:"
& $JAR tf $JAR_OUT | Where-Object { $_ -like "*.class" } | Measure-Object | Select-Object -ExpandProperty Count | ForEach-Object { Write-Host "  $_  .class files" }
