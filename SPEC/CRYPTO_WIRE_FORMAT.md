# Crypto wire format

Status: **v5**, implemented in `:crypto` (`io.fidelitycard.crypto`)

This document is the byte-for-byte specification of the signed message
types in [SPEC/SPECS.md](SPECS.md) §5. It exists so a **non-Kotlin**
implementation (Swift, Python, Rust, whatever) can produce and verify
byte-identical messages without reading the Kotlin source — only an
Ed25519 library and this document. The Kotlin implementation is the
reference implementation, not the spec; this file is the spec.

> **v2** dropped ordered serials for unordered, uniquely-identified stamps.
> **v3** removed Card Certificate entirely — enrollment is no longer a
> signed handshake at all, just a local decision the collector makes after
> verifying a Program Manifest (see SPEC/SPECS.md §6.1 for why: nothing
> downstream ever re-checked a Card Certificate's collector key, so it
> wasn't buying real security). **v4** added `color`/`icon` to Program
> Manifest for card personalization (SPEC/SPECS.md §5.1, `TODO.md`
> "Product / UX") - Stamp Token and Redemption Certificate didn't change
> shape, but the shared version byte still moved for them too. **v5**
> removed `card_id` from Stamp Token and Redemption Certificate entirely:
> a stamp's value now lives in holding its bytes, not in any collector
> identity, so minting no longer needs the issuer to learn anything from
> the collector first (SPEC/SPECS.md §4/§6.2) - it's a one-way QR (issuer
> mints and shows, collector scans), not a request/response round trip.
> The version byte is bumped on every one of these changes so bytes from
> different versions can never be silently misparsed as each other; there
> is no compatibility between v1/v2/v3/v4/v5.

## 1. Design choice: fixed binary layout, not CBOR

SPEC/SPECS.md §11 left the wire format as an open question between JSON,
CBOR, and a fixed binary layout. This implementation uses a **fixed binary
layout**, decided for one reason: every message here is a fixed-shape
record (the same fields, in the same order, every time) — there are no
maps with variable keys. CBOR's "canonical encoding" rules mostly exist to
make map key ordering deterministic; a fixed-shape record is already
canonical the moment you fix a field order and an unambiguous framing for
each field, which is exactly what this document does. That gets the
determinism CBOR would provide, without requiring a CBOR library on every
target platform.

## 2. Primitives

| Name | Encoding |
|---|---|
| `byte` | 1 byte |
| `int32` | 4 bytes, big-endian, two's complement |
| `int64` | 8 bytes, big-endian, two's complement |
| `fixedBytes(n)` | exactly `n` bytes, verbatim, no length prefix |
| `varBytes` | 2-byte big-endian length prefix (`0..65535`), then that many bytes |
| `string` | UTF-8 encoded, then written as `varBytes` |

There is no padding, no alignment, and no field ever changes size based on
its value except via the `varBytes`/`string` length prefix.

## 3. Common header

Every message starts with:

| Field | Type | Value |
|---|---|---|
| `version` | `byte` | `5` |
| `type` | `byte` | see table below |

| Message | `type` |
|---|---|
| Program Manifest | `1` |
| Stamp Token | `2` |
| Redemption Certificate | `3` |

A reader must reject the message (as malformed, not as a signature
failure) if `version` or `type` don't match what it expected.

## 4. Signing

For every message type:

```
signed_payload = version || type || <fields, in the order below>
signature      = Ed25519_Sign(issuer_private_key, signed_payload)
wire_bytes     = signed_payload || signature        (signature is fixedBytes(64))
```

Verification recomputes `signed_payload` from the decoded fields
(byte-for-byte, using the same encoding rules) and checks
`Ed25519_Verify(issuer_public_key, signed_payload, signature)`. A verifier
must also reject the message if any bytes remain after the signature —
there must be nothing else in the buffer.

Ed25519 signatures are deterministic (RFC 8032): signing the same payload
with the same private key always produces the same signature. This is what
makes the test vectors below exact and reproducible.

## 5. Message layouts

### 5.1 Program Manifest (`type = 1`)

Self-signed by the issuer: the root of trust for one program. Also what a
collector verifies to join it (§6 below) — there is no separate enrollment
message.

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` (26 ASCII chars, see §6) |
| 2 | `issuer_public_key` | `fixedBytes(32)` |
| 3 | `name` | `string` |
| 4 | `threshold` | `int32` (must be > 0) |
| 5 | `reward` | `string` |
| 6 | `color` | `int32`, ARGB (e.g. `0xFFRRGGBB`) - opaque to this format, a UI's choice |
| 7 | `icon` | `string` - opaque to this format (e.g. a single emoji) |
| — | `signature` | `fixedBytes(64)`, by `issuer_public_key` itself |

### 5.2 Stamp Token (`type = 2`)

Deliberately unordered: each stamp is an independent grant identified by a
random `stamp_id`, not a position in a sequence (SPEC/SPECS.md §6.2).
Preventing the same purchase from minting more than one stamp is treated
as an issuer-side operational/trust matter, the same as a paper card — the
protocol only cryptographically enforces that a stamp can't be forged and
can't be redeemed twice (§5.3).

Deliberately **not** bound to a collector/card id (SPEC/SPECS.md §4/§6.2):
whoever holds the valid bytes can redeem it, the same way a physical
stamp card's value lives in holding the card, not in an identity check.
This is what lets minting a stamp be a single one-way QR (issuer mints
and shows, collector scans, done) instead of a request/response round
trip — the issuer never needs to learn who it's minting for. The accepted
trade-off: a leaked/copied stamp is redeemable by whoever gets to
redemption first, not just its original recipient.

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` |
| 2 | `stamp_id` | `fixedBytes(16)`, random, chosen by the issuer at mint time |
| 3 | `issued_at` | `int64` |
| — | `signature` | `fixedBytes(64)`, by the program's issuer key |

