<p align="center">
  <img src="assets/instaprime-icon.png" width="96" alt="InstaPrime icon">
</p>

# InstaPrime

I made InstaPrime as a personal settings UI and branding rework of HushGram, the Instagram patch project created by SysAdminDoc.

<p align="center">
  <a href="https://morphe.software/add-source?github=FarhanZafarr-9%2FInstaPrime"><img src="https://img.shields.io/badge/Add_InstaPrime_to_Morphe-FFFFFF?style=for-the-badge" alt="Add InstaPrime to Morphe"></a>
</p>

<p align="center"><a href="https://github.com/FarhanZafarr-9/InstaPrime/releases/tag/v0.0.17">Download InstaPrime v0.0.17</a></p>

**The Instagram patches, their underlying functionality, research, compatibility work, and ongoing upstream maintenance are the work of SysAdminDoc and the upstream contributors. I do not claim credit for that work.**

My contribution is the settings interface rework and the InstaPrime name and icon. I do not claim authorship of HushGram's patches or the work that keeps them working as Instagram changes. That credit belongs to SysAdminDoc and the contributors credited by the original project.

I started this out of personal interest in interface design. It is not a critique of HushGram's original UI: everyone has their own preferences and likes different interfaces. I wanted to try a layout that suits mine, and I am sharing it for anyone who likes the same approach.

My first release is InstaPrime v0.0.17, based on HushGram 0.0.17 with 123 patches. The link adds my source to Morphe; patch and install Instagram with that source to use my redesigned interface.

## Updates and maintenance

I work on this out of personal interest and in my available time. Adapting the UI rework to new upstream patches takes time, testing, and maintenance, so updates here may sometimes be significantly delayed. I cannot promise to keep pace with every HushGram release.

