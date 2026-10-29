# MynAssistant Android — 10-part audit and acceptance matrix

Audit started: 2026-10-30  
Working branch: `fix/android-full-audit-10-parts`  
Parent snapshot: `4087d5d6ca4b349acf12fa10dd2a085accc20a22` (PR #32 head)  
No changes are being made to `main` or PR #32's source branch.

## Evidence reviewed so far

- Current PR #32 metadata and changed-file diff, including the 0.6.3 / versionCode 40 changes.
- `MainActivity.kt`, `DevicePresencePolicy.kt`, `RemoteCommandSubmissionPolicy.kt`, existing unit tests, `app/build.gradle.kts`, Android workflow and `firestore.rules`.
- Related Windows settings source `SettingsWindow.xaml` and `SettingsWindow.xaml.cs` to verify its actual category names/navigation.
- The named reference images `1000008059.jpg`, `1000007992.jpg`, `1000007966.jpg` and related `1000007991.png`, `1000008050.jpg`, `1000008052.jpg`, `1000007967.jpg`.
- Prior delivery notes `MynAssistant-Stage6-Test-Notes.txt` and `MynAssistant_Proje_Teslim_Linkleri.txt`.
- The Library search did not return a related PDF in the examined items. This does not prove that no PDF exists anywhere outside the accessible results.

## Ten implementation parts

| Part | Requirements | Baseline finding | Acceptance evidence |
|---|---|---|---|
| 1 | Audit / source inventory | PR #32 is open/draft, not merged. The last inspected Actions job failed before runner steps; there are no recorded compile/test steps. | Source-based inventory, branch/PR provenance and per-item status |
| 2 | Home target selector and real device choice | Home currently still renders the robot with the text “Oto”; the menu only offers AUTO / this phone / generic PC. Firestore device records include Android and Windows records. | Selection UI, routing guards, tests and manual end-to-end follow-up |
| 3 | PC remote controls | The three screen/touchpad/keyboard toggles exist, but their row is below the page title. The touchpad already uses a grey canvas with a top-left label and no decorative dots. Delete and Enter controls already share a row. | Header geometry review; existing behavior retained; control/routing tests |
| 4 | Android Settings navigation | Settings is currently one long scrolling page. Windows Settings has real categories: Genel, Agent, Hesap, İzinler, Eklentiler, Başlangıç, Hata günlükleri, Kısayollar. | Tabs open meaningful sections; saved settings and existing controls remain intact |
| 5 | Device cards / names / presence | Editing UI and heartbeat expiry policy exist; must validate duplicate labels, edit isolation and stale/offline selection behavior. | Source and unit tests; real multi-device run when available |
| 6 | Profile and account safety | Firebase Auth and ownerId-filtered device queries exist; account isolation and Google Contacts consent require review. Drawer currently exposes a version string despite the requirement to hide it from the visible UI. | Static ownership checks, targeted tests and account-switch test when available |
| 7 | Pairing / Windows Agent / command routing | Android pairing-code UI exists. Firestore rules check ownerId on existing and new documents. Live deployment and physical end-to-end pairing remain unverified in the prior notes. | Pairing-policy tests, server/Agent review and real two-device test |
| 8 | Wake word / native libraries | PR adds LinkageError handling and MYN-WAKE-8033 plus a unit test. Actual 0.6.3 build and physical wake-word run remain unverified. | Unit tests, APK ABI inspection, real-phone audio test |
| 9 | PcFiles and remaining features | File listing/transfer contracts and tests exist; verify they remain unaffected while changing target/UI code. | Existing and new unit tests; file transfer manual acceptance when available |
| 10 | Package and delivery | PR description says no verified 0.6.3 APK/release exists; older artifacts must not be relabelled. | Successful CI build, metadata, signature, native libraries, SHA-256 and install/update checks |

## Initial image-to-requirement mapping

| Image | Evidence | Required behavior | Baseline assessment |
|---|---|---|---|
| 1000008059.jpg | White circle around the robot and “Oto” at the command field | Replace with a real plus control and open target/device selection | Not implemented in the inspected branch |
| 1000007992.jpg | Windows Account settings screen with pairing status, account and Google Contacts sections | Preserve real pairing/account status; do not expose another user's account | Screen proves visible status only; runtime ownership still needs tests |
| 1000007966.jpg | Windows Settings with horizontal category strip; current section is Account | Use its existing category names as source of truth; Android categories belong under the Settings title | Windows navigation is a left-side menu; mobile parity is not yet present |
| 1000008052.jpg | Pink annotation immediately beneath Android Settings title | Put mobile category navigation in the marked area; pink is annotation, not UI | Not implemented in the inspected branch |
| 1000008050.jpg | Blue box above screen/touchpad/keyboard icons on PC Kumandası | Move three existing controls into the upper-right header area | Controls exist, placement does not match the reference |
| 1000007991.png | Home screen command input, microphone/send and PC target visible | Preserve input, microphone and send controls; target must be explicit and correct | Generic PC/phone mode exists; multi-device binding is incomplete |
| 1000007967.jpg | Account page/photo and pairing UI | Keep profile/account UI; avoid exposing fixed developer/test identity | Visible account belongs to the active session in this screenshot; broader isolation needs tests |

## Change log

- Created this audit branch from PR #32's inspected head commit; `main` and PR #32 were not modified.
- No code requirement is marked fully complete merely because a widget or class exists. Runtime-only requirements remain unverified until a runnable test or device run produces evidence.

## Test / delivery gates

A successful source compile is not a substitute for behavior checks. Release status must stay **NOT VERIFIED** until all required checks run on the exact APK. Do not rename an old APK as 0.6.3 or report physical-device tests that were not run.
