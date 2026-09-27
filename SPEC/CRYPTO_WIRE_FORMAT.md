# Crypto wire format

Status: **v3**, implemented in `:crypto` (`io.fidelitycard.crypto`)

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
> wasn't buying real security). The version byte is bumped on every one of
> these changes so bytes from different versions can never be silently
> misparsed as each other; there is no compatibility between v1/v2/v3.

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
| `version` | `byte` | `3` |
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
| — | `signature` | `fixedBytes(64)`, by `issuer_public_key` itself |

### 5.2 Stamp Token (`type = 2`)

Deliberately unordered: each stamp is an independent grant identified by a
random `stamp_id`, not a position in a sequence (SPEC/SPECS.md §6.2).
Preventing the same purchase from minting more than one stamp is treated
as an issuer-side operational/trust matter, the same as a paper card — the
protocol only cryptographically enforces that a stamp can't be forged and
can't be redeemed twice (§5.3).

| Order | Field | Type |
|---|---|---|
| 1 | `program_id` | `string` |
| 2 | `card_id` | `string` |
| 3 | `stamp_id` | `fixedBytes(16)`, random, chosen by the issuer at mint time |
| 4 | `issued_at` | `int64` |
| — | `signature` | `fixedBytes(64)`, by the program's issuer key |

### 5.3 Redemption Certificate (`type = 3`)

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
```

**Program Manifest** — `name = "Joe's Coffee"`, `threshold = 10`,
`reward = "Free coffee"`, `program_id` nonce = `00 01 02 ... 0f` (16 bytes):

```
program_id: OUBSI7FFNROBZITIHPKRLX7HFL

wire bytes (157 bytes):
0301001a4f554253493746464e524f425a49544948504b524c583748464c03a107bff3ce10be1d70dd18e74bc09967e4d6309ba50d5f1ddc8664125531b8000c4a6f65277320436f666665650000000a000b4672656520636f666665658265cd8ec9abc25d04c51d51a3a549257d362e0412237c750531e36969d2f948c0d2628278075f6bb64575d44b9f1e1e2512ee92873a8bc56b328fcf2983c60d
```

**Stamp Token** — `program_id` as above, `card_id = "11111111-1111-1111-1111-111111111111"`, `stamp_id = 00 01 ... 0f` (16 bytes), `issued_at = 1700000000000`:

```
wire bytes (156 bytes):
0302001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d313131313131313131313131000102030405060708090a0b0c0d0e0f0000018bcfe568004f7dd4988cc61a5ac52dce5a32cd0bdfcf193909d77e079aa9da0dfa5a27b04fda16d160a8b38d15f393b919691b886982b5e86264f5ccbee9d24b434f78a001
```

**Redemption Certificate** — same `program_id`/`card_id`, `redeemed_count = 10`, `redeemed_at = 1700000000000`, `redemption_id = 01 02 ... 10` (16 bytes):

```
wire bytes (160 bytes):
0303001a4f554253493746464e524f425a49544948504b524c583748464c002431313131313131312d313131312d313131312d313131312d3131313131313131313131310000000a0000018bcfe568000102030405060708090a0b0c0d0e0f100c1dabfb7aed26214027ea7288cffae26ffb5ed5b2a762f1c023e3abdb8eaa934dc9f226dafbd8551178656294765c36e175095093a664b6cba7e478ca30b906
```

A conforming implementation on any platform must: (a) reproduce these exact
bytes given these exact inputs, and (b) have its `parseAndVerify` accept
each of these byte strings and reject them if any single byte is flipped.
Both directions are exercised by the Kotlin reference test suite
(`crypto/src/test/kotlin/io/fidelitycard/crypto/`).
