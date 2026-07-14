package cg.creamgod45.localization.ui

import javax.swing.ListSelectionModel
import javax.swing.JButton
import javax.swing.SwingUtilities
import javax.swing.table.DefaultTableModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RowHighlightTableTest {
    @Test
    fun `selected cell paints its row without expanding the actual column selection`() {
        SwingUtilities.invokeAndWait {
            val table = RowHighlightTable(DefaultTableModel(2, 3)).apply {
                cellSelectionEnabled = true
                rowSelectionAllowed = true
                columnSelectionAllowed = true
                selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
            }

            table.changeSelection(0, 1, false, false)

            assertTrue(table.isCellSelected(0, 0))
            assertTrue(table.isCellSelected(0, 1))
            assertTrue(table.isCellSelected(0, 2))
            assertFalse(table.isCellSelected(1, 1))
            assertEquals(1, table.selectedColumnCount)
            assertEquals(1, table.selectedColumns.single())
        }
    }

    @Test
    fun `responsive grid wraps cells when the tool window becomes narrow`() {
        SwingUtilities.invokeAndWait {
            val panel = ResponsiveGridPanel(4, 4).apply {
                repeat(3) { add(JButton("Button").apply { preferredSize = java.awt.Dimension(80, 24) }) }
            }

            panel.setSize(180, 100)
            panel.doLayout()
            val narrowHeight = panel.preferredSize.height
            assertTrue(panel.getComponent(2).y > panel.getComponent(0).y)

            panel.setSize(300, 100)
            panel.doLayout()
            assertTrue(panel.components.all { it.y == panel.getComponent(0).y })
            assertTrue(panel.preferredSize.height < narrowHeight)
        }
    }
}
