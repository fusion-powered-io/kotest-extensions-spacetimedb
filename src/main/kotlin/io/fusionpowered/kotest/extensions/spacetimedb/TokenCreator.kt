package io.fusionpowered.kotest.extensions.spacetimedb

import io.jsonwebtoken.Jwts
import java.io.File
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.random.Random

object TokenCreator {

    /**
     * Generates a signed ES256 JWT auth token using the ECDSA private key located at `~/.config/spacetime/id_ecdsa`.
     *
     * @param issuer The JWT token issuer name.
     * @param claims A map of claims to include in the token payload.
     * @return The compact, signed JWT token string.
     */
    fun createToken(
        issuer: String,
        claims: Map<String, String>
    ): String {
        val privateKey = File("${System.getProperty("user.home")}/.config/spacetime/id_ecdsa")
            .readText()
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("\\s".toRegex(), "")
            .let { Base64.decode(it) }
            .let { PKCS8EncodedKeySpec(it) }
            .let { KeyFactory.getInstance("EC").generatePrivate(it) }
        return Jwts.builder()
            .issuer(issuer)
            .issuedAt(java.util.Date())
            .expiration(java.util.Date(System.currentTimeMillis() + 3_600_000))
            .subject(Random.nextBytes(ByteArray(64)).toHexString())
            .claims(claims)
            .signWith(privateKey, Jwts.SIG.ES256)
            .compact()
    }
}