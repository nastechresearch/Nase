import { describe, expect, it } from "vitest";

import {
  b64urlDecode,
  b64urlEncode,
  bytesToHex,
  decrypt,
  decryptJson,
  deriveSessionKeys,
  encrypt,
  encryptJson,
  encryptWithNonce,
  generateKeyPair,
  publicKeyFromPrivate,
  safetyCode,
  tokenBytes,
} from "./crypto.ts";
import { hexToBytes } from "@noble/hashes/utils.js";

describe("session crypto", () => {
  it("derives matching directional keys and the same safety code", () => {
    const bot = generateKeyPair();
    const phone = generateKeyPair();
    const token = "ab".repeat(32);

    const botKeys = deriveSessionKeys(bot.privateKey, phone.publicKey, token, "bot");
    const phoneKeys = deriveSessionKeys(phone.privateKey, bot.publicKey, token, "phone");

    expect(botKeys.send).toEqual(phoneKeys.recv);
    expect(botKeys.recv).toEqual(phoneKeys.send);
    expect(botKeys.safetyCode).toBe(phoneKeys.safetyCode);
    expect(botKeys.safetyCode).toMatch(/^\d{6}$/);
    expect(botKeys.shared).toEqual(phoneKeys.shared);
  });

  it("round-trips JSON through ChaCha20-Poly1305", () => {
    const bot = generateKeyPair();
    const phone = generateKeyPair();
    const token = "cd".repeat(32);
    const botKeys = deriveSessionKeys(bot.privateKey, phone.publicKey, token, "bot");
    const phoneKeys = deriveSessionKeys(phone.privateKey, bot.publicKey, token, "phone");

    const frame = encryptJson(botKeys.send, { op: "tap", x: 12, y: 40 });
    const msg = decryptJson<{ op: string; x: number; y: number }>(phoneKeys.recv, frame);
    expect(msg).toEqual({ op: "tap", x: 12, y: 40 });
  });

  it("rejects a frame encrypted with the wrong direction key", () => {
    const bot = generateKeyPair();
    const phone = generateKeyPair();
    const token = "ef".repeat(32);
    const botKeys = deriveSessionKeys(bot.privateKey, phone.publicKey, token, "bot");
    const frame = encrypt(botKeys.send, new TextEncoder().encode("secret"));
    expect(() => decrypt(botKeys.recv, frame)).toThrow();
  });

  it("encodes base64url without padding", () => {
    const raw = new Uint8Array([0xff, 0x00, 0xab, 0x11]);
    const encoded = b64urlEncode(raw);
    expect(encoded).not.toMatch(/[+/=]/);
    expect(b64urlDecode(encoded)).toEqual(raw);
  });

  it("matches a fixed X25519 + HKDF + safety-code vector", () => {
    // RFC 7748 §5.2 first X25519 test vector, plus our HKDF transcript.
    const alicePriv = hexToBytes(
      "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a",
    );
    const bobPriv = hexToBytes(
      "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb",
    );
    const token = "11".repeat(32);
    const alicePub = publicKeyFromPrivate(alicePriv);
    const bobPub = publicKeyFromPrivate(bobPriv);
    const alice = deriveSessionKeys(alicePriv, bobPub, token, "phone");
    const bob = deriveSessionKeys(bobPriv, alicePub, token, "bot");
    expect(alice.shared).toEqual(bob.shared);
    expect(alice.safetyCode).toBe(bob.safetyCode);
    expect(safetyCode(alice.shared, tokenBytes(token))).toBe(alice.safetyCode);
    expect(alice.send).toEqual(bob.recv);
    expect(bytesToHex(alice.shared)).toBe(
      "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742",
    );
    expect(alice.safetyCode).toBe("570538");
    expect(bytesToHex(alice.send)).toBe(
      "044c2d7c9353456f23db4ab8fdeb963add16c4c69a1d554a32cac26faa22bc7b",
    );
    const frame = encryptWithNonce(
      alice.send,
      hexToBytes("000102030405060708090a0b"),
      new TextEncoder().encode('{"op":"ping"}'),
    );
    expect(bytesToHex(frame)).toBe(
      "000102030405060708090a0bd0b20929ad89981d7a646211af1fc239e0934de74fac2a325275255458",
    );
    expect(new TextDecoder().decode(decrypt(bob.recv, frame))).toBe('{"op":"ping"}');
  });
});
