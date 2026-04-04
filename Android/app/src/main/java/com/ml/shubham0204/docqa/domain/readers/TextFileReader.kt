package com.ml.shubham0204.docqa.domain.readers

import java.io.InputStream

class TextFileReader : Reader() {
    override fun readFromInputStream(inputStream: InputStream): String {
        return inputStream.bufferedReader().use { it.readText() }
    }
}
