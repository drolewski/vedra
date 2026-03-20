package io.vedra.infrastructure.slack.filter

import jakarta.servlet.ReadListener
import jakarta.servlet.ServletInputStream
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletRequestWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader

class CachedBodyHttpServletRequest(
    request: HttpServletRequest,
    private val cachedBody: ByteArray,
) : HttpServletRequestWrapper(request) {
    override fun getInputStream(): ServletInputStream = CachedBodyServletInputStream(cachedBody)

    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(ByteArrayInputStream(cachedBody)))

    private class CachedBodyServletInputStream(
        private val cachedBody: ByteArray,
    ) : ServletInputStream() {
        private val inputStream = ByteArrayInputStream(cachedBody)

        override fun read(): Int = inputStream.read()

        override fun isFinished(): Boolean = inputStream.available() == 0

        override fun isReady(): Boolean = true

        override fun setReadListener(listener: ReadListener?) {}
    }
}
