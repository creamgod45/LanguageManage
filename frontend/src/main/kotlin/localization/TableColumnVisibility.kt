package cg.creamgod45.localization.ui

import javax.swing.JTable
import javax.swing.table.TableColumn

/**
 * Hides and shows table columns by model index without recreating the others, so widths and any user reordering of
 * the visible columns survive. A re-shown column returns next to the visible column that precedes it in the model.
 */
internal class TableColumnVisibility(
    private val table: JTable,
) {
    private val hiddenColumns = linkedMapOf<Int, TableColumn>()

    /** Forget removed columns after the model structure changed, because the table recreates every column then. */
    fun reset() {
        hiddenColumns.clear()
    }

    fun apply(hiddenModelColumns: Set<Int>) {
        val columnModel = table.columnModel
        (0 until columnModel.columnCount)
            .map(columnModel::getColumn)
            .filter { it.modelIndex in hiddenModelColumns }
            .forEach { column ->
                hiddenColumns[column.modelIndex] = column
                columnModel.removeColumn(column)
            }
        hiddenColumns.keys
            .filter { it !in hiddenModelColumns && it < table.model.columnCount }
            .sorted()
            .forEach { modelIndex ->
                val column = hiddenColumns.remove(modelIndex) ?: return@forEach
                columnModel.addColumn(column)
                val target =
                    (0 until columnModel.columnCount - 1).count { viewIndex ->
                        columnModel.getColumn(viewIndex).modelIndex < modelIndex
                    }
                columnModel.moveColumn(columnModel.columnCount - 1, target)
            }
    }

    fun isHidden(modelColumn: Int): Boolean = modelColumn in hiddenColumns
}
