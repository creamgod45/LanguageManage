package cg.creamgod45.localization.ui

import java.awt.Point
import javax.swing.JTable

/** Decides which cell a table context menu acts on, so row actions never run on a row the user did not point at. */
internal object TableContextMenuTarget {
    /**
     * Selects the cell under [point] unless it is already part of the selection, in which case a multi-row selection is
     * kept for bulk actions. An open in-place edit is committed first. Returns the targeted view cell, or null when the
     * pointer is outside the rows (the menu then offers the actions that need no selection).
     */
    fun select(
        table: JTable,
        point: Point,
    ): Pair<Int, Int>? {
        val viewRow = table.rowAtPoint(point)
        val viewColumn = table.columnAtPoint(point)
        if (viewRow < 0 || viewColumn < 0) return null
        if (table.isEditing && !table.cellEditor.stopCellEditing()) table.cellEditor?.cancelCellEditing()
        val alreadySelected =
            table.selectionModel.isSelectedIndex(viewRow) && table.columnModel.selectionModel.isSelectedIndex(viewColumn)
        if (!alreadySelected) table.changeSelection(viewRow, viewColumn, false, false)
        table.requestFocusInWindow()
        return viewRow to viewColumn
    }
}
