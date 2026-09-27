# Crypto wire format

Status: **v2**, implemented in `:crypto` (`io.fidelitycard.crypto`)

This document is the byte-for-byte specification of the four signed message
types in [SPEC/SPECS.md](SPECS.md) §5. It exists so a **non-Kotlin**
implementation (Swift, Python, Rust, whatever) can produce and verify
byte-identical messages without reading the Kotlin source — only an
Ed25519 library and this document. The Kotlin implementation is the
reference implementation, not the spec; this file is the spec.

> **v2 changed the Stamp Token and Redemption Certificate layouts**
> (dropped ordered serials for unordered, uniquely-identified stamps — see
> SPEC/SPECS.md §6.2/§7.1). The version byte was bumped so v1 and v2 bytes
> can never be silently misparsed as each other; there is no compatibility
> between them.

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
| `version` | `byte` | `2` |
| `type` | `byte` | see table below |

| Message | `type` |
|---|---|
| Program Manifest | `1` |
| Card Certificate | `2` |
| Stamp Token | `3` |
| Redemption Certificate | `4` |

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

Self-signed by the issuer: the root of trust for one program.

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` (26 ASCII chars, see §6) |
| 2 | `issuer_public_key` | `fixedBytes(32)` |
| 3 | `name` | `string` |
| 4 | `threshold` | `int32` (must be > 0) |
| 5 | `reward` | `string` |
| — | `signature` | `fixedBytes(64)`, by `issuer_public_key` itself |

### 5.2 Card Certificate (`type = 2`)

Signed by the issuer over a collector's enrollment. Verified against the
issuer key already pinned from that program's manifest (this message
carries no key to bootstrap trust from).

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` |
| 2 | `card_id` | `string` |
| 3 | `collector_public_key` | `fixedBytes(32)` |
| 4 | `issued_at` | `int64` (Unix epoch milliseconds) |
| — | `signature` | `fixedBytes(64)`, by the program's issuer key |

### 5.3 Stamp Token (`type = 3`)

Deliberately unordered: each stamp is an independent grant identified by a
random `stamp_id`, not a position in a sequence (SPEC/SPECS.md §6.2).
Preventing the same purchase from minting more than one stamp is treated
as an issuer-side operational/trust matter, the same as a paper card — the
protocol only cryptographically enforces that a stamp can't be forged and
can't be redeemed twice (§5.4).

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` |
| 2 | `card_id` | `string` |
| 3 | `stamp_id` | `fixedBytes(16)`, random, chosen by the issuer at mint time |
| 4 | `issued_at` | `int64` |
| — | `signature` | `fixedBytes(64)`, by the program's issuer key |

### 5.4 Redemption Certificate (`type = 4`)

A receipt, not a range: since stamps are unordered there is no "through
serial N" to certify. The collector already knows exactly which stamp ids
it submitted and deletes those locally; the issuer's own record of which
specific stamp ids are now spent (the double-redemption defense) lives in
its own redeemed-stamps store, not in this certificate.

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` |
| 2 | `card_id` | `string` |
| 3 | `redeemed_count` | `int32` (must be > 0) |
| 4 | `redeemed_at` | `int64` |
| 5 | `redemption_id` | `fixedBytes(16)`, random |
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

collector seed (32 bytes):
6465666768696a6b6c6d6e6f707172737475767778797a7b7c7d7e7f80818283

collector public key (32 bytes):
0bbc346a57667c380120bd9c7fd7e51d2c5fdfea37cd2f5bf405b2c6bf6f2d78
```

**Program Manifest** — `name = "Joe's Coffee"`, `threshold = 10`,
`reward = "Free coffee"`, `program_id` nonce = `00 01 02 ... 0f` (16 bytes):

```
program_id: OUBSI7FFNROBZITIHPKRLX7HFL

wire bytes (157 bytes):
0201001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f66666565f8d100cbe2c683d690955cebf633890fa9c239cd54c3d8258e7cf2a828050345ac81bbb0a04c035941d5ea626f8958ccd5690754d2c7c61eb1b3c33333fc1904
```

**Card Certificate** — `program_id` as above, `card_id = "11111111-1111-1111-1111-111111111111"`, `collector_public_key` as above, `issued_at = 1700000000000`:

```
wire bytes (172 bytes):
0202001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310bbc346a57667c380120bd9c7fd7e51d2c5fdfea37cd2f5bf405b2c6bf6f2d780000018bcfe56800ab4e332931464c265ea19130a2dd9b1b287ac4ed63e9c6960f64727cab119a87df60fead0b8126e6d6685d897980e8daa7a7cdd174ac9993eb9f2f2f11ad2d06
```

**Stamp Token** — same `program_id`/`card_id`, `stamp_id = 00 01 ... 0f` (16 bytes), `issued_at = 1700000000000`:

```
wire bytes (156 bytes):
0203001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d313131313131313131313131000102030405060708090a0b0c0d0e0f0000018bcfe56800ffaffc87d11cae25271f6fdac5e40aa2607289e82e4a0040ace36669d6f6a473316b4aa67a37d615161e671e50d04edc423311227542f57b86aa3243a7e5360f
```

**Redemption Certificate** — same `program_id`/`card_id`, `redeemed_count = 10`, `redeemed_at = 1700000000000`, `redemption_id = 01 02 ... 10` (16 bytes):

```
wire bytes (160 bytes):
0204001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000000a0000018bcfe568000102030405060708090a0b0c0d0e0f10193303e4d6e3e68552af9b7a8b3ac936d1a420955a561f1f08b0a0c9cf9c1d9d1b4f5bc924fd369ff7256ab63509eab6361988a091eed55af4bc7a91f700ea00
```

A conforming implementation on any platform must: (a) reproduce these exact
bytes given these exact inputs, and (b) have its `parseAndVerify` accept
each of these byte strings and reject them if any single byte is flipped.
Both directions are exercised by the Kotlin reference test suite
(`crypto/src/test/kotlin/io/fidelitycard/crypto/`).
