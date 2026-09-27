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
       gets tight; fragmented/animated QR sequences would be the fallback
       (SPEC §6.3/§11).
 * [ ] NFC as a second transport alongside QR, for a one-tap exchange instead of
       show-then-scan. The token format is already transport-agnostic, so this
       is protocol-compatible, just a different Android API (SPEC §11).
 * [ ] Work through the F-Droid compliance checklist for real before submitting
       (SPEC §9): audit every transitive dependency for non-free/tracking code,
       confirm the build is reproducible, confirm LICENSE matches the F-Droid
       metadata, confirm no mandatory network access anywhere in the core app.
