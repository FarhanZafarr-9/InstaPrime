<# Copyright 2026 InstaPrime contributors. Focused checks; builds the MPP only with -Build. #>
[CmdletBinding()]
param(
    [string]$Apk = 'C:\Users\hp\Downloads\com.instagram.android_454.0.0.54.77_original.apk',
    [switch]$Build
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!(Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "Original APK not found: $Apk" }
$env:HUSHGRAM_GESTURE_APK = (Resolve-Path -LiteralPath $Apk).Path
$env:HUSHGRAM_MESSAGE_FIXTURE_APK = $env:HUSHGRAM_GESTURE_APK
if (!$env:JAVA_HOME -and (Test-Path 'C:\Program Files\Java\jdk-21.0.5+11')) {
    $env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.5+11'
}
$credentials = Join-Path $repo '.env.github-testing'
if (Test-Path -LiteralPath $credentials) {
    Get-Content -LiteralPath $credentials | ForEach-Object {
        if ($_ -match '^\s*(GITHUB_USERNAME|GH_TOKEN)\s*=\s*(.*)$') {
            $name = $matches[1]
            $value = $matches[2].Trim().Trim('"').Trim("'")
            if ($name -eq 'GITHUB_USERNAME') { $env:GITHUB_ACTOR = $value }
            else { $env:GITHUB_TOKEN = $value }
        }
    }
}
. (Join-Path $PSScriptRoot 'build-jobs.ps1')
Invoke-GradleBuild -ProjectDir $repo -Tasks @(':patches:test',
    '--tests', '*MessageGesturesHookTest', '--tests', '*ChatPlusMenuHookTest',
    '--tests', '*NativeSnackbarHookTest', '--tests', '*NativePopupPresentationTest',
    '--max-workers=1', '--offline')
if ($LASTEXITCODE -ne 0) { throw 'Native hook checks failed; stopped before the MPP build.' }
Invoke-GradleBuild -ProjectDir $repo -Tasks @(':extensions:instagram:testDebugUnitTest',
    '--tests', '*ChatPopupMenuTest', '--tests', '*StyledPopupNativeTest',
    '--tests', '*NativeSnackbarTest', '--tests', '*ProfileStatusBarTest',
    '--tests', '*SavedProfilesSettingsTest', '--tests', '*ChatPagerTest',
    '--tests', '*ProfileExtrasTest', '--tests', '*ProfilePictureTest',
    '--tests', '*MessageGesturesTest', '--tests', '*MessageActionsTest', '--tests', '*PlusMenuTest',
    '--max-workers=1', '--offline')
if ($LASTEXITCODE -ne 0) { throw 'Popup/UI checks failed; stopped before the MPP build.' }
if ($Build) {
    Invoke-GradleBuild -ProjectDir $repo -Tasks @(':patches:buildAndroid', '--max-workers=1', '--offline')
    if ($LASTEXITCODE -ne 0) { throw 'MPP build failed.' }
    Write-Host 'MPP ready in patches\build\release.'
}
