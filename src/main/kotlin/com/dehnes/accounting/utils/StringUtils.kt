package com.dehnes.accounting.utils

import java.nio.charset.StandardCharsets
import java.util.*

object StringUtils {

    fun String.deterministicId(prefix: String = "", postfix: String = "") =
        UUID.nameUUIDFromBytes("$prefix-$this-$postfix".toByteArray(StandardCharsets.UTF_8)).toString()

}