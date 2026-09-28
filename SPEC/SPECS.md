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
  separate installs). The app remembers which mode was used last
  (`io.fidelitycard.app.data.ModePreference`) and opens straight into it,
  rather than asking on every launch - the choice is still one tap away
  via each mode's "Switch mode" action.
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
- Not designed for high-value rewards or payment-grade security. The
  threat model and key storage (§7) target low-value loyalty rewards
  (e.g. a free coffee, a small discount) — the same trust level as a
  paper punch card, with cryptography added to stop casual forgery, not
  to resist a well-resourced attacker.

## 3. Terminology

- **Issuer**: an identity, backed by a keypair, that defines one or more
  Programs and signs Stamp Tokens.
- **Program**: a loyalty scheme run by one issuer (e.g. "Joe's Coffee — 10th
  coffee free"). Has a name, a stamp threshold, a reward description, and an
  icon/color.
- **Collector**: an identity (the customer) that holds Card Instances.
- **Card Instance**: one collector's enrollment in one Program. Identified
  by a Card ID.
- **Stamp Token**: a single, uniquely-identified, signed credit toward a
  Card Instance.
- **Redemption**: the act of exchanging a full set of Stamp Tokens (≥
  threshold) for a reward, witnessed and closed out by the issuer.

## 4. Actors and modes

Only the **Issuer** side has a cryptographic identity:

- A device acting as **Issuer** generates and stores an Ed25519 keypair per
  Program it creates (a device can run multiple Programs, e.g. a market
  stall with several kiosks could still share one Program keypair if
  desired, or use one per till — that's an issuer-side operational choice,
  not a protocol constraint).
- A device acting as **Collector** holds no keypair at all. A Card
  Instance's `card_id` is a locally-generated opaque string that never
  leaves the device (SPEC §6.1) — nothing in the protocol ever needs to
  verify who the collector is, or which card a stamp belongs to: an
  issuer's signature is what makes a Stamp Token or Redemption Certificate
  real, and possessing the actual signed bytes is what makes a redemption
  valid. A Stamp Token isn't bound to any `card_id` either (§5.2) - its
  value lives in holding its bytes, the same way a physical stamp card's
  value lives in holding the card. (An earlier revision of this spec did
  give the collector a keypair, bound to a `card_id` via a signed Card
  Certificate at enrollment time — removed once it became clear nothing
  downstream ever checked that key again, so it wasn't buying real
  security; see §6.1.)

A single physical phone can hold both Issuer identities and Collector
cards. There is no server-issued account for either role — an Issuer's
identity *is* their keypair; a Collector has no identity to speak of
beyond the cards they happen to be tracking locally.

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

The program's QR (used for joining, §6.1) encodes:

```json
{
  "v": 4,
  "type": "program",
  "program_id": "...",
  "issuer_pubkey": "base64",
  "name": "Joe's Coffee",
  "threshold": 10,
  "reward": "Free coffee",
  "color": "0xFF00897B",
  "icon": "☕",
  "sig": "base64(Ed25519 signature over the above fields by issuer_privkey)"
}
```

The `sig` lets a collector's app prove, forever, that these program terms
(name, threshold, reward) were the ones the issuer actually offered at
joining time — protecting the collector against an issuer later claiming
"the deal was actually 20 stamps." It's also what pins `issuer_pubkey` for
verifying every later Stamp Token and Redemption Certificate for this
program — the *only* trust bootstrap needed; see §6.1 for why no separate
enrollment handshake or Card Certificate is needed on top of this.

`color`/`icon` are the card's visual personality (TODO.md "Product / UX")
- an ARGB int and a short string (e.g. one emoji), both opaque to the
protocol itself (just signed alongside everything else so a collector's
rendering of them can't be tampered with in transit); a UI layer defines
whatever fixed palette/icon set it offers when creating a program.

### 5.2 Stamp Token

A Stamp Token is the unforgeable unit. **Deliberately unordered**: each
stamp is an independent grant identified by a random `stamp_id`, not a
position in a per-card sequence (this was a design revision from an
earlier ordered/serial approach — see the rationale at the end of this
section). **Deliberately not bound to a collector/card id** either (a
further revision — see the rationale after that):

```json
{
  "v": 5,
  "type": "stamp",
  "program_id": "...",
  "stamp_id": "base64(16B random, chosen by the issuer at mint time)",
  "issued_at": "unix_ts",
  "sig": "base64(Ed25519 by issuer_privkey over program_id|stamp_id|issued_at)"
}
```

- `stamp_id` is what makes the token unique and is the key the collector
  deduplicates on (if it somehow receives the exact same token twice, e.g.
  re-scanning its own screenshot by mistake, it just stores it once).
- Minting a stamp is **unconditional**: the issuer signs a fresh
  `stamp_id` any time it decides to (gated only by the cashier's own
  choice to tap "Give a stamp" for a real purchase). The protocol does
  not check or enforce how many stamps have been given, or prevent the
  same purchase from producing more than one — that is treated as an
  operational/trust matter for the issuer, the same as a paper stamp card.
  What the protocol *does* cryptographically enforce is that a stamp can't
  be forged (Ed25519 verification) and can't be **redeemed** twice (§5.3).

**Why not bound to a card id (as originally specified):** an earlier
revision put `card_id` inside the signed payload, specifically so a stamp
issued for one card couldn't be grafted onto another. That protection came
at a real cost: the issuer had to *learn* the collector's `card_id` before
it could mint a valid stamp, which meant minting could only ever be a
request/response round trip (collector shows "give me a stamp for card X,"
issuer scans it, then mints and shows the reply) — never a single one-way
QR, no matter how it was arranged, because the issuer had no other channel
to learn which card it was minting for. Dropping the binding removes that
constraint: the issuer can mint and display a stamp completely
unprompted, and the collector just scans it, the same one-way pattern as
joining (§6.1). The accepted cost: a stamp's value now lives entirely in
holding its bytes, so a leaked/copied stamp (a photo, a screen recording)
is redeemable by whoever gets to redemption first, not just its original
recipient - see §7.1's threat table for exactly what this does and doesn't
expose.

**Why unordered, not a monotonic serial (as originally specified):** an
earlier revision of this spec used a per-card serial, requiring the
collector to tell the issuer its `last_accepted_serial` and the issuer to
check `serial == last_accepted + 1`. That created a real bug class: if a
stamp's QR round trip was interrupted after minting but before the
collector accepted it (failed scan, closed app), the issuer's count and
the collector's count would diverge with no way back — every subsequent
stamp request compared unequal forever. A self-healing "resend" mechanism
was built to patch this, then discarded along with the ordering entirely,
once it became clear the ordering wasn't buying anything the trust model
needed: double-issuance was never actually prevented by the serial check
either (a dishonest or confused party could simply re-present the same
"give me a stamp" request to farm unlimited stamps, since the issuer had
no way to recognize a repeat of the same request) — so the serial was
adding failure modes (the desync bug) without closing the gap it looked
like it was closing. Dropping it removes that whole bug class and is
simpler for the same threat coverage; the one thing dropped ordering costs
is the mechanism no longer detects double-issuance, which is now an
explicit operational trust boundary rather than an implicit protocol one.
It also incidentally helps the multi-till case (§7.4): two independently
minted stamps for the same card never collide the way two independently
incremented serials could.

### 5.3 Redemption Certificate

A receipt, not a range: since stamps are unordered there is no "through
serial N" to certify, and since stamps aren't bound to a collector
identity (§5.2), there's no card to certify either. The collector presents
any set of currently-held stamps whose size is `>= threshold`; the issuer
verifies every signature, checks none of the submitted stamp ids repeat
within the same submission and none were already redeemed before (its own
persisted "redeemed stamp ids" store, scoped per **program**, not per
card — an exact set, not a probabilistic one; see §7.1 for why an exact
set is the right choice here), then issues:

```json
{
  "v": 5,
  "type": "redemption",
  "program_id": "...",
  "redeemed_count": 10,
  "redeemed_at": "unix_ts",
  "redemption_id": "base64(16B random)",
  "sig": "base64(Ed25519 by issuer_privkey)"
}
```

The collector already knows exactly which stamp ids it submitted (it built
the request) and deletes those locally on a valid certificate — the
certificate only needs to confirm the count, not enumerate the ids again.
The issuer's own redeemed-stamp-ids store is what refuses to redeem the
same stamp twice **on that same device** (see §7.4 for the multi-device
caveat).

## 6. QR exchange protocols

QR is one-way (display + camera scan); a round trip needs an explicit
back-and-forth of "show QR, scan QR, show QR, scan QR." Only redemption
(§6.3) needs one, because the issuer must validate-and-confirm what it
consumed. Joining (§6.1) and stamping (§6.2) are both a single one-way
scan, no reply — the issuer never needs to learn anything from the
collector to produce either a Program Manifest or a Stamp Token.

Neither exchange is a `:crypto` message type of its own - the issuer's
screen always shows one of two small app-level containers,
`IssuerMessage.ProgramInvite` or `IssuerMessage.StampGrant`, each just
carrying already-independently-signed bytes (a Program Manifest, a Stamp
Token). A leading tag byte, distinct from both `:crypto`'s own type tags
and the collector's `CustomerMessage` tag space (§6.3), is what lets the
collector's single scanner tell which one it's looking at.

### 6.1 Joining a program

```
Issuer device                          Collector device
  [shows ProgramInvite QR]  --------->  scans it, verifies the manifest's
                                         self-signature against its own
                                         embedded issuer_pubkey (nothing to
                                         pin against except the QR's own
                                         signature - see §5.1)
                                         generates a card_id locally and
                                         starts tracking a Card Instance
                                         - done, no reply shown, no message
                                         sent back to the issuer at all
```

A `ProgramInvite` carries only the Program Manifest, no stamp - this is
the "join with no purchase" path: a poster, a sticker on the counter,
scanned out of curiosity any time, with no cashier involved at all.
`card_id` never leaves the collector's device - the issuer has no concept
of "cards it has seen" (§7). (An earlier revision of this spec had the
collector generate a keypair and send a "Join Request" the issuer would
sign into a Card Certificate before the collector trusted anything -
removed once it became clear that step verified nothing a Stamp Token's
or Redemption Certificate's own signature doesn't already cover on its
own; see §4.)

### 6.2 Stamping (award a stamp)

```
Issuer device                          Collector device
  [shows StampGrant QR]  ------------>  scans it
  { manifest_bytes, stamp_bytes }       verifies the manifest, then the
  unconditionally mints a fresh         stamp against its issuer_pubkey;
  Stamp Token (random stamp_id) the     if no Card Instance exists yet for
  moment the cashier taps "Give a       this program_id, creates one from
  stamp" for a real purchase - no       the manifest first - one scan
  scan, no input needed first           either way. Stores the stamp
                                         (keyed by its stamp_id - a
                                         re-scan of the same grant is a
                                         harmless no-op)
```

A single one-way QR either way (§5.2): the issuer never needs to learn
anything from the collector first, because a Stamp Token isn't addressed
to anyone in particular, and now neither is a `StampGrant` - it always
carries the manifest too, so a customer's very first-ever stamp needs no
separate join scan beforehand (a brand new Card Instance is created
on-the-fly from that same bundled manifest if one doesn't exist yet). This
was originally two separate steps - join once with a `ProgramInvite`, then
stamp - dropped in favor of always bundling once it became clear the only
reason a stamp needed the manifest kept separate was habit, not necessity:
the manifest is genuinely redundant on every stamp after the first for a
given card, but measured at ~140 extra bytes (a `StampGrant` is ~255-290
bytes fully bundled vs. ~120 for a bare Stamp Token; see CRYPTO_WIRE_FORMAT.md
for the exact Stamp Token/Program Manifest sizes), that's a QR roughly
2-2.4x denser (e.g. version 8 to version 13-15 at the error-correction
level a center icon needs) - well within what this app already asks
people to scan for a large redemption (§6.3, up to version ~36). Minting
is unconditional and stateless per tap - there is no count, sequence, or
card to check against, so there is nothing to fall out of sync (see §5.2
for the design revisions that got the underlying Stamp Token here, and
what it deliberately does not protect against: double-issuance is an
operational trust matter, not a protocol check, and neither is who ends up
holding a given stamp).

One Card Instance per program per device: a repeat `ProgramInvite` or
`StampGrant` for a program already joined reuses the existing card rather
than creating a duplicate.

### 6.3 Redemption

```
Collector device                       Issuer device
  [shows "Redemption Request QR"]  -->   scans it
  { program_id,
    stamp_proofs: [proof_1..proof_N] }
  (N >= threshold, all currently        for each proof, reconstructs the
   held, unredeemed stamps)             full signed Stamp Token using the
                                        request's shared program_id
                                        (§5.2.1) and verifies it, verifies
                                        no id repeats within this submission
                                        and none is in its own
                                        redeemed-stamp-ids store (checked
                                        per program, not per card - §7.1)
                                        issues Redemption Certificate,
                                        records these ids as redeemed
  scans it              <-------------   [shows Redemption Certificate QR]
  verifies sig, deletes the exact
  stamp ids it submitted
```

Each `proof` is a **Compact Stamp Proof** (CRYPTO_WIRE_FORMAT.md §5.2.1):
`stamp_id || issued_at || signature`, 88 bytes, with `program_id`
stripped out since every stamp in one redemption shares that value —
repeating it per stamp would be pure waste. This alone cuts per-stamp
cost from ~118 bytes (a full self-contained Stamp Token) to 88, roughly
doubling how large a threshold fits in one scannable QR before needing
anything fancier (v1 targets 5–20; this comfortably covers up to ~30–40).

For a redemption above that (`io.fidelitycard.app.data.
CollectorRepository`'s `LARGE_REDEMPTION_STAMP_COUNT_THRESHOLD`, currently
40), the collector's app automatically switches to a **large-threshold
redemption**: bare 16-byte stamp ids instead of 88-byte proofs (~5x
smaller again), no signature and no `issued_at` at all. Authenticity comes
from the issuer's own `IssuerMintedStamp` ledger instead of a signature
check — a stamp id this device never minted for this program simply
won't be found there. This trades the Compact Stamp Proof's self-contained
signature (verifiable with no dependency on this issuer device's own
state) for that local ledger surviving — the same shape as the existing
§7.4 multi-till limitation, which is exactly why full-signature redemption
stays the default and this only kicks in once it's actually needed. A
probabilistic commit-then-spot-check scheme (Merkle root + random sample)
was considered as an alternative and rejected: sampling `k` of `N` stamps
only catches one bad stamp hidden among the rest with probability
`~1-(N-k)/N` (e.g. ~10% with `k=10`, `N=100`), too weak for the threat
that matters.

## 7. Data model (local storage)

Local storage only (Room/SQLite), no cloud sync in v1. Suggested schema:

```
IssuerProgram(program_id PK, name, threshold, reward, privkey_alias, created_at,
              color, icon)  -- color/icon: card personalization, see §5.1
RedeemedStamp(program_id, stamp_id_hex, redeemed_at)  -- PK (program_id, stamp_id_hex),
              scoped per program, not per card - see §5.2/§7.1
IssuerMintedStamp(program_id, stamp_id_hex, minted_at)  -- PK (program_id, stamp_id_hex),
              large-threshold redemption ledger, see §6.3

CollectorCard(program_id, card_id PK, issuer_pubkey, program_name, threshold,
              reward, created_at, color, icon)  -- card_id is a
              locally-generated string that never leaves this device
              (§4); purely local bookkeeping for this app's own UI, not a
              protocol concept - color/icon copied from the Program
              Manifest at join time, see §5.1. At most one row per
              program_id (§6.1/§6.2): resolved-or-created by program_id
              on every scan, never blindly inserted
CollectorStamp(card_id, stamp_id_hex, stamp_token_bytes)  -- PK (card_id, stamp_id_hex);
              card_id here is also purely local grouping (which of my
              cards this stamp counts toward), never transmitted
```

There is no `IssuedCard` table any more: the issuer has no concept of
"cards it has seen" at all now that neither a Stamp Token nor a Redemption
Certificate carries a `card_id` (§5.2/§5.3) - the previous table only ever
recorded a card the moment it showed up in a stamp *request*, and there is
no longer any such request to observe.

`RedeemedStamp` is the issuer's exact spent-set — not a probabilistic
structure (a Bloom filter, say). At this app's actual scale (one business's
own redemptions, on its own phone) an exact set costs nothing: even 50
redemptions/day for 10 years is under 200k rows, trivial for a phone,
and it avoids the false-rejection risk a Bloom filter would introduce for
no real benefit at this scale (a Bloom filter's error direction is always
"wrongly say already-spent," never the reverse — so it never *causes*
double redemption, but it does cause wrongful denial of a genuine reward
once it starts filling up; simply unnecessary here).

Private keys (the issuer's Ed25519 seed) are stored as plain bytes in
Room, in the app's private storage — not hardware-backed via Android
Keystore. This was evaluated and deliberately deferred (not rejected
outright): native Ed25519 support in Keystore only arrived in API 33,
above this app's minSdk 26, and the realistic threat this app faces
(the value at stake is a small reward like a free coffee, not a
high-value good or payment credential) doesn't justify the added
complexity of a version-gated implementation or an AES key-wrapping
layer right now. Android's app sandboxing already prevents other apps
from reading this data on a non-rooted device; what Keystore would add
is protection against a rooted device or forensic extraction, which is
out of proportion to what's actually being protected here. Revisit if
the app ever supports higher-value rewards. See the README's security
note and `TODO.md`.

### 7.1 Threat model summary

| Threat | Mitigation |
|---|---|
| Forge a stamp without issuer's key | Ed25519 signature verification |
| Replay an old stamp QR (photo of a screen) | Deduplicated by `stamp_id` on the collector's own device; redeeming it twice is separately blocked (see below) |
| A leaked/copied stamp (photo, screen recording, a compromised collector device) is redeemed by someone other than its original recipient | **Not prevented by the protocol** — accepted by design (§5.2): a stamp isn't bound to any collector/card id, so its value lives in holding its bytes, like a physical stamp card. Whoever redeems `threshold`-many unspent copies first gets the reward; the deciding factor is redemption order, not original ownership |
| Issuer denies the deal terms after the fact | Program QR terms are signed and kept by the collector |
| Same purchase minting more than one stamp (double issuance) | **Not prevented by the protocol** — issuance is unconditional by design (§5.2/§6.2); this is an explicit operational/trust matter for the issuer, the same as a paper card, not a cryptographic guarantee |
| Redeeming the same stamp twice at the same issuer device | Issuer's `RedeemedStamp` store — exact match on `stamp_id`, scoped per program (not per card, since stamps no longer carry one) |
| Double redemption across multiple issuer devices for the same program (no sync) | **Not fully solved in v1** — documented limitation, §7.4 |
| A card's local state becomes corrupted or otherwise unrecoverable | Not automatically fixable; escape hatch is deleting the card and rejoining (collector-initiated, loses that card's progress) |
| Loss of collector's phone | Out of scope for v1 (no backup/restore yet, see §10) |
| Root/forensic extraction of the issuer's device reads the raw private key | **Not mitigated** — accepted risk, see §2's scope note; key is plain bytes in Room, not Keystore-backed |

### 7.4 Known limitation: multi-device issuers

If one Program's private key is used by several physical devices (e.g. two
tills at the same shop) without a shared "already redeemed" ledger, a
collector could in theory redeem the same set of stamps once at each till.
v1 accepts this risk for the common case (one issuer device per program)
and documents it; §11 sketches an optional lightweight sync service for
issuers who need multi-till support. (Unordered stamps, §5.2, already
remove the *other* multi-till failure mode this used to have — two tills
independently minting for the same card no longer collide the way two
independently incremented serials could, so a legitimate purchase at
either till is never lost. Only the double-*redemption* side of the
limitation remains.)

## 8. Android architecture

- **Language**: Kotlin.
- **Crypto**: Bouncy Castle (Ed25519), or Google Tink if its FOSS build
  fits F-Droid's anti-features policy — needs verification at
  implementation time.
- **QR generation/scanning**: ZXing (`zxing-android-embedded`), *not*
  ML Kit — ML Kit depends on Google Play Services, which is disqualifying
  for F-Droid.
- **Persistence**: Room over SQLite. Schema is exported (`exportSchema = true`,
  JSON checked into `app/schemas/`) starting at version 4, the first
  version with a real baseline to migrate from. Versions 1-3 predate this
  and were only ever installed pre-release, so the database builder still
  destructively wipes from any of them
  (`fallbackToDestructiveMigrationFrom(true, 1, 2, 3)`); any version bump
  from 4 onward must ship a real `Migration` (added via
  `.addMigrations(...)`) — Room throws at startup instead of silently
  wiping data if one is missing, which is the actual guardrail this
  replaces the old blanket `fallbackToDestructiveMigration(true)` with.
- **Key storage**: plain bytes in Room (app-private storage), not Android
  Keystore — see §7's note on why, and the scope this app is intended
  for.
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
├── backup/                     ← pure Kotlin/JVM library, NO Android deps
│   ├── build.gradle.kts
│   └── src/{main,test}/kotlin/...
└── app/                        ← Android app, depends on :crypto, :core, :backup
    ├── build.gradle.kts
    └── src/...
```

`:crypto`, `:core` and `:backup` are all plain Kotlin/JVM modules, not
Android library modules. Each must build and run its full test suite with
a bare JDK and no Android SDK. This is what makes the "usable elsewhere,
testable separately" goal (§0, user requirement) concrete rather than
aspirational: `:app` consumes all three as ordinary project dependencies
(`implementation(project(":crypto"))`, etc.), and nothing else in the repo
is allowed to leak into any of them.

`:core` holds the stamp/redemption business rules from §5.3 and §7.1
(`CardProgress`, `RedemptionValidator`) as plain functions over primitives
(stamp counts, stamp id strings) — deliberately with no dependency on
`:crypto` at all, so these rules stay checkable in complete isolation from
the wire format or any cryptographic concern.

`:backup` holds the encrypted backup format (BACKUP_FORMAT.md, §11 "Backup
& restore") - snapshot encoding and passphrase-based AES-GCM encryption.
It depends on `:crypto` only for `WireWriter`/`WireReader` (generic binary
framing, not the stamp-protocol message types), since a backup file is a
distinct concern from the QR wire format and deliberately isn't layered
into `:crypto`'s own message set.

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
- `:backup` depends only on `:crypto` (for `WireWriter`/`WireReader`) plus
  JUnit 5 for tests. Its encryption (§3, BACKUP_FORMAT.md) uses standard
  `javax.crypto`/`java.security` JCE - no new dependency for that at all.
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

- **`jvm-ci.yml`** — triggers on changes under `crypto/**`, `core/**` or
  `backup/**`. Just `actions/setup-java` + `./gradlew :crypto:test
  :core:test :backup:test`. Runs on JDK 17 and 21 as a cheap, continuous
  check on the "portable/reusable elsewhere" claim — all three run
  together here since all are plain Kotlin/JVM and none needs anything
  Android.
- **`app-ci.yml`** — triggers on changes under `app/**`, `crypto/**`,
  `core/**` or `backup/**` (the app depends on all three). Sets up the
  Android SDK, runs `./gradlew :app:assembleDebug :app:testDebugUnitTest
  :app:lint`.
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

- ~~**Backup & restore**~~ — resolved: full-database encrypted export/import,
  specified in [BACKUP_FORMAT.md](BACKUP_FORMAT.md) and implemented in the
  `:backup` module (§10.1) plus `BackupRepository`/`BackupScreen` in `:app`.
  AES-256-GCM with a PBKDF2-HMAC-SHA256 passphrase-derived key; a restore
  is a full replace, not a merge.
- **Optional sync service**: for issuers running multiple till devices, a
  minimal self-hostable relay that just exchanges Stamp/Redemption
  Certificates between an issuer's own devices (not a trust party — it
  never signs anything) would close the gap in §7.4.
- ~~**Revocation**~~ — considered and rejected: an issuer can already
  unilaterally refuse a card or shut down a program with no protocol
  change at all (it's the sole authority over its own redemptions); a
  signed, collector-visible `RevocationCertificate` message type was
  considered but decided against as too much machinery for this app's
  actual stakes (a free coffee, not fraud-at-scale).
- ~~**Large thresholds**~~ — resolved: Compact Stamp Proofs (§6.3/
  CRYPTO_WIRE_FORMAT.md §5.2.1) push the comfortable ceiling to ~30-40 in
  one QR; beyond that, the collector automatically switches to bare
  16-byte stamp ids verified against the issuer's own minted-stamp ledger
  instead of a signature (§6.3, §7's `IssuerMintedStamp`) - ~5x smaller
  again. Animated/fragmented QR sequences, and a probabilistic
  commit-then-spot-check scheme, were both considered and rejected (§6.3).
- **NFC as a second transport**: token formats in §5 are transport-agnostic
  (they're just signed byte blobs); NFC could reuse them unchanged.
- ~~**Token encoding**~~ — resolved: a fixed binary layout, specified in
  [CRYPTO_WIRE_FORMAT.md](CRYPTO_WIRE_FORMAT.md) with test vectors, chosen
  over CBOR/JSON since every message here is a fixed-shape record (see that
  document §1 for the reasoning).
- ~~**Why does stamping need a round trip?**~~ — resolved by removing the
  round trip: Stamp Token and Redemption Certificate dropped `card_id`
  entirely (wire v5, §5.2/§5.3). The original reason for the round trip
  was that the issuer needed the collector's `card_id` before it could
  mint a valid (card-bound) stamp; once stamps stopped being bound to any
  card, that need disappeared, and minting became a single one-way QR
  like joining. Accepted trade-off: a stamp's value now lives in holding
  its bytes, so a leaked/copied stamp can be redeemed by whoever gets
  there first, not just its original recipient (§7.1) - judged acceptable
  given this app's own stated scope (a free coffee, not a payment
  credential; see §2).
- ~~**Why does a customer's first stamp need a separate join scan?**~~ —
  resolved by removing the separate scan: `IssuerMessage.StampGrant`
  (§6.2) always bundles the Program Manifest alongside the Stamp Token, so
  the collector can create the Card Instance on the spot if one doesn't
  exist yet for that `program_id` - a brand new customer's very first
  stamp is one scan, not two. Measured before deciding, not assumed: the
  bundle costs ~140 extra bytes per stamp (a `StampGrant` is ~255-290
  bytes vs. ~120 for a bare Stamp Token), which is a real but small QR
  density increase (roughly version 8 to version 13-15 at the
  error-correction level a center icon needs) - well inside what this app
  already asks people to scan for a large redemption (§6.3, up to version
  ~36), so the size cost was not the deciding factor. `ProgramInvite`
  (manifest only, no stamp) stays available for joining without a
  purchase - a poster, scanned any time, no cashier involved.

## 12. License

**MIT**, chosen for maximum permissiveness and to ease third-party adoption
of the token format/protocol by other apps or services, at the cost of not
requiring downstream forks (e.g. a hosted sync variant, §10) to share
modifications back.
