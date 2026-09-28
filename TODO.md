TODO
====

## Product / UX

 * [x] Make the app more simple by treating the collector/issuer mode as settings and
       not each time: done. `ModePreference` (plain SharedPreferences) remembers the
       last-used mode and the app now opens straight into `BusinessListScreen` /
       `CardListScreen`, skipping `HomeScreen`'s fork after the first launch. The
       fork is still reachable any time via a "Switch mode" action in each list
       screen's top bar - a device can still use both modes (SPEC/SPECS.md §4),
       this only changes the default landing screen.
 * [x] Create a logo for the app. Maybe we a little stylized stamp: done - a
       rounded-square stamp outline with a checkmark inside, on a solid
       teal background (`app/src/main/res/drawable/ic_launcher_*.xml`,
       `mipmap-anydpi-v26/ic_launcher*.xml`). Adaptive icon only (no legacy
       raster mipmaps needed - minSdk 26 is exactly when adaptive icons
       became universal), includes a monochrome layer for Android 13+
       themed icons. Also shown in-app on `HomeScreen` for brand
       consistency, not just as the launcher icon.
 * [x] We should be able to give a little more personnality to the card (like a
       stamp style, colors, a logo etc...): done, as color + icon/emoji. An
       issuer picks both from a small fixed palette/set
       (`io.fidelitycard.app.ui.CardStyle`) when creating a business; both
       travel inside the signed `ProgramManifest` (wire format bumped
       3 -> 4, CRYPTO_WIRE_FORMAT.md §5.1) so the collector's card view
       shows the same personalization, not just the issuer's own list.
       Rendered as a small badge (`CardStyleBadge`) on both list screens.
       DB version bumped 5 -> 6 (`MIGRATION_5_6`), backup format bumped
       2 -> 3 (`IssuerProgramRow`/`CollectorCardRow`). Free-form colors/a
       custom image upload were considered and rejected as more than this
       app needs - a fixed palette keeps the picker a two-tap choice and
       every rendering a single simple badge shape.
 * [x] There should be a bgcolor for the fidcard. The icon and the (text)color
       and bgcolor should reused across the gui. We should use the icon as the
       stamp itself, instead of using the whitetick mark. We should also show
       the icon and color on the QRCode (icon in the center of the QRcode):
       done.
         - List items (`BusinessListScreen`/`CardListScreen`) and detail
           screens (`CardColorHeader`) now use the program's own `color` as a
           full background fill, white text/icon on top.
         - `StampProgressDots` renders a filled stamp as the program's own
           `icon` on a `color`-filled circle, replacing the generic
           checkmark - "the icon as the stamp itself." Shown inside a
           translucent `ColorSurfaceTray` wherever it sits on an
           already-colored surface, so the dots don't blend into the
           background behind them.
         - `QrCodec`/`QrDisplay` gained `foregroundColor`/`centerIcon`
           support: QR modules tinted with the program's color everywhere,
           plus the icon rendered in a small white badge at the QR's
           center for the issuer's Program Manifest/Stamp Token/Redemption
           Certificate QRs and the collector's Redemption Certificate QR.
           Center icon deliberately **not** added to the collector's
           outgoing redemption *request* QR - that payload's size already
           depends on how many stamps are being redeemed (up to the
           large-threshold fallback, SPEC §6.3), and a center logo needs
           high error correction, which costs real capacity; tint-only
           there to avoid regressing scannability. `CardStyle.colors`
           (the fixed palette) is all mid/dark-toned Material swatches,
           chosen so white text/icon overlays stay legible on every one.
         - No protocol/schema changes - purely a `:app`/UI-layer change,
           `color`/`icon` were already signed into the Program Manifest.

