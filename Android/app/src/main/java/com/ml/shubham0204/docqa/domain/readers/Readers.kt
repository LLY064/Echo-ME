package com.ml.shubham0204.docqa.domain.readers

import com.ml.shubham0204.docqa.domain.readers.Readers.DocumentType

class Readers {
    enum class DocumentType {
        PDF,
        MS_DOCX,
        PLAIN_TEXT,
        MARKDOWN,
    }

    companion object {
        fun getReaderForDocType(docType: DocumentType): Reader =
            when (docType) {
                DocumentType.PDF -> PDFReader()
                DocumentType.MS_DOCX -> DOCXReader()
                DocumentType.MARKDOWN -> MarkdownReader()
                DocumentType.PLAIN_TEXT -> TextFileReader()
            }
    }
}

fun DocumentType.getMimeType(): String = when (this) {
    DocumentType.PDF -> "application/pdf"
    DocumentType.MS_DOCX -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    DocumentType.PLAIN_TEXT -> "text/plain"
    DocumentType.MARKDOWN -> "text/markdown"
}