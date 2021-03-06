package crypto

import java.math.BigInteger
import java.security.MessageDigest



fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(toByteArray())).toString(16).padStart(MD5_LENGTH, '0')
}

val MD5_LENGTH get() = 32