package com.novaboost

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

object Sh {
    suspend fun run(cmd: String): String = withContext(Dispatchers.IO) {
        if (cmd.isBlank()) return@withContext ""
        try {
            val m = Shizuku::class.java.getDeclaredMethod(
                "newProcess", Array<String>::class.java,
                Array<String>::class.java, String::class.java
            )
            m.isAccessible = true
            val p = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
            val out = p.inputStream.bufferedReader().readText()
            val err = p.errorStream.bufferedReader().readText()
            p.waitFor()
            (out + err).trim()
        } catch (e: Throwable) {
            "ERR: ${e.message}"
        }
    }
}