## From the spec (SPEC/SPECS.md)

 * [x] Backup & restore / export-import of the config: done. Full-database
       encrypted export/import - AES-256-GCM, PBKDF2-HMAC-SHA256
       passphrase-derived key, format specified in `SPEC/BACKUP_FORMAT.md`
       and implemented in a new `:backup` module (pure Kotlin/JVM, TDD'd)
       plus `BackupRepository`/`BackupScreen` in `:app` (reachable from
       Home). Restore is a full replace, not a merge - simplest safe
       behavior given there's no conflict-resolution model between two
       independently-evolved states.
 * [x] Store issuer key material in the Android Keystore instead of as raw
       bytes in Room: discussed and deliberately deferred, not implemented.
       Native Ed25519 in AndroidKeyStore only arrived in API 33, above this
       app's minSdk 26; an AES key-wrapping fallback would work on every
       supported version but adds real complexity for a threat level this
       app doesn't need — the value at stake is a small reward (free
       coffee), not a payment credential. Documented instead: SPEC §2/§7
       now spell out the scope/threat model explicitly, the README has a
       security note, and the "New business card" screen shows an
       in-app hint. Revisit only if the app ever supports higher-value
       rewards. (Note: this item used to say "issuer/collector" - stale,
       the collector keypair was removed entirely when the enrollment
       handshake was cut; only issuer keys exist now.)
 * [x] Replace Room's `fallbackToDestructiveMigration` with a real `Migration`
       before any actual release: done as a guardrail, not as an actual
       migration (there's no schema change to migrate yet). Schema export
       turned on (`exportSchema = true`), `app/schemas/` now holds the
       version-4 baseline JSON. The database builder now only allows a
       destructive wipe for versions 1-3 (pre-baseline, pre-release only —
       `fallbackToDestructiveMigrationFrom(true, 1, 2, 3)`); any future
       version bump must come with a real `Migration` or Room throws at
       startup instead of silently wiping data (SPEC §8).
 * [ ] Optional lightweight sync service for issuers running more than one till
       device, to close the multi-device double-redemption gap (SPEC §7.4/§11).
       Not needed until someone actually wants multi-till support.
 * [x] ~~Revocation~~ — rejected: too much machinery for this app's actual
       stakes (a free coffee, not fraud-at-scale). An issuer already has
       everything needed to refuse a card or shut down a program
       unilaterally without any protocol change (it's the sole authority
       over its own redemptions); a signed, collector-visible
       `RevocationCertificate` was considered but decided against as not
       worth the wire-format weight for this app.
 * [x] Large reward thresholds (much more than ~20 stamps): a single QR's payload
       gets tight (each StampToken is ~156 bytes; a QR tops out around 2-3KB) —
       explored in conversation, decided against fragmented/animated QR
       sequences (real scanning-UX cost) in favor of, in order:
         1. [x] Done: de-duplicated `program_id`/`card_id` inside RedemptionRequest
            via a new Compact Stamp Proof format (`io.fidelitycard.crypto.
            StampToken.toCompactProofBytes`/`parseAndVerifyCompactProof`,
            CRYPTO_WIRE_FORMAT.md §5.2.1) - 156 -> 88 bytes/stamp, zero trust
            cost (wrong program_id/card_id reconstructs different signed bytes
            and just fails verification). Roughly doubles the threshold that
            fits in one QR (~20 -> ~30-40).
         2. [x] Done: an issuer-side ledger of minted stamp ids
            (`IssuerMintedStampEntity`/`IssuerMintedStampDao`, DB version 5,
            `MIGRATION_4_5`) so a large-threshold redemption
            (`CustomerMessage.LargeRedemptionRequest`) can send raw 16-byte
            stamp ids instead of full proofs, verified against that ledger
            (`IssuerRepository.handleLargeRedemption`) instead of
            re-checking signatures - ~5x smaller again than a compact
            proof. `CollectorRepository.buildRedemptionRequest` switches to
            this mode automatically above
            `LARGE_REDEMPTION_STAMP_COUNT_THRESHOLD` (40 stamps); included
            in the backup/restore format (`IssuerMintedStampRow`,
            BACKUP_FORMAT.md bumped to format version 2). Trade-off:
            redemption now depends on the issuer device's own local state
            surviving - same shape as the existing §7.4 multi-till
            limitation - which is why full-signature redemption stays the
            default for normal thresholds.
         3. Considered and rejected as the primary fix: a probabilistic
            commit-then-spot-check scheme (Merkle root + random sample of k
            stamps). The math doesn't work for the threat that matters - hiding
            one bad stamp among N and sampling only k catches it with
            probability ~1-(N-k)/N, e.g. only ~10% with k=10, N=100. Could
            still be a deliberate, documented extra layer on top of (2) given
            how low-stakes this app is (a free coffee, not a payment), but not
            a substitute for it.
       (SPEC §6.3/§11)
 * [x] ~~Why does stamping need a round trip?~~ — resolved by removing the round
       trip: Stamp Token and Redemption Certificate dropped `card_id` entirely
       (wire format bumped 4 -> 5, CRYPTO_WIRE_FORMAT.md §5.2/§5.3, SPEC
       §4/§5.2/§11). The issuer used to need the collector's `card_id` before
       it could mint a valid (card-bound) stamp, which forced minting to be a
       request/response round trip; once stamps stopped being bound to any
       card, minting became a single one-way QR (issuer mints and shows,
       collector scans, done) - `CustomerMessage.StampRequest` is gone,
       `IssuerRepository.mintStamp`/`giveStamp` replaces the old scan-triggered
       `handleStamp`, and the collector's "Get a stamp" screen is now a plain
       scanner (`GetStampState`) instead of a two-step show/scan exchange.
       `RedeemedStamp`/`IssuerMintedStamp` are now scoped per program, not per
       card (DB version 6 -> 7, `MIGRATION_6_7`, drops `issued_cards` entirely
       and rebuilds both tables without their `cardId` column/PK segment);
       backup format bumped 3 -> 4 to match. Accepted trade-off: a stamp's
       value now lives in holding its bytes, so a leaked/copied stamp is
       redeemable by whoever gets to redemption first, not just its original
       recipient - judged acceptable given this app's own stated scope (a free
       coffee, not a payment credential).
 * [x] ~~Why does a customer's first stamp need a separate join scan?~~ —
       resolved by removing the separate scan: the issuer's "give a stamp"
       QR (`IssuerMessage.StampGrant`, `app/src/main/kotlin/io/fidelitycard/
       app/qr/IssuerMessages.kt`) now always bundles the Program Manifest
       alongside the Stamp Token, so `CollectorRepository.acceptIssuerMessage`
       can create the Card Instance on the spot (via a new
       `CollectorCardDao.findByProgramId` lookup) if this device doesn't
       have one yet for that program - a brand new customer's very first
       stamp is one scan, not two. Superseded `JoinBusinessScreen`/
       `StampFlowScreen` with one unified `ScanBusinessScreen`
       (`ScanBusinessViewModel`/`ScanBusinessOutcome`), reachable both from
       the card list's "+" (no business assumed) and an existing card's
       "Get a stamp" (rejects a scan for any other business, via an
       `expectedCardId`). `ProgramInvite` (manifest only, no stamp) stays
       available for joining with no purchase, e.g. a poster. One Card
       Instance per program per device from here on (a repeat scan for a
       program already joined reuses it, never duplicates it).
       No `:crypto`/wire-version change - `IssuerMessage` is an app-level
       container, like `CustomerMessage`. Measured, not assumed, before
       deciding this was worth it: a `StampGrant` is ~255-290 bytes vs
       ~120 for a bare Stamp Token, which needs a real-but-modest denser
       QR (roughly version 8 to 13-15 at the error-correction level a
       center icon needs) - well inside what this app already asks people
       to scan for a large redemption (up to version ~36, SPEC §6.3), so
       size was not the deciding factor either way (SPEC §6.1/§6.2/§11).
 * [x] ~~Rescanning an already-redeemed stamp~~ — fixed a real bug (user-reported):
       the collector deleted a stamp from `CollectorStampEntity` on redemption but
       never recorded that it *had been* redeemed, so re-scanning the same stamp
       QR afterward (a screenshot, the issuer's screen not having moved on) was
       silently re-accepted as a fresh stamp toward the *next* reward. Fixed with
       a new `CollectorRedeemedStampEntity`/`CollectorRedeemedStampDao` (DB
       version 7 -> 8, `MIGRATION_7_8`) - the collector's own memory of what it
       has redeemed, scoped per program (not per `card_id`, so "Leave this
       business" can't launder an already-redeemed stamp back into a usable one
       by resetting the memory), checked in
       `CollectorRepository.acceptStampGrant` before accepting any stamp, and
       populated in `acceptRedemptionResponse` alongside the existing delete.
       Backup format bumped 4 -> 5 (`CollectorRedeemedStampRow`) so this memory
       survives export/restore too. The issuer's own spent-set would eventually
       have caught the same stamp at actual redemption time, just less
       precisely - rejecting the whole batch it was mixed into with no way to
       tell which stamp was bad; this catches it immediately and specifically,
       on the collector's own side (SPEC §7/§7.1/§11).
 * [ ] NFC as a second transport alongside QR, for a one-tap exchange instead of
       show-then-scan. The token format is already transport-agnostic, so this
       is protocol-compatible, just a different Android API (SPEC §11).
 * [ ] Work through the F-Droid compliance checklist for real before submitting
       (SPEC §9): audit every transitive dependency for non-free/tracking code,
       confirm the build is reproducible, confirm LICENSE matches the F-Droid
       metadata, confirm no mandatory network access anywhere in the core app.
