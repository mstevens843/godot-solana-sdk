# SDK Pull Requests — Summary & Test Results

## Branches

| Branch | PR Target | Status | Commit | Test Result |
|--------|-----------|--------|--------|-------------|
| `fix/clearstate-reset-myresult` | PR #449 (open) | Waiting for CI approval | `f461ec3d` | `[ALL TESTS PASSED]` locally |
| `feature/get-capabilities` | New PR | Ready to submit | `e868c1d2` | `[ALL TESTS PASSED]` locally |
| `fix/set-identity-auth-caching` | New PR | Ready to submit | `58f5f97e` | `[ALL TESTS PASSED]` locally |

All branches are based on upstream master (`e29360da`).

---

## PR #449 — `fix/clearstate-reset-myresult`

**Subject:** fix: clearState() now resets myResult to enable disconnect/reconnect

**What it does:** After calling `connectWallet()`, the SDK caches `myResult` as `TransactionResult.Success`. Subsequent `connectWallet()` calls hit an early return and never reopen the OS wallet picker. Users cannot disconnect and reconnect or switch wallets.

**Changes:**
- `GDExtensionAndroidPlugin.kt`: Added `myResult = null` to `clearState()` + deterministic logging
- `example/WalletAdapterAndroid/flow.yaml`: Increased AUTHORIZE taps from 5 to 12 (clearState is called after every connection/signing by C++ `WalletAdapter` at `wallet_adapter.cpp` lines 221, 268, 297 — with `myResult = null`, every `connectWallet()` reopens the picker and needs an AUTHORIZE tap)

**CI status:** Runs #1528/#1529 waiting for maintainer approval. The previous attempt (#2 of run #1526) failed because it ran the original 5-tap flow.yaml — our fix commit `f461ec3d` with 12 taps was pushed after that attempt.

**Local test result:**
```
[OK]: 0
[OK]: 1
[OK]: 3
[OK]: 2
[ALL TESTS PASSED]
```

---

## PR — `feature/get-capabilities`

**Subject:** feat: add getCapabilities MWA 2.0 method

**What it does:** The MWA 2.0 spec defines `get_capabilities` as a non-privileged method that queries wallet capabilities. This method did not exist in the Godot SDK — there was no way to call it from GDScript.

**Before:** No way to query wallet capabilities from Godot. Developers must hardcode assumptions about wallet limits. MWA 2.0 spec compliance incomplete.

**After:** GDScript calls `plugin.call("getCapabilitiesWallet")`, polls `plugin.call("getCapabilitiesStatus")`, reads `plugin.call("getCapabilitiesResult")`. Returns: maxTransactions, maxMessages, supportsCloneAuth, supportsSignAndSend, supportedVersions, optionalFeatures.

**Changes (3 files, +93 lines):**

| File | Change |
|------|--------|
| `GDExtensionAndroidPlugin.kt` | Added `getCapabilitiesWallet()`, `getCapabilitiesStatus()`, `getCapabilitiesResult()` + imports for `myCapabilitiesResult`/`myCapabilitiesStatus` |
| `MyComposable.kt` | Added global vars `myCapabilitiesResult`, `myCapabilitiesStatus` + `getWalletCapabilities(sender)` composable that calls `walletAdapter.transact { getCapabilities() }` |
| `MyComponentActivity.kt` | Added `else if (myAction == 3)` branch routing to `getWalletCapabilities(sender)` |

**Tested on Solana Seeker with Phantom:**
```
maxTransactions=10
maxMessages=1
supportsCloneAuth=false
supportsSignAndSend=false
supportedVersions=legacy;0
optionalFeatures=supports_sign_and_send_transactions
```

**Local CI test result (existing tests, no regressions):**
```
[OK]: 0
[OK]: 1
[OK]: 3
[OK]: 2
[ALL TESTS PASSED]
```

---

## PR — `fix/set-identity-auth-caching`

**Subject:** fix: add setIdentity() and cache authToken to fix signing after app restart

**What it does:** After an app restart followed by a cached reconnect (where GDScript restores the session from local storage without calling `connectWallet()`), all signing operations crash with:

```
java.lang.IllegalArgumentException: If non-null, identityUri must be an absolute, hierarchical Uri
```

This happens because the Kotlin static vars (`myIdentityUri`, `myIconUri`, `myIdentityName`, `myConnectCluster`) reset to defaults (`Uri.EMPTY`, `""`, `0`) on process restart.

Additionally, `authToken` is lost on restart — only `connectWallet()` cached it, not signing operations.

**Before:**
- App restart + cached reconnect + sign anything = CRASH (`identityUri must be absolute`)
- `authToken` only set during `connectWallet()`, lost on restart
- Every sign operation after restart requires fresh wallet authorization

**After:**
- `setIdentity(cluster, uri, icon, name)` allows GDScript to initialize Kotlin identity vars on startup
- Signing works after any reconnect path
- `signTransaction` and `signTextMessage` cache `authToken` on success

**Changes (3 files, +70/-5 lines):**

| File | Change |
|------|--------|
| `GDExtensionAndroidPlugin.kt` | Added `setIdentity(cluster, uri, icon, name)` method + `clearState()` now resets `myResult` to null |
| `MyComposable.kt` | `signTransaction` success: added `authToken = result.authResult.authToken`. `signTextMessage` success: same |
| `flow.yaml` | Increased AUTHORIZE taps from 5 to 12 (needed because `clearState()` now nulls `myResult`) |

**Note:** This PR includes the `clearState()` fix from PR #449 (`myResult = null`) plus the flow.yaml tap increase. PR #449 can be closed if this PR is merged, or both can coexist since they don't conflict.

**Local CI test result:**
```
[OK]: 0
[OK]: 1
[OK]: 3
[OK]: 2
[ALL TESTS PASSED]
```

---

## Local Test Environment

All tests were run locally on macOS arm64 (Apple Silicon) using:

- Android emulator: `test30` AVD (android-30, google_apis, arm64-v8a)
- Fake wallet: `fakewallet-v1-debug.apk` v2.1.0 from solana-mobile/mobile-wallet-adapter
- Maestro CLI: v2.4.0
- Godot: 4.6.2 stable
- JDK: 17 (homebrew)
- Native `.so` files: downloaded from CI `addons` artifact (run #1526)
- AAR: built locally from each branch via `./gradlew :plugin:assembleDebug :plugin:assembleRelease`

**Test procedure for each branch:**
1. Checkout branch from upstream master (`e29360da`)
2. Apply only the branch-specific Kotlin changes
3. Build AAR with JDK 17
4. Copy AAR to `addons/SolanaSDK/WalletAdapterAndroid/bin/{debug,release}/`
5. Export test APK: `godot --headless --path . --export-debug Android tmp/app.apk`
6. Install fake wallet + test APK on emulator
7. Run `maestro --device emulator-5554 test flow.yaml`
8. Verify `adb logcat -d | grep 'ALL TESTS PASSED'`

---

## Working Example App

https://github.com/mstevens843/godot-solana-mwa-example

Demonstrates all MWA 2.0 methods working on Solana Seeker with Phantom, Seed Vault, Solflare, Backpack, and Jupiter wallets.
