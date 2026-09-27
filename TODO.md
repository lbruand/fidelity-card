TODO
====

## Product / UX

 * [ ] Make the app more simple by treating the collector/issuer mode as settings and not each time.
 * [ ] Create a logo for the app. Maybe we a little stylized stamp.
 * [ ] We should be able to give a little more personnality to the card. ( Like a stamp style, colors, a logo etc...)

## From the spec (SPEC/SPECS.md)

 * [ ] Backup & restore: losing a phone currently loses every collector card and
       issuer program key, with no way back. Needs an encrypted export/import
       format (SPEC §11) — this is the same thing as the item below.
 * [ ] export/import of the config of the apps to save.
 * [ ] Store issuer/collector key material in the Android Keystore instead of as
       raw bytes in Room. Known, documented gap: Ed25519 support in AndroidKeyStore
       only arrived in API 33, above this app's minSdk 26 (SPEC §7).
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
         1. De-duplicate `program_id`/`card_id` inside RedemptionRequest - they're
            repeated once per stamp today even though they're identical for the
            whole batch. Verifier reconstructs each stamp's signed payload from
            the shared prefix. Zero trust cost, ~156 -> ~90 bytes/stamp, roughly
            doubles the threshold that fits in one QR (~20 -> ~30-40). Low
            effort, worth doing regardless of the rest.
         2. For thresholds beyond that: bring back a lightweight issuer-side
            ledger of minted `(card_id, stamp_id)` pairs (not for gating
            issuance - just a local record) so a large-threshold redemption can
            send raw 16-byte stamp ids instead of full 156-byte signed tokens,
            verified against that ledger instead of re-checking signatures.
            ~10x smaller, full cryptographic certainty (no sampling). Trade-off:
            redemption now depends on the issuer device's own local state
            surviving - same shape as the existing §7.4 multi-till limitation,
            and why this should land after backup & restore, not before. Keep
            full-signature redemption as the default/resilient path for normal
            thresholds; this becomes an explicit "large threshold" mode.
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