If you need the latest available patches and compatibility updates promptly, please stick with the original HushGram project. Its project and Morphe source links are grouped under [Original project and support](#original-project-and-support) below. SysAdminDoc and the upstream contributors do the ongoing patch development and maintenance that this fork depends on.

## What I changed

I built on HushGram's existing settings and patch infrastructure, focusing on how its settings are presented and used:

- **Category navigation:** the settings home shows category rows that open their own pages. Larger categories have another level of grouping, with a back action and restored scroll positions.
- **Row layout:** borderless grouped rows, rounded corners, more horizontal padding, space below search and at the bottom, and fully readable descriptions. Ripples stay inside the visible row.
- **Settings colors:** wallpaper-derived Material tones on Android 12 and newer, with fallback colors on older Android versions. This describes the settings interface; it does not add wallpaper-colored backgrounds throughout Instagram.
- **Switches:** rounded tracks and circular thumbs, an on-state checkmark, animations that survive row replacement, and clearer disabled states. Temporarily unavailable options retain their saved choices.
- **Dialogs:** rounded selection indicators, tonal backgrounds for selected and unselected choices, borderless text fields, vertically stacked full-width actions, and a redesigned restart notice.
- **Section controls:** an **Enable all** switch on pages containing direct toggle options, respecting each option's availability and validation. Category pages containing further subcategories do not show it.
- **Fork information:** a section at the top with my fork's details, the original project and creator, licenses, reporting, and upstream support links.
- **Branding:** the InstaPrime name and rounded camera icon in the settings entry, shortcut, launcher option, launch screen, and optional clone badge. The clone defaults to `InstaPrime` with package `com.instagram.android.prime`.

The Instagram features exposed by these settings come from HushGram. I reorganized their controls; I did not create the patches behind them. I retained the existing setting keys and backup format for compatibility.

## Before and after

Left: the original HushGram interface. Right: the InstaPrime rework. These captures compare presentation; different switch positions in the images are not changes to feature ownership or proof of changed defaults.

### Category navigation

| HushGram — original | InstaPrime — rework |
| --- | --- |
| <img src="docs/screenshots/settings/old-1.jpg" width="300" alt="Original HushGram home with compact expandable categories"> | <img src="docs/screenshots/settings/new-1.jpg" width="300" alt="InstaPrime home with rounded category rows and setting counts"> |

### Settings rows and switches

| HushGram — original | InstaPrime — rework |
| --- | --- |
| <img src="docs/screenshots/settings/old-2.jpg" width="300" alt="Original expanded privacy settings with rectangular switch thumbs"> | <img src="docs/screenshots/settings/new-2.jpg" width="300" alt="InstaPrime privacy page with grouped rounded rows and circular switch thumbs"> |

### Selectable options

| HushGram — original | InstaPrime — rework |
| --- | --- |
| <img src="docs/screenshots/settings/old-3.jpg" width="300" alt="Original Ghost mode selection dialog with outlined choices"> | <img src="docs/screenshots/settings/new-3.jpg" width="300" alt="InstaPrime Ghost mode selection dialog with tonal rows and a full-width action"> |

### Text-entry dialogs

| HushGram — original | InstaPrime — rework |
| --- | --- |
| <img src="docs/screenshots/settings/old-4.jpg" width="300" alt="Original sharing-domain dialog with an outlined field and horizontal buttons"> | <img src="docs/screenshots/settings/new-4.jpg" width="300" alt="InstaPrime sharing-domain dialog with a filled field and stacked actions"> |

The original screenshot files are kept in [docs/screenshots/settings](docs/screenshots/settings).

## Patches and compatibility

I based this rework on HushGram **0.0.17**, whose catalog contains **123 patches** for Instagram. Features such as ad hiding, privacy controls, downloads, playback controls, and tab bar customization are inherited from upstream.

| Item | Inherited compatibility target |
| --- | --- |
| Android | Android 9 / API 28 or newer |
| Instagram package | `com.instagram.android` |
| Instagram version | `453.0.0.55.79` |
| arm64-v8a build | `385914283` |
| armeabi-v7a build | `385914282` |
| x86 / x86_64 builds | `385914284` / `385914285` |
| Morphe Manager | `2.2.0` or newer |

I inherited these compatibility targets from upstream; I have not independently repeated every upstream device test. See the [upstream guide](docs/upstream/README.md#supported-instagram-builds) and [upstream changelog](CHANGELOG.md) for the original feature and compatibility history. The changelog records HushGram's work; my changes are listed above.

## Install the UI rework

1. Install [Morphe Manager](https://github.com/MorpheApp/morphe-manager) 2.2.0 or newer.
2. [Add my source to Morphe](https://morphe.software/add-source?github=FarhanZafarr-9%2FInstaPrime), or open **Sources → + → Remote** in Manager and enter `github.com/FarhanZafarr-9/InstaPrime`.
3. Refresh the source and use InstaPrime as the only enabled Instagram patch source for this patch run.
4. Select a stock Instagram APK matching the version and build above, choose the patches you want, then patch and install.
5. Open **InstaPrime** at the top of Instagram's **Settings and activity**, or long-press the Instagram logo on Home. Choose a category to open its settings.

For a local source, download the `.mpp` from [my release](https://github.com/FarhanZafarr-9/InstaPrime/releases/tag/v0.0.17) and add it through **Sources → + → Local**. A `.mpp` is a patch bundle, not an Instagram APK.

For a separate installation, select **Install next to Instagram**. I set the clone defaults to the label **InstaPrime** and package **`com.instagram.android.prime`**. Keep the same package name and signing key for subsequent updates.

Keep a backup of your Morphe signing key. Android requires the same key to update an existing patched installation while preserving its data. A differently signed installation may require uninstalling the existing app, which deletes its local data. See the [upstream signing-key guide](docs/upstream/README.md#keep-your-signing-key).

The original HushGram releases and patch source remain available from [SysAdminDoc/HushGram](https://github.com/SysAdminDoc/HushGram). SysAdminDoc and the upstream contributors maintain those releases; my UI build is separate.

## Build from source

Use the upstream build prerequisites: JDK 17 or newer, the Android SDK, and a GitHub token with `read:packages` access for the Morphe patcher dependency. Supply credentials through environment variables; never put a token in source files or commit it.

```bash
export GITHUB_ACTOR="your-github-username"
export GITHUB_TOKEN="your-package-read-token"
./gradlew :patches:generatePatchesList --max-workers=1
./gradlew :patches:buildAndroid --max-workers=1
```

On Windows, set the variables using `$env:GITHUB_ACTOR` and `$env:GITHUB_TOKEN`, and run `gradlew.bat` instead of `./gradlew`.

Run the two build commands in the order shown. The bundle is written to `patches/build/release/patches-<version>.mpp`, alongside build verification artifacts. The documentation screenshots live outside that generated build directory so a clean build will not remove them.

I used these focused settings checks for the rework:

```bash
./gradlew :extensions:instagram:testDebugUnitTest --tests 'app.hushgram.extension.instagram.settings.SettingsCardsTest' --max-workers=1
```

My latest local run passed **44 settings tests** across Android API 28 and 37, including navigation, selection behavior, animation across row replacement, dependent options, and rendered disabled-switch contrast. The generated bundle also passed its integrity checks for 123 patches and all three non-empty DEX payloads. These checks do not replace testing a patched APK on your own device.

For the full upstream build workflow, fixture-based patch tests, and release checks, see the [upstream build guide](docs/upstream/README.md#building-from-source) and [CONTRIBUTING.md](CONTRIBUTING.md).

## Reporting problems

Report issues with my settings navigation, visuals, switches, dialogs, or branding in [my issue tracker](https://github.com/FarhanZafarr-9/InstaPrime/issues).

For an underlying patch problem, first check whether it also happens with the original HushGram build. If it does, follow the [upstream reporting guide](docs/upstream/README.md#getting-help), identify the build you used, and include the requested diagnostics. Please do not ask the original creator to support changes specific to this fork.

## Credits and support

**SysAdminDoc deserves the credit for creating HushGram and for the continuing research, development, testing, compatibility updates, and maintenance behind its patches.** The upstream contributors and the projects HushGram builds on retain their own authorship and attribution.

My contribution is the settings UI rework, navigation and control presentation, documentation, and InstaPrime branding described here. I do not take credit for creating the patch collection or for upstream maintenance. I chose a separate name to identify my fork; it does not imply endorsement by SysAdminDoc.

### Original project and support

- [HushGram source and releases](https://github.com/SysAdminDoc/HushGram)
- [SysAdminDoc, the original creator](https://github.com/SysAdminDoc)
- [Support SysAdminDoc on Ko-fi](https://ko-fi.com/X8K126YVER)
- [Add original HushGram to Morphe](https://morphe.software/add-source?github=SysAdminDoc%2FHushGram)

### Documentation and attribution

- [Original HushGram guide](docs/upstream/README.md)
- [Upstream project credits](docs/upstream/README.md#where-the-patches-come-from)
- [Source and license ledger](sources/instagram-sources.json)
- [License](LICENSE) and [notices](NOTICE)

HushGram also carries work and foundations from Hushfacebook, Hushfeed, HushPinterest, Andrew Liang's patches, Morphe, ReVanced, and other credited contributors. The references above preserve that provenance. The original notices and copyright headers remain in place.

## License

[GNU GPL v3](LICENSE), with the additional notices and requirements reproduced in [NOTICE](NOTICE). The fork retains the upstream license and attribution.

InstaPrime is not affiliated with or endorsed by Meta, Instagram, Morphe, or the original HushGram creator. Instagram and Meta are trademarks of Meta Platforms, Inc. Modified clients remain subject to Instagram's rules and account restrictions; the [upstream account-risk guidance](docs/upstream/README.md#before-you-sign-in) still applies.
