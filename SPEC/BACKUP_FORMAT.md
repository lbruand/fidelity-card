# Backup file format

Status: v1, implemented in `:backup` (`io.fidelitycard.backup`)

This document specifies the encrypted backup file produced and read by
`BackupRepository` (`:app`), as introduced for the "Backup & restore" item
in `TODO.md` and SPEC/SPECS.md §11. Unlike [CRYPTO_WIRE_FORMAT.md](CRYPTO_WIRE_FORMAT.md),
this isn't a protocol exchanged between two devices — it's a single file
one device writes and, later, that same (or a replacement) device reads
back. It gets its own document anyway because it's the one place in this
codebase where private key material is deliberately written to a file the
user controls and might copy elsewhere (cloud storage, another device),
so precisely what protects it deserves to be spelled out.

## 1. What a backup contains

A full, unfiltered snapshot of the local database: every program an
issuer runs — **including its private signing key seed** — every card
either side is tracking, every accepted stamp, and the issuer's spent-set.
Restoring is a **full replace**, not a merge: there's no notion of
reconciling two independently-evolved states, so a restore deletes
whatever is currently on the device first.

Because the plaintext contains raw private keys, it is never written to
disk or handed to any Android API without [§3](#3-encryption-aes-256-gcm-passphrase-derived-key)'s
encryption wrapped around it first.

## 2. Snapshot encoding (the plaintext)

A fixed binary layout, reusing `io.fidelitycard.crypto.wire.WireWriter`/`WireReader`
(the same primitives [CRYPTO_WIRE_FORMAT.md](CRYPTO_WIRE_FORMAT.md) is
built on) purely for their generic framing - this is a distinct format
from the QR protocol, with its own version byte, not a `:crypto` message
type.

```
version: byte = 5

issuer_programs: int32 count, then that many:
  program_id:            string
  name:                  string
  threshold:             int32
  reward:                string
  issuer_seed:           fixedBytes(32)   -- the program's private key
  program_manifest_bytes: varBytes
  created_at:            int64
  color:                 int32            -- ARGB, card personalization
  icon:                  string           -- e.g. one emoji

redeemed_stamps: int32 count, then that many:
  program_id:   string
  stamp_id_hex: string
  redeemed_at:  int64

collector_cards: int32 count, then that many:
  card_id:            string
  program_id:         string
  issuer_public_key:  fixedBytes(32)
  program_name:       string
  threshold:          int32
  reward:             string
  created_at:         int64
  color:              int32   -- ARGB, card personalization
  icon:               string  -- e.g. one emoji

collector_stamps: int32 count, then that many:
  card_id:            string
  stamp_id_hex:       string
  stamp_token_bytes:  varBytes

issuer_minted_stamps: int32 count, then that many:
  program_id:   string
  stamp_id_hex: string
  minted_at:    int64

collector_redeemed_stamps: int32 count, then that many:
  program_id:   string
  stamp_id_hex: string
  redeemed_at:  int64
```

Each table's rows mirror an `:app` Room entity field-for-field
(`io.fidelitycard.app.data.Entities.kt`) - deliberately, since this is a
full-replace backup of exactly that storage, not an independent format
that has to be kept in sync by hand.

`issuer_minted_stamps` is the issuer's ledger of every stamp id it has
ever minted, used by the large-threshold redemption fallback
(SPEC/SPECS.md §6.3/§11) instead of re-checking a signature per stamp.
Losing it (e.g. restoring an older backup taken before it existed) doesn't
corrupt anything, but it does mean stamps minted since that backup can no
longer be redeemed via the large-threshold path - full-signature
redemption is unaffected, since it never depends on this table.

`collector_redeemed_stamps` is the *collector's* own memory of which
stamps it has already redeemed (SPEC/SPECS.md §6.3/§7.1) - without it, a
stamp QR still lying around after being redeemed (a screenshot, the
issuer's screen not having moved on) could be re-scanned and silently
re-accepted as a fresh stamp toward the next reward. Losing this table
(restoring an older backup) reopens exactly that gap for whatever was
redeemed since that backup was taken - the issuer's own spent-set would
still catch it at actual redemption time, just less precisely (the whole
redemption batch rejected, not just the one bad stamp).

`FORMAT_VERSION` history: 1 -> 2 added `issuer_minted_stamps`; 2 -> 3
added `color`/`icon` to `issuer_programs`/`collector_cards` (card
personalization, TODO.md "Product / UX"); 3 -> 4 dropped `card_id` from
`redeemed_stamps`/`issuer_minted_stamps` and removed the `issued_cards`
table entirely (SPEC/SPECS.md §4/§5.2 - a Stamp Token isn't bound to a
collector identity any more, so the issuer has no concept of "cards it
has seen"); 4 -> 5 added `collector_redeemed_stamps`. An older-version
file is rejected outright rather than read with defaulted/dropped fields
- simpler, and this early pre-release there's no real backup file anyone
needs read back.

A reader must reject the whole snapshot (as malformed) if the version byte
doesn't match, or if any bytes remain unconsumed after the last table -
both surface as `MalformedMessageException`, the same type
CRYPTO_WIRE_FORMAT.md's messages use, since `WireReader` is shared.

## 3. Encryption: AES-256-GCM, passphrase-derived key

The snapshot above is never written anywhere unencrypted. The encrypted
file layout:

```
magic:          fixedBytes(4) = "FCBK" (ASCII)
format_version: byte = 1
salt:           fixedBytes(16)   -- for key derivation
iv:             fixedBytes(12)   -- AES-GCM nonce
ciphertext:     remaining bytes  -- AES-256-GCM(key, iv, plaintext = §2's snapshot bytes)
                                    (the 16-byte GCM authentication tag is
                                    appended to the ciphertext automatically
                                    by the standard JCE Cipher API - not a
                                    separate field here)
```

- **Key derivation**: `key = PBKDF2-HMAC-SHA256(passphrase, salt, iterations=210000, keyLength=256 bits)`.
  210,000 is the current OWASP-recommended minimum for PBKDF2-HMAC-SHA256
  (2023 guidance); revisit upward if that guidance moves. `salt` is fresh
  random bytes on every export - never reused, never derived from
  anything else.
- **Cipher**: AES-256-GCM, a 96-bit (12-byte) nonce (`iv`), a 128-bit
  authentication tag. GCM is *authenticated* encryption: decrypting with
  the wrong passphrase, or decrypting a file that's been altered in any
  way (even a single flipped bit), fails loudly (`BackupDecryptionException`)
  rather than silently returning garbage or partially-correct data.
- **No new dependency**: both PBKDF2 and AES-GCM are standard `javax.crypto`/
  `java.security` JCE, present on every JVM/Android target without adding
  anything to the dependency tree (consistent with SPEC/SPECS.md §10.2's
  "keep the dependency tree small and auditable").
- `magic`/`format_version` let a reader reject an unrelated file (or a
  future incompatible format) immediately, before even attempting a
  decrypt - a clearer failure than waiting for GCM's tag check to fail.

**Why no reproducible test vector for the encrypted file** (unlike
CRYPTO_WIRE_FORMAT.md's signed messages): salt and iv are fresh random
bytes on every single call to `encrypt()`, by design - encrypting the same
plaintext with the same passphrase twice must never produce the same
ciphertext twice (`BackupEncryptionTest` asserts exactly this). That
means there is no single "correct" encrypted output to pin. What *is*
pinned and tested is everything deterministic: the plaintext snapshot
encoding (§2, test vector below), the exact algorithm/parameter choices
above, and that encrypt-then-decrypt round-trips correctly, that a wrong
passphrase and a tampered ciphertext both fail cleanly, and that an
unrelated file (wrong magic) or a future format version are rejected
outright (`BackupEncryptionTest`, `:backup` module).

## 4. Test vector (plaintext snapshot only)

Generated by the reference implementation. One program (with a
deterministic key seed and manifest bytes standing in for real ones),
every other table empty:

```
snapshot (155 bytes):
0500000001001a4f554253493746464e524f425a49544948504b524c583748464c000c4a6f65277320436f666665650000000a000b4672656520636f66666565000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f00140102030405060708090a0b0c0d0e0f10111213140000018bcfe56800ff00897b0003e298950000000000000000000000000000000000000000
```

Exercised by `BackupFormatVectorTest` (`:backup`), alongside
`BackupSnapshotTest`'s round-trip, truncation, and version-mismatch cases.
