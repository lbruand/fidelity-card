TODO
====

## Product / UX

 * [ ] Make the app more simple by treating the collector/issuer mode as settings and not each time.
 * [ ] Create a logo for the app. Maybe we a little stylized stamp.
 * [ ] We should be able to give a little more personnality to the card. ( Like a stamp style, colors, a logo etc...)

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
 * [ ] Replace Room's `fallbackToDestructiveMigration` with a real `Migration`
       before any actual release — right now a schema bump just wipes local data,
       fine pre-release, not fine once anyone has real cards/programs saved.
 * [ ] Optional lightweight sync service for issuers running more than one till
       device, to close the multi-device double-redemption gap (SPEC §7.4/§11).
       Not needed until someone actually wants multi-till support.
 * [ ] Revocation: a signed message type to revoke a single card (fraud) or an
       entire program (business closing) (SPEC §11). No way to do this today.
 * [ ] Large reward thresholds (much more than ~20 stamps): a single QR's payload
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
         2. For thresholds beyond that: bring back a lightweight issuer-side
            ledger of minted `(card_id, stamp_id)` pairs (not for gating
            issuance - just a local record) so a large-threshold redemption can
            send raw 16-byte stamp ids instead of full 156-byte signed tokens,
            verified against that ledger instead of re-checking signatures.
            ~10x smaller, full cryptographic certainty (no sampling). Trade-off:
            redemption now depends on the issuer device's own local state
            surviving - same shape as the existing §7.4 multi-till limitation.
            Backup & restore is done now, so that blocker is cleared; still
            not started. Keep full-signature redemption as the
            default/resilient path for normal thresholds; this becomes an
            explicit "large threshold" mode.
         3. Considered and rejected as the primary fix: a probabilistic
            commit-then-spot-check scheme (Merkle root + random sample of k
            stamps). The math doesn't work for the threat that matters - hiding
            one bad stamp among N and sampling only k catches it with
            probability ~1-(N-k)/N, e.g. only ~10% with k=10, N=100. Could
            still be a deliberate, documented extra layer on top of (2) given
            how low-stakes this app is (a free coffee, not a payment), but not
            a substitute for it.
       (SPEC §6.3/§11)
 * [ ] NFC as a second transport alongside QR, for a one-tap exchange instead of
       show-then-scan. The token format is already transport-agnostic, so this
       is protocol-compatible, just a different Android API (SPEC §11).
 * [ ] Work through the F-Droid compliance checklist for real before submitting
       (SPEC §9): audit every transitive dependency for non-free/tracking code,
       confirm the build is reproducible, confirm LICENSE matches the F-Droid
       metadata, confirm no mandatory network access anywhere in the core app.
