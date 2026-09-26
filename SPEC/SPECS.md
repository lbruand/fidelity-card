# Fidelity Card — Specification

Status: draft
License of this spec / project: MIT (see §12)

## 0. Prior art research

Searched F-Droid and public GitHub before writing this spec, since duplicating
existing FOSS work would be wasteful.

**On F-Droid today:**

| App | What it does | Why it doesn't cover this use case |
|---|---|---|
| [Catima](https://f-droid.org/en/packages/me.hackerchick.catima/) | Wallet that stores barcodes/QR codes for loyalty cards you already have | Passive storage only; issues nothing, no crypto, no issuer role |
| [Cardabase](https://f-droid.org/en/packages/com.georgeyt9769.cardabase/) | Encrypted wallet for existing loyalty cards, can share a card via QR | Same card storage model, not a stamp-issuing protocol |
| [Loyalty Card Keychain](https://fxedel.gitlab.io/fdroid-website/packages/protect.card_locker/) | Barcode-based loyalty card storage | Same as above |

None of these have an "issuer" concept, a stamp unit, a redemption threshold,
or any cryptographic signing — they are all digitizations of a physical card
you already own, for programs run by a third party (supermarket, etc).

**Off F-Droid (GitHub, not packaged for F-Droid), closer in spirit:**

- [soboapps/LoyaltyCard](https://github.com/soboapps/LoyaltyCard) — QR/NFC
  punch-card concept, but no signed token format described.
- [danlim26/stampee](https://github.com/danlim26/stampee) — web app, requires
  a hosted backend (Supabase) to validate stamps server-side.
- [nilesh9552/digital-stamp](https://github.com/nilesh9552/digital-stamp) —
  multi-shop SaaS, server is the source of truth.
- [Rabas-dev/justbrod](https://github.com/Rabas-dev/justbrod) — cashier scans
  a customer's static QR; the QR payload itself isn't a signed, single-use
  token, so a photo of it can be replayed.

**Conclusion:** the specific combination this project targets — an
Android-native, dual-mode (issuer/collector) app where each stamp is a
unique, unforgeable, cryptographically signed token, exchanged entirely
peer-to-peer over QR codes with no mandatory backend server — does not exist
as FOSS today. This spec proceeds on that basis.

## 1. Goals

- Let any small business (or individual) become a stamp **issuer** without
  signing up for a SaaS product or running a server.
- Let a customer act as a **collector**, holding any number of cards from
  any number of issuers in one app.
- Every stamp is a unique cryptographic token: it cannot be forged,
  duplicated, or replayed by anyone without the issuer's private key.
- All exchanges (enrollment, stamping, redemption) happen via QR code,
  phone-to-phone, with no network requirement (Bluetooth/Wi-Fi/mobile data
  not needed for the core flow).
- Single Android app; a device can hold both issuer identities and collector
  cards simultaneously ("Issuer mode" / "Collector mode" are views, not
  separate installs).
- Ship as reproducible-build, FOSS-only-dependency software suitable for
  F-Droid inclusion.

## 2. Non-goals (v1)

- No requirement for a central server. (A companion sync service may be
  addressed in a later spec — see §11 — but v1 must work fully offline.)
- No payment processing, no PII collection beyond what's needed to render a
  card (issuer name, program name).
- No NFC/Bluetooth transport in v1 — QR only, to keep the trust model and
  UX simple and auditable. (NFC as an alternative transport is a future
  extension; the token format is transport-agnostic so this is low-risk to
  add later.)
- No multi-issuer-device synchronization in v1 (see §7.4 limitation).

## 3. Terminology

- **Issuer**: an identity, backed by a keypair, that defines one or more
  Programs and signs Stamp Tokens.
- **Program**: a loyalty scheme run by one issuer (e.g. "Joe's Coffee — 10th
  coffee free"). Has a name, a stamp threshold, a reward description, and an
  icon/color.
- **Collector**: an identity (the customer) that holds Card Instances.
- **Card Instance**: one collector's enrollment in one Program. Identified
  by a Card ID.
- **Stamp Token**: a single, uniquely-serialed, signed credit toward a Card
  Instance.
- **Redemption**: the act of exchanging a full set of Stamp Tokens (≥
  threshold) for a reward, witnessed and closed out by the issuer.

## 4. Actors and modes

The app has one identity keypair per role a device plays:

- A device acting as **Issuer** generates and stores an Ed25519 keypair per
  Program it creates (a device can run multiple Programs, e.g. a market
  stall with several kiosks could still share one Program keypair if
  desired, or use one per till — that's an issuer-side operational choice,
  not a protocol constraint).
- A device acting as **Collector** generates one Ed25519 keypair used to
  identify itself to issuers per Card Instance (a fresh keypair per card is
  recommended, to avoid cross-program correlation of the same customer).

A single physical phone can hold both Issuer identities and Collector
cards. There is no server-issued account for either role — identity *is*
the keypair.

## 5. Cryptographic design

Primitive: **Ed25519** (small signatures, fast verify, widely available in
FOSS libraries — e.g. Bouncy Castle — without needing Google Play Services).

Wire format: a fixed binary layout, precisely specified with byte-exact
test vectors in [CRYPTO_WIRE_FORMAT.md](CRYPTO_WIRE_FORMAT.md), implemented
in the standalone `:crypto` Kotlin/JVM module (see §10). The JSON shown
below is illustrative of the fields each message carries, not the actual
wire encoding.

### 5.1 Program identity

```
program_id   = base32( SHA-256( issuer_pubkey || program_name || nonce_16B ) )[:26]
```

The program's QR (used for enrollment) encodes:

```json
{
  "v": 1,
  "type": "program",
  "program_id": "...",
  "issuer_pubkey": "base64",
  "name": "Joe's Coffee",
  "threshold": 10,
  "reward": "Free coffee",
  "sig": "base64(Ed25519 signature over the above fields by issuer_privkey)"
}
```

The `sig` lets a collector's app prove, forever, that these program terms
(name, threshold, reward) were the ones the issuer actually offered at
enrollment time — protecting the collector against an issuer later claiming
"the deal was actually 20 stamps."

### 5.2 Card Instance / enrollment

Enrollment is a two-step QR handshake (see §6.1) that ends with the issuer
signing a **Card Certificate**:

```json
{
  "v": 1,
  "type": "card_cert",
  "program_id": "...",
  "card_id": "uuid-v4",
  "collector_pubkey": "base64",
  "issued_at": "unix_ts",
  "sig": "base64(Ed25519 by issuer_privkey over program_id|card_id|collector_pubkey|issued_at)"
}
```

`card_id` is generated by the collector app; the issuer's signature over it
binds this specific collector key to this specific card, preventing a third
party from grafting stamps meant for one collector onto another.

### 5.3 Stamp Token

A Stamp Token is the unforgeable unit. Each is monotonically serialed **per
card**, so double-issuance and reordering are detectable:

```json
{
  "v": 1,
  "type": "stamp",
  "program_id": "...",
  "card_id": "...",
  "serial": 4,
  "issued_at": "unix_ts",
  "nonce": "base64(8B random)",
  "sig": "base64(Ed25519 by issuer_privkey over program_id|card_id|serial|issued_at|nonce)"
}
```

- `serial` starts at 1 and increments by 1 per card; the collector app
  refuses to accept a stamp whose serial isn't exactly
  `last_accepted_serial + 1`, which makes replay of an old stamp QR (or
  reuse of a stamp meant for a different card) immediately detectable on
  the collector's own device.
- The nonce plus the issuer's signature is what makes the token
  "cryptographic" and unique: nobody without `issuer_privkey` can produce a
  token that verifies against `issuer_pubkey`, and a screenshot/photo of a
  token is inert once its serial has been consumed (see §6.2 — a stamp
  isn't "consumed" by viewing it, but redemption burns the whole run of
  serials, see §5.4).

### 5.4 Redemption Certificate

When a Card Instance's highest contiguous accepted serial reaches
`threshold`, the collector can present the run `[1..threshold]` for
redemption. The issuer verifies all signatures, verifies contiguity, then
issues:

```json
{
  "v": 1,
  "type": "redemption",
  "program_id": "...",
  "card_id": "...",
  "redeemed_through_serial": 10,
  "redeemed_at": "unix_ts",
  "sig": "base64(Ed25519 by issuer_privkey)"
}
```

The collector app deletes/archives stamps `1..10` locally and the card
continues accruing from serial 11. The issuer device records
`(card_id → highest redeemed_through_serial)` locally so it refuses to
re-redeem the same run twice **on that same device** (see §7.4 for the
multi-device caveat).

## 6. QR exchange protocols

QR is one-way (display + camera scan), so any handshake needs an explicit
back-and-forth of "show QR, scan QR, show QR, scan QR." All three flows
below are two round-trips.

### 6.1 Enrollment (join a program)

```
Issuer device                          Collector device
  [shows Program QR]  -------------->   scans it
                                         generates card_id + collector keypair
                                         shows "Join Request QR":
                                         { program_id, card_id, collector_pubkey, ts }
  scans it            <--------------
  verifies program_id is one it owns
  signs Card Certificate (§5.2)
  [shows Card Cert QR] -------------->   scans it, verifies sig against
                                         issuer_pubkey it already trusts
                                         (pinned from the Program QR)
                                         stores Card Instance
```

### 6.2 Stamping (award a stamp)

```
Collector device                       Issuer device
  [shows "Stamp Request QR"]  ------->   scans it
  { card_id, last_accepted_serial }
                                         looks up card_id, compares
                                         last_accepted_serial against its
                                         own issued count (see below)
  scans it             <-------------   [shows Stamp Token QR]
  verifies sig, verifies serial == last+1
  stores stamp
```

The issuer's comparison has three outcomes, not just accept/reject
(`io.fidelitycard.core.StampIssuance`):

- **Equal** — the collector is caught up: mint a genuinely new Stamp Token,
  serial = last+1.
- **Collector behind** (`last_accepted_serial` < issuer's issued count) —
  almost always means a *previous* stamp's QR round trip was interrupted
  after the issuer minted it but before the collector accepted it (the scan
  failed, the app closed, etc). Rather than refusing, the issuer resends
  the exact previously-minted token for `last_accepted_serial + 1` (kept on
  the issuer's device for this reason - see §7) so the collector catches
  up one stamp at a time, at the normal pace, without granting anything
  unearned or minting a duplicate for the same serial.
- **Collector ahead** — the collector claims more stamps than the issuer
  ever issued for this card. Not recoverable by resending anything; this is
  real inconsistency, not a lost round trip. Not automatically fixable — see
  §7.1's escape hatch.

This makes the common case (an interrupted exchange) self-healing on the
very next attempt, with no manual step. It does *not* need Bluetooth/Wi-Fi
or the two devices to reconcile out-of-band - the resend happens over the
exact same QR-exchange shape as an ordinary stamp.

### 6.3 Redemption

```
Collector device                       Issuer device
  [shows "Redemption Request QR"]  -->   scans it
  { card_id, stamps: [token_1..token_N] }
  (N >= threshold, contiguous run)       verifies every token's sig,
                                         verifies contiguity and that
                                         program_id/card_id match,
                                         verifies not already redeemed
                                         through this serial
                                         issues Redemption Certificate
  scans it              <-------------   [shows Redemption Certificate QR]
  verifies sig, archives stamps 1..N
```

Note: a Redemption Request QR embedding many stamp tokens can get large.
v1 targets thresholds in the 5–20 range, which stays within a single QR's
practical payload (a few KB at low error-correction is fine for ~20 compact
JSON/CBOR tokens); if larger thresholds are needed later, animated
(fragmented) QR sequences are the fallback (see §11).

## 7. Data model (local storage)

Local storage only (Room/SQLite), no cloud sync in v1. Suggested schema:

```
IssuerProgram(program_id PK, name, threshold, reward, privkey_alias, created_at)
IssuedCard(program_id, card_id PK, collector_pubkey, issued_serial_count,
           redeemed_through_serial, created_at)
IssuedStamp(program_id, card_id, serial, stamp_token_bytes)  -- PK (program_id, card_id, serial)

CollectorCard(program_id, card_id PK, issuer_pubkey, program_name, threshold,
              reward, collector_privkey_alias, last_accepted_serial,
              created_at)
CollectorStamp(card_id, serial, issued_at, nonce, sig)  -- PK (card_id, serial)
```

`IssuedStamp` is what makes the resend in §6.2 possible: the issuer keeps
every Stamp Token it has ever minted for a card (not just the count), so a
collector who fell behind can be caught up with the exact token they
missed rather than a freshly minted one.

Private keys are stored via Android Keystore (hardware-backed where
available), never exported in plaintext; `privkey_alias` is a Keystore
alias, not the key material itself.

### 7.1 Threat model summary

| Threat | Mitigation |
|---|---|
| Forge a stamp without issuer's key | Ed25519 signature verification |
| Replay an old stamp QR (photo of a screen) | Per-card monotonic serial, collector rejects non-`last+1` |
| Graft a stamp issued for card A onto card B | `card_id` is inside the signed payload |
| Issuer denies the deal terms after the fact | Program QR terms are signed and kept by the collector |
| Collector claims more stamps than issued | Issuer's own `issued_serial_count` is authoritative for what *it* will redeem; collector-side state is just a client cache |
| Double redemption at the same issuer device | Issuer tracks `redeemed_through_serial` per card |
| Double redemption across multiple issuer devices for the same program (no sync) | **Not fully solved in v1** — documented limitation, §7.4 |
| Collector falls behind the issuer's count (interrupted stamp round trip) | Self-healing: issuer resends the already-minted stamp instead of refusing, see §6.2 |
| Collector claims to be *ahead* of what the issuer ever issued (genuine corruption, not a lost round trip) | Not automatically fixable; escape hatch is deleting the card and rejoining (collector-initiated, loses that card's progress) |
| Loss of collector's phone | Out of scope for v1 (no backup/restore yet, see §10) |

### 7.4 Known limitation: multi-device issuers

If one Program's private key is used by several physical devices (e.g. two
tills at the same shop) without a shared "already redeemed" ledger, a
collector could in theory redeem the same run of stamps once at each till.
v1 accepts this risk for the common case (one issuer device per program)
and documents it; §11 sketches an optional lightweight sync service for
issuers who need multi-till support.

## 8. Android architecture

- **Language**: Kotlin.
- **Crypto**: Bouncy Castle (Ed25519), or Google Tink if its FOSS build
  fits F-Droid's anti-features policy — needs verification at
  implementation time.
- **QR generation/scanning**: ZXing (`zxing-android-embedded`), *not*
  ML Kit — ML Kit depends on Google Play Services, which is disqualifying
  for F-Droid.
- **Persistence**: Room over SQLite.
- **Key storage**: Android Keystore.
- **Min/target SDK**: TBD at implementation time; no Play Services
  dependency anywhere in the dependency tree.
- **UI**: Jetpack Compose.
- No analytics, no ads, no network permission required for core
  functionality (a network permission would only be needed by the optional
  future sync feature in §10, and should be a separate build flavor or
  clearly gated so the core app can be verified to work fully offline).

## 9. F-Droid compliance checklist

- [ ] No proprietary blobs / non-free dependencies (verify every transitive
      dependency, including crypto and QR libraries).
- [ ] No tracking/analytics SDKs (F-Droid "anti-features" list).
- [ ] Reproducible build.
- [ ] OSI-approved license in repo root (`LICENSE`), matching what's
      declared in the F-Droid metadata.
- [ ] No mandatory network access for core functionality.

## 10. Build system, dependency management, and CI/CD

### 10.1 Module layout

Gradle multi-module project, Kotlin throughout (app and library, per
project decision — Kotlin is the officially recommended, Compose-required
language for Android, and the crypto library gains from null-safety and
sealed classes when modeling signed-message types):

```
fidelity-card/
├── settings.gradle.kts
├── gradle/libs.versions.toml   ← version catalog, single source of truth
├── gradlew, gradlew.bat, gradle/wrapper/...
├── crypto/                     ← pure Kotlin/JVM library, NO Android deps
│   ├── build.gradle.kts
│   └── src/{main,test}/kotlin/...
├── core/                       ← pure Kotlin/JVM library, NO Android deps
│   ├── build.gradle.kts
│   └── src/{main,test}/kotlin/...
└── app/                        ← Android app, depends on :crypto and :core
    ├── build.gradle.kts
    └── src/...
```

`:crypto` and `:core` are both plain Kotlin/JVM modules, not Android
library modules. Each must build and run its full test suite with a bare
JDK and no Android SDK. This is what makes the "usable elsewhere, testable
separately" goal (§0, user requirement) concrete rather than aspirational:
`:app` consumes both as ordinary project dependencies
(`implementation(project(":crypto"))`, `implementation(project(":core"))`),
and nothing else in the repo is allowed to leak into either.

`:core` holds the stamp/redemption business rules from §5.3-§5.4, §6.2 and
§7.1 (`CardProgress`, `StampLedger`, `RedemptionValidator`,
`StampIssuance`) as plain functions over primitives (serials, counts) —
deliberately with no dependency on `:crypto` at all, so these rules stay
checkable in complete isolation from the wire format or any cryptographic
concern.

### 10.2 Dependency management

- A single **version catalog** (`gradle/libs.versions.toml`) declares every
  dependency and version once; both modules reference catalog entries
  (`libs.bouncycastle`, `libs.junit`, etc.) instead of inline version
  strings. Makes the full dependency tree auditable at a glance, which
  matters for the F-Droid compliance checklist (§9).
- `:crypto` dependencies are kept deliberately minimal: **Bouncy Castle**
  (Ed25519 signing) as the only runtime dependency, JUnit 5 for tests. The
  canonical CBOR encoder (§5, wire format) is hand-rolled rather than
  pulled from a general-purpose serialization library, since exact,
  deterministic byte output on every platform is the entire point of the
  format and shouldn't depend on a third-party library's own
  serialization-order behavior.
- `:core` has no runtime dependency at all (not even on `:crypto`), just
  JUnit 5 for tests — see §10.1.
- `:app` dependencies: AndroidX, Jetpack Compose, Room, ZXing
  (`zxing-android-embedded`, not ML Kit — see §8). No Google Play Services
  anywhere in the tree.
- Not doing yet: publishing `:crypto` or `:core` as standalone artifacts
  (Maven Central / JitPack) for consumption by other repos or non-Kotlin-JVM
  projects. Being a clean, dependency-light, independently-tested module
  already satisfies "usable elsewhere" for now; publishing is a separate
  decision to revisit once an actual external consumer exists.

### 10.3 CI/CD

Two independent GitHub Actions workflows, split so the pure-JVM modules'
CI never needs an Android SDK or emulator:

- **`jvm-ci.yml`** — triggers on changes under `crypto/**` or `core/**`.
  Just `actions/setup-java` + `./gradlew :crypto:test :core:test`. Runs on
  JDK 17 and 21 as a cheap, continuous check on the "portable/reusable
  elsewhere" claim — `:crypto` and `:core` run together here since both
  are plain Kotlin/JVM and neither needs anything the other pulls in.
- **`app-ci.yml`** — triggers on changes under `app/**`, `crypto/**` or
  `core/**` (the app depends on both). Sets up the Android SDK, runs
  `./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lint`.
- A dependency-tree guardrail step (`./gradlew :app:dependencies` checked
  against a known Play-Services/tracker denylist) to catch an accidental
  F-Droid-disqualifying dependency before it lands.
- Deliberately not replicated here: F-Droid's own build server. Once the
  app is submitted to F-Droid, their reproducible rebuild is itself an
  independent CI signal; the job of this repo's CI is only to avoid doing
  anything incompatible with that (no network access mid-build, no
  non-reproducible timestamps/paths, wrapper checked in). Release signing
  and F-Droid metadata submission are out of scope until v1 is functionally
  complete.

## 11. Open questions / future work

- **Backup & restore**: losing a phone currently means losing all
  collector cards and issuer program keys. Needs a spec for encrypted
  export/import (e.g. to a file the user controls) before v1 ships.
- **Optional sync service**: for issuers running multiple till devices, a
  minimal self-hostable relay that just exchanges Stamp/Redemption
  Certificates between an issuer's own devices (not a trust party — it
  never signs anything) would close the gap in §7.4.
- **Revocation**: what happens if an issuer needs to revoke a card (fraud)
  or a whole program (going out of business)? Needs a signed revocation
  message type.
- **Large thresholds**: fragmented/animated QR sequences if thresholds
  much larger than ~20 stamps are needed.
- **NFC as a second transport**: token formats in §5 are transport-agnostic
  (they're just signed byte blobs); NFC could reuse them unchanged.
- ~~**Token encoding**~~ — resolved: a fixed binary layout, specified in
  [CRYPTO_WIRE_FORMAT.md](CRYPTO_WIRE_FORMAT.md) with test vectors, chosen
  over CBOR/JSON since every message here is a fixed-shape record (see that
  document §1 for the reasoning).

## 12. License

**MIT**, chosen for maximum permissiveness and to ease third-party adoption
of the token format/protocol by other apps or services, at the cost of not
requiring downstream forks (e.g. a hosted sync variant, §10) to share
modifications back.
