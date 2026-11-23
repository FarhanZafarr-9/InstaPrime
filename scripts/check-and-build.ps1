<# Copyright 2026 InstaPrime contributors. Focused regression checks; builds the MPP only with -Build. #>
[CmdletBinding()]
param(
    [string]$Apk,
    [switch]$Build,
    [switch]$Offline
)
$ErrorActionPreference = 'Stop'
$repo = Split-Path $PSScriptRoot -Parent
if (!$Apk) {
    $compatibility = Get-Content (Join-Path $repo 'patches/src/main/kotlin/app/morphe/patches/shared/compat/AppCompatibilities.kt') -Raw
    $target = [regex]::Match($compatibility, 'INSTAGRAM_TARGET_VERSION\s*=\s*"([^"]+)"').Groups[1].Value
    if (!$target) { throw 'Could not determine the Instagram target version; supply -Apk explicitly.' }
    $downloads = Join-Path ([Environment]::GetFolderPath('UserProfile')) 'Downloads'
    $candidate = Get-ChildItem -LiteralPath $downloads -File -Filter "*$target*" |
        Where-Object { $_.Extension -in @('.apk', '.apkm', '.apks', '.xapk') } |
        Sort-Object @{ Expression = { $_.Extension -eq '.apk' }; Descending = $true }, LastWriteTime -Descending |
        Select-Object -First 1
    if (!$candidate) { throw "No original Instagram $target APK or bundle found in Downloads; supply -Apk explicitly." }
    $Apk = $candidate.FullName
}
if (!(Test-Path -LiteralPath $Apk -PathType Leaf)) { throw "Original APK not found: $Apk" }
Write-Host "Original Instagram fixture: $Apk"
$env:HUSHGRAM_GESTURE_APK = (Resolve-Path -LiteralPath $Apk).Path
$env:HUSHGRAM_MESSAGE_FIXTURE_APK = $env:HUSHGRAM_GESTURE_APK
$env:HUSHGRAM_ROW_FIXTURE_APK = $env:HUSHGRAM_GESTURE_APK
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
$gradleOptions = @('--max-workers=1')
if ($Offline) { $gradleOptions += '--offline' }
Invoke-GradleBuild -ProjectDir $repo -Tasks (@(':patches:test',
    '--tests', '*MessageGesturesHookTest', '--tests', '*ChatPlusMenuHookTest',
    '--tests', '*NativeSnackbarHookTest', '--tests', '*NativePopupPresentationTest', '--tests', '*InboxHeaderButtonsHookTest',
    '--tests', '*AccentThemeTest.suppliedApkKeepsInboxAndActivityRowHooks',
    '--tests', '*ChatBackgroundTest.theHookIsInTheExtension', '--tests', '*ChatBackgroundTest.everyWayOntoTheCallGoesThroughTheHook') + $gradleOptions)
if ($LASTEXITCODE -ne 0) { throw 'Native hook checks failed; stopped before the MPP build.' }
Invoke-GradleBuild -ProjectDir $repo -Tasks (@(':extensions:instagram:testDebugUnitTest',
    '--tests', '*ChatPopupMenuTest', '--tests', '*StyledPopupNativeTest',
    '--tests', '*NativeSnackbarTest', '--tests', '*ProfileStatusBarTest', '--tests', '*ScreenColorsAccessibilityTest',
    '--tests', '*InboxHeaderButtonsTest', '--tests', '*NewMessageButtonTest',
    '--tests', '*CallButtonsTest', '--tests', '*AskBeforeCallSettingsTest',
    '--tests', '*TabStartTest', '--tests', '*TabOrderTest', '--tests', '*HideHomeTabTest',
    '--tests', '*SavedProfilesSettingsTest', '--tests', '*ChatPagerTest',
    '--tests', '*ProfileExtrasTest', '--tests', '*ProfilePictureTest',
    '--tests', '*MessageGesturesTest', '--tests', '*MessageActionsTest', '--tests', '*PlusMenuTest',
    '--tests', '*AccentThemeTest.composeReadsTheClassicStyleBeforeAnyScreenIsStyled',
    '--tests', '*AccentThemeTest.aChatsPageBlackBackgroundTakesThePageShade',
    '--tests', '*AccentThemeTest.aChatsPageBlackHeaderTakesThePageShade',
    '--tests', '*NewRowDotsTest') + $gradleOptions)
if ($LASTEXITCODE -ne 0) { throw 'Popup/UI checks failed; stopped before the MPP build.' }
if ($Build) {
    Invoke-GradleBuild -ProjectDir $repo -Tasks (@(':patches:buildAndroid') + $gradleOptions)
    if ($LASTEXITCODE -ne 0) { throw 'MPP build failed.' }
    Write-Host 'MPP ready in patches\build\release.'
}