#### 5.2.1 Compact Stamp Proof (batched redemption, no `type`/`version` byte of its own)

Not a standalone message — a **fragment** used only inside a Redemption
Request (an app-level container, not part of this crypto wire format
itself; see SPEC/SPECS.md §6.3), to redeem many stamps for one program
without repeating `program_id` inside every single one of them. It
carries exactly the part of a Stamp Token that a full `type = 2` message
doesn't already say once, at the container level:

| Order | Field | Type |
|---|---|---|
| 1 | `stamp_id` | `fixedBytes(16)` |
| 2 | `issued_at` | `int64` |
| 3 | `signature` | `fixedBytes(64)` |

Total: exactly 88 bytes, always — no length prefix needed, since a
container that already knows how many stamps it holds can just read
`count × 88` contiguous bytes. To verify one, a reader reconstructs the
exact same `signed_payload` a full Stamp Token would have had — `version
|| type=2 || program_id || stamp_id || issued_at` — using the
`program_id` supplied by the surrounding container, then checks the
signature against it exactly as in §5.2. Supplying the wrong `program_id`
here isn't a way to bypass anything: it reconstructs different bytes than
were actually signed, so verification simply fails.

### 5.3 Redemption Certificate (`type = 3`)

A receipt, not a range: since stamps are unordered there is no "through
serial N" to certify. The collector already knows exactly which stamp ids
it submitted and deletes those locally; the issuer's own record of which
specific stamp ids are now spent (the double-redemption defense) lives in
its own redeemed-stamps store, not in this certificate.

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` |
| 2 | `redeemed_count` | `int32` (must be > 0) |
| 3 | `redeemed_at` | `int64` |
| 4 | `redemption_id` | `fixedBytes(16)`, random |
| — | `signature` | `fixedBytes(64)`, by the program's issuer key |

## 6. Program ID derivation

Per SPEC/SPECS.md §5.1:

```
program_id = Base32( SHA-256( issuer_public_key || UTF8(name) || nonce ) )[:26]
```

- `nonce` is 16 random bytes, chosen once by the issuer when creating the
  program. It is not secret and is not carried in the wire message — it
  only needs to exist long enough to compute `program_id`.
- Base32 alphabet: RFC 4648 (`A`–`Z`, `2`–`7`), no padding needed since the
  result is truncated to 26 characters before any `=` padding would occur.
- The 26-character result is then used verbatim as the `program_id`
  `string` field in every message above. Nothing re-checks the formula at
  verify time — a `program_id` is just an opaque identifier from every
  other message type's point of view; only the issuer needs it to not
  collide with their own other programs.

## 7. Test vectors

Generated by the reference implementation, deterministic inputs, byte-exact
reproducible output. All byte strings are lowercase hex.

```
issuer seed (32 bytes):
000102030405060708090a0b0c0d0e0f101112131415161718191a1b1c1d1e1f

issuer public key (32 bytes):
03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8
```

**Program Manifest** — `name = "Joe's Coffee"`, `threshold = 10`,
`reward = "Free coffee"`, `color = 0xFF00897B`, `icon = "☕"`, `program_id`
nonce = `00 01 02 ... 0f` (16 bytes):

```
program_id: OUBSI7FFNROBZITIHPKRLX7HFL

wire bytes (166 bytes):
0501001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f66666565ff00897b0003e29895cb5ed34bb2bb84806f425bd8d74f90b471b89235a29f58172d8611ea9408a71ee8a24e7eaf46e7a735c70388eeea5e2a5e85936b5c5c3daf170190508f980b0b
```

**Stamp Token** — `program_id` as above, `stamp_id = 00 01 ... 0f` (16 bytes), `issued_at = 1700000000000`:

```
wire bytes (118 bytes):
0502001a4f554253493746464e524f425a49544948504b524c583748464c000102030405060708090a0b0c0d0e0f0000018bcfe568008ecd7d3da43852d6e8b13e868245e8a1becf8048cd2b955fff47ea20a084f1dd1ccdcebb4c5a8db6380adb9b35e9c1254bc25cf82f392c32b690011ee7ff2c00
```

**Compact Stamp Proof** (§5.2.1) for that same Stamp Token — always exactly
the wire bytes above's trailing 88 bytes (`stamp_id || issued_at ||
signature`), since `program_id` is what got stripped out:

```
compact proof (88 bytes):
000102030405060708090a0b0c0d0e0f0000018bcfe568008ecd7d3da43852d6e8b13e868245e8a1becf8048cd2b955fff47ea20a084f1dd1ccdcebb4c5a8db6380adb9b35e9c1254bc25cf82f392c32b690011ee7ff2c00
```

**Redemption Certificate** — same `program_id`, `redeemed_count = 10`, `redeemed_at = 1700000000000`, `redemption_id = 01 02 ... 10` (16 bytes):

```
wire bytes (122 bytes):
0503001a4f554253493746464e524f425a49544948504b524c583748464c0000000a0000018bcfe568000102030405060708090a0b0c0d0e0f107bf38703b2c79c275b65a6a15fb51da735ef491d7cdbe29a1b7fec3c00af681f56ae6f3d2f39afe6a249bfc940740c54e94ae36977d6e87761736ca5792e6508
```

A conforming implementation on any platform must: (a) reproduce these exact
bytes given these exact inputs, and (b) have its `parseAndVerify` accept
each of these byte strings and reject them if any single byte is flipped.
Both directions are exercised by the Kotlin reference test suite
(`crypto/src/test/kotlin/io/fidelitycard/crypto/`).
