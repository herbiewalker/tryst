# SPDX-License-Identifier: GPL-3.0-or-later
# ╭──────────────────────────────────────────────────────────────────╮
# │  ┌●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┐  │
# │  │                                                            │  │
# │  ●   ██╗  ██╗    ██╗    ██╗                                   ●  │
# │  │   ██║  ██║    ██║    ██║           herbiewalker            │  │
# │  │   ███████║    ██║ █╗ ██║──●──●──●──┐                       │  │
# │  │   ██╔══██║    ██║███╗██║           │                       │  │
# │  ●   ██║  ██║    ╚███╔███╔╝           ●───⏣  code · tools     ●  │
# │  │   ╚═╝  ╚═╝     ╚══╝╚══╝                    homelab         │  │
# │  │                                                            │  │
# │  └●───●───●───●───●───●───●───●───●───●───●───●───●───●───●───┘  │
# ╰──────────────────────────────────────────────────────────────────╯
# release.ps1 — the release IS this script (DevPlaybook principle 6; checklist 03 is the spec).
# Usage: .\release.ps1 -Version X.Y.Z -VersionCode N
#   -SkipBuild  : skip the release build + gates (only for re-running after a build already passed)
#   -DryRun     : do everything except commit/tag/push
param(
    [Parameter(Mandatory)][ValidatePattern('^\d+\.\d+\.\d+$')][string]$Version,
    [Parameter(Mandatory)][int]$VersionCode,
    [switch]$SkipBuild,
    [switch]$DryRun
)
$ErrorActionPreference = 'Stop'
$Tag = "v$Version"
function Fail($msg) { Write-Host "ERROR: $msg" -ForegroundColor Red; exit 1 }
function Step($msg) { Write-Host "`n== $msg ==" -ForegroundColor Cyan }

Step 'Preflight'
$Branch = git rev-parse --abbrev-ref HEAD
if ($Branch -ne 'main') { Fail "on '$Branch', not main" }
git rev-parse -q --verify "refs/tags/$Tag" 2>$null
if ($LASTEXITCODE -eq 0) { Fail "$Tag already exists" }

Step 'Version bump'
# Single source of truth: app/build.gradle.kts. Write it only if it doesn't already match —
# idempotent, so running this after a hand-edit (or a previous partial run) is safe.
$gradlePath = 'app\build.gradle.kts'
$gradleText = Get-Content $gradlePath -Raw
$wantCode = "versionCode = $VersionCode"
$wantName = "versionName = `"$Version`""
if ($gradleText -notmatch [regex]::Escape($wantCode) -or $gradleText -notmatch [regex]::Escape($wantName)) {
    $gradleText = $gradleText -replace 'versionCode = \d+', $wantCode
    $gradleText = $gradleText -replace 'versionName = "[^"]*"', $wantName
    # PS 5.1's Set-Content/Out-File add a BOM by default — write raw UTF-8 without one.
    [System.IO.File]::WriteAllText((Resolve-Path $gradlePath), $gradleText, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "Wrote versionCode=$VersionCode, versionName=$Version to $gradlePath"
} else {
    Write-Host "$gradlePath already at versionCode=$VersionCode, versionName=$Version"
}

Step 'Changelog agreement check (principle 4)'
# Three places must agree: CHANGELOG.md, the fastlane changelog for this versionCode, and the
# first (newest) entry in ReleaseNotes.kt. Not generated from one source (yet) - checked instead.
if (-not (Select-String -Path CHANGELOG.md -Pattern "\[$([regex]::Escape($Version))\].*versionCode $VersionCode" -Quiet)) {
    Fail "CHANGELOG.md has no '[$Version] ... (versionCode $VersionCode)' entry"
}
$fastlanePath = "fastlane\metadata\android\en-US\changelogs\$VersionCode.txt"
if (-not (Test-Path $fastlanePath)) { Fail "missing $fastlanePath" }
$notesPath = 'app\src\main\java\app\tryst\ui\whatsnew\ReleaseNotes.kt'
$notesText = Get-Content $notesPath -Raw
$firstEntry = [regex]::Match($notesText, 'ReleaseNote\(\s*versionName = "([^"]+)",\s*versionCode = (\d+),')
if (-not $firstEntry.Success -or $firstEntry.Groups[1].Value -ne $Version -or [int]$firstEntry.Groups[2].Value -ne $VersionCode) {
    Fail "ReleaseNotes.kt's first (newest) entry doesn't match $Version / versionCode $VersionCode"
}
Write-Host 'CHANGELOG.md, fastlane changelog, and ReleaseNotes.kt all agree.'

if (-not $SkipBuild) {
    Step 'Build + gates (release config)'
    $env:JAVA_HOME = if ($env:JAVA_HOME) { $env:JAVA_HOME } else { 'C:\Program Files\Android\Android Studio\jbr' }
    & .\gradlew.bat assembleRelease checkNoNetworkRelease ktlintCheck detekt testDebugUnitTest
    if ($LASTEXITCODE) { Fail 'build/gates failed' }
} else {
    Write-Host "`n== Build + gates SKIPPED (-SkipBuild) ==" -ForegroundColor Yellow
}

Step 'Archive R8 mapping'
$archiveDir = "archive\v$Version"
New-Item -ItemType Directory -Force -Path $archiveDir | Out-Null
$mappingSrc = 'app\build\outputs\mapping\release\mapping.txt'
if (Test-Path $mappingSrc) {
    Copy-Item $mappingSrc "$archiveDir\mapping.txt" -Force
    Write-Host "Archived mapping.txt to $archiveDir\"
} else {
    Write-Host "No mapping.txt found (unexpected after a release build) - continuing anyway." -ForegroundColor Yellow
}

if ($DryRun) {
    Write-Host "`n-DryRun set: stopping before commit/tag/push." -ForegroundColor Yellow
    exit 0
}

Step 'Commit and tag (TAG LAST)'
git add -A
git commit -m "chore(release): $Tag (versionCode $VersionCode)"
if ($LASTEXITCODE) { Fail 'commit failed (maybe nothing to commit?)' }
git tag -a $Tag -m "Release $Tag"
if ($LASTEXITCODE) { Fail 'tag failed' }

Step 'Verify tag == HEAD'
if ((git rev-list -n1 $Tag) -ne (git rev-parse HEAD)) { Fail 'tag != HEAD' }

Step 'Push'
git push origin $Branch $Tag
if ($LASTEXITCODE) { Fail 'push failed' }

Write-Host "`nDONE: $Tag at $(git rev-parse --short HEAD)." -ForegroundColor Green
Write-Host 'F-Droid: checkupdates-bot should open a fdroiddata recipe MR within ~1 day (UpdateCheckMode: Tags).'
Write-Host 'Post-release (manual): smoke-test the F-Droid build once it lands; confirm the update path from the previous version, not just a fresh install.'

# ⏣ HW ⏣ · code · tools · homelab · ⏣ HW ⏣
