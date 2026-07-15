package cg.creamgod45.localization.ui

import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.SchemeImportFilePreviewDto
import cg.creamgod45.localization.SchemeImportPreviewDto
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import java.awt.BorderLayout
import java.awt.Dimension
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.table.AbstractTableModel

internal object SchemeSettingsLocalFileAccess {
    private const val MAX_BYTES = 1_000_000L

    fun read(file: VirtualFile): String {
        require(file.extension?.equals("json", true) == true && file.length in 1..MAX_BYTES) {
            message("scheme.transfer.file.invalid")
        }
        return file.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }.also { content ->
            require(content.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES && content.none { it == '\u0000' }) {
                message("scheme.transfer.file.invalid")
            }
        }
    }

    fun write(
        path: Path,
        content: String,
    ) {
        require(path.fileName.toString().endsWith(".json", true) && content.toByteArray(StandardCharsets.UTF_8).size <= MAX_BYTES) {
            message("scheme.transfer.file.invalid")
        }
        path
            .toAbsolutePath()
            .normalize()
            .parent
            ?.let(Files::createDirectories)
        val target = path.toAbsolutePath().normalize()
        val temp = Files.createTempFile(target.parent, ".language-manager-schemes-", ".tmp")
        try {
            Files.writeString(temp, content, StandardCharsets.UTF_8)
            try {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }
}

internal class SchemeImportPreviewDialog(
    project: Project,
    private val preview: SchemeImportPreviewDto,
) : DialogWrapper(project, true) {
    private val rows = preview.schemes.flatMap { scheme -> scheme.files.map { scheme.name to it } }
    private val table = JBTable(SchemeImportPreviewTableModel(rows))

    init {
        title = message("scheme.transfer.preview.title")
        setOKButtonText(message("scheme.transfer.import.apply"))
        isOKActionEnabled = preview.canImport
        init()
    }

    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(6, 6)).apply {
            preferredSize = Dimension(1050, 520)
            add(JBLabel(message("scheme.transfer.preview.summary", preview.schemes.size, rows.size, preview.basePath)), BorderLayout.NORTH)
            table.autoCreateRowSorter = true
            table.autoResizeMode = JBTable.AUTO_RESIZE_OFF
            add(JBScrollPane(table), BorderLayout.CENTER)
            listOf(150, 260, 390, 220).forEachIndexed { index, width -> table.columnModel.getColumn(index).preferredWidth = width }
            if (!preview.canImport) add(JBLabel(message("scheme.transfer.preview.blocked")), BorderLayout.SOUTH)
        }
}

private class SchemeImportPreviewTableModel(
    private val rows: List<Pair<String, SchemeImportFilePreviewDto>>,
) : AbstractTableModel() {
    override fun getRowCount(): Int = rows.size

    override fun getColumnCount(): Int = 4

    override fun getColumnName(column: Int): String =
        when (column) {
            0 -> message("scheme.transfer.table.scheme")
            1 -> message("scheme.transfer.table.configured")
            2 -> message("scheme.transfer.table.resolved")
            else -> message("scheme.transfer.table.status")
        }

    override fun getValueAt(
        rowIndex: Int,
        columnIndex: Int,
    ): Any =
        rows[rowIndex].let { (scheme, file) ->
            when (columnIndex) {
                0 -> {
                    scheme
                }

                1 -> {
                    file.configuredPath
                }

                2 -> {
                    file.resolvedPath
                }

                else -> {
                    when {
                        !file.available -> message("scheme.transfer.status.unavailable", file.detail)
                        file.recognized -> message("scheme.transfer.status.recognized")
                        else -> message("scheme.transfer.status.parse.warning", file.detail)
                    }
                }
            }
        }
}
