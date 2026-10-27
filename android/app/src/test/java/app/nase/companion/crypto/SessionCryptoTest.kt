package app.nase.companion.crypto

import com.google.crypto.tink.subtle.X25519
import org.junit.Assert.assertEquals
import org.junit.Test

/** Must match shared/src/crypto.test.ts. The relay never sees these bytes. */
class SessionCryptoTest {
    @Test
    fun matchesNobleX25519HkdfAndChaCha() {
        val alicePriv = SessionCrypto.hexToBytes(
            "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a",
        )
        val bobPriv = SessionCrypto.hexToBytes(
            "5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb",
        )
        val phone = SessionCrypto.derive(
            alicePriv,
            X25519.publicFromPrivate(bobPriv),
            "11".repeat(32),
            "phone",
        )
        val bot = SessionCrypto.derive(
            bobPriv,
            X25519.publicFromPrivate(alicePriv),
            "11".repeat(32),
            "bot",
        )
        assertEquals(
            "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742",
            hex(phone.shared),
        )
        assertEquals(phone.safetyCode, bot.safetyCode)
        assertEquals("570538", phone.safetyCode)
        assertEquals(
            "044c2d7c9353456f23db4ab8fdeb963add16c4c69a1d554a32cac26faa22bc7b",
            hex(phone.send),
        )
        assertEquals(hex(phone.send), hex(bot.recv))
        val frame = SessionCrypto.hexToBytes(
            "000102030405060708090a0bd0b20929ad89981d7a646211af1fc239e0934de74fac2a325275255458",
        )
        val plain = String(SessionCrypto.decrypt(bot.recv, frame))
        assertEquals("{\"op\":\"ping\"}", plain)
    }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
