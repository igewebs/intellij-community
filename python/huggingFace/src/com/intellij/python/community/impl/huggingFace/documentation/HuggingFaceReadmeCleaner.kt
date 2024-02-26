package com.intellij.python.community.impl.huggingFace.documentation

import com.intellij.python.community.impl.huggingFace.HuggingFaceConstants
import com.intellij.python.community.impl.huggingFace.HuggingFaceEntityKind
import com.intellij.python.community.impl.huggingFace.api.HuggingFaceURLProvider
import java.net.URL


private const val HF_MD_HEADER_SEPARATOR = "---\n"
private const val ERR_PY_CODE_FENCE_HEADER = "```py\n"
private const val PY_CODE_FENCE_HEADER = "```python\n"
private const val CODE_FENCE_MARKER = "```"
private const val MD_IMG_PATTERN = """!\[(.*?)]\((.*?)\)"""
private const val HTML_IMG_PATTERN = """<img([^>]+)?>"""


class HuggingFaceReadmeCleaner(
  private var markdown: String,
  private val entityId: String,
  private val entityKind: HuggingFaceEntityKind
) {
  private val cardUrl: URL = HuggingFaceURLProvider.getEntityCardLink(entityId, entityKind)

  fun doCleanUp(): HuggingFaceReadmeCleaner {
    removeMetaData()
    increaseHeaderLevels()
    fixCodeFences()
    cleanupImages()
    processMarkdownTables()
    // trimLongMd()
    return this
  }

  private fun removeMetaData() {
    val parts = markdown.split(HF_MD_HEADER_SEPARATOR)
    markdown = if (parts.size > 2) {
      parts.drop(2).joinToString(HF_MD_HEADER_SEPARATOR)
    } else {
      markdown
    }
  }

  private fun increaseHeaderLevels() {
    val pattern = """(?m)^#{1,5}\s""".toRegex()
    markdown = pattern.replace(markdown) { matchResult ->
      "#${matchResult.value}"
    }
  }

  private fun fixCodeFences() {
    markdown = markdown.replace(ERR_PY_CODE_FENCE_HEADER, PY_CODE_FENCE_HEADER)
      .replace("<details>", "")
      .replace("</details>", "")
      .replace(Regex("<summary>.*</summary>"), "")
  }

  private fun cleanupImages() {  // See PY-70539
    // Pattern to match ![alt text](url)
    val markdownImgPattern = Regex(MD_IMG_PATTERN)
    markdown = markdownImgPattern.replace(markdown) { matchResult ->
      val altText = matchResult.groupValues[1].ifBlank { matchResult.groupValues[2].split("/").last() }
      "\n[Image: $altText]($cardUrl)\n"
    }

    val htmlImgPattern = Regex(HTML_IMG_PATTERN, RegexOption.IGNORE_CASE)
    markdown = htmlImgPattern.replace(markdown) { matchResult ->
      val imgTag = matchResult.value
      val altPattern = Regex("""\balt=(['"]?)(.*?)\1""", RegexOption.IGNORE_CASE)

      val altText = altPattern.find(imgTag)?.groupValues?.get(2)
      val srcPattern = Regex("""\bsrc=(['"]?)(.*?)\1""", RegexOption.IGNORE_CASE)
      val srcValue = srcPattern.find(imgTag)?.groupValues?.get(2)
      val filename = srcValue?.split("/")?.lastOrNull()

      "\n[Image: ${altText?: filename}]($cardUrl)\n"
    }
  }

  private fun processMarkdownTables() {
    val lines = markdown.split("\n")
    val processedLines = mutableListOf<String>()
    var isTable = false
    var table = mutableListOf<String>()

    for (line in lines) {
      if (line.startsWith("|") && line.endsWith("|")) {
        isTable = true
        table.add(line)
      } else {
        if (isTable) {
          processedLines.addAll(truncateTable(table))
          table = mutableListOf()
          isTable = false
        }
        processedLines.add(line)
      }
    }

    if (isTable) {
      processedLines.addAll(truncateTable(table))
    }
    markdown = processedLines.joinToString("\n")
  }

  private fun truncateTable(table: MutableList<String>): List<String> {
    val header = table.first()
    val columnCount = header.split("|").filter { it.isNotBlank() }.size

    if (columnCount <= 3) return table

    val truncatedTable = mutableListOf<String>()
    truncatedTable.add(header.split("|").take(4).joinToString("|") + "|...|")
    val separator = table[1].split("|").take(4).joinToString("|") + "|---|"
    truncatedTable.add(separator)
    for (row in table.drop(2)) {
      val cells = row.split("|")
      if (cells.all { it.isBlank() }) { continue }
      val modifiedRow = cells.take(4).joinToString("|") + "|...|"
      truncatedTable.add(modifiedRow)
    }

    return truncatedTable
  }

  fun getMarkdown(): String {
    return markdown.ifEmpty {
      HuggingFaceDocumentationPlaceholdersUtil.noReadmePlaceholder(entityId, entityKind)
    }
  }

  @Suppress("unused")  // may be activated if we decide to trim model cards
  private fun trimLongMd() {
    markdown = if (markdown.length < HuggingFaceConstants.MAX_MD_CHAR_NUM) markdown else {
      var trimmedMd = markdown.substring(0, HuggingFaceConstants.MAX_MD_CHAR_NUM)
      val numCodeFences = trimmedMd.split(CODE_FENCE_MARKER).size - 1

      // Check for an odd number of code fences
      if (numCodeFences % 2 != 0) {
        val lastCodeFenceIndex = trimmedMd.lastIndexOf("```")
        if (lastCodeFenceIndex != -1) {
          trimmedMd = trimmedMd.substring(0, lastCodeFenceIndex)
        }
      }

      val sentenceBoundary = trimmedMd.lastIndexOf(". ")
      val imageBoundary = trimmedMd.lastIndexOf("![")
      val tableBoundary = trimmedMd.lastIndexOf("|")
      val lastNewLine = trimmedMd.lastIndexOf("\n")

      val maxBoundary = maxOf(sentenceBoundary, imageBoundary, tableBoundary, lastNewLine)

      if (maxBoundary != -1) {
        trimmedMd = trimmedMd.substring(0, maxBoundary + 1)
      }

      val placeholder = HuggingFaceDocumentationPlaceholdersUtil.trimmedMdPlaceholder(entityId, entityKind)
      "${trimmedMd.trimEnd()}\n\n${placeholder}"
    }
  }
}