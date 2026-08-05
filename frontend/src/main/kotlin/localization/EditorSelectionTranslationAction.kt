package cg.creamgod45.localization.ui

import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.EntryMutationDto
import cg.creamgod45.localization.LanguageEntryDto
import cg.creamgod45.localization.LanguageSchemeDto
import cg.creamgod45.localization.ReplacementTemplateRuleDto
import cg.creamgod45.localization.SelectionReplacementCandidateDto
import cg.creamgod45.localization.SelectionScanProgressDto
import cg.creamgod45.localization.SelectionScanStage
import cg.creamgod45.localization.SelectionTranslationRequestDto
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.CheckBoxList
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BoxLayout
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JList
import javax.swing.JMenu
import javax.swing.JMenuItem
import javax.swing.JPanel
import javax.swing.JPopupMenu
import javax.swing.JProgressBar
import kotlin.coroutines.CoroutineContext

class LanguageManagerEditorActionGroup : DefaultActionGroup(), DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.text = message("action.editor.group")
        event.presentation.isVisible = event.project != null && event.getData(CommonDataKeys.EDITOR) != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class CreateTranslationFromSelectionAction : DumbAwareAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.text = message("action.editor.create.translation")
        val project = event.project
        val selected = event.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText
        val active = project?.let { LocalizationActionContext.getInstance(it).hasActiveScheme() } == true
        event.presentation.isEnabled = project != null && !selected.isNullOrBlank() && active
        event.presentation.description =
            if (project != null && !active) message("action.editor.create.translation.no.scheme")
            else message("action.editor.create.translation.description")
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        val selectedText = event.getData(CommonDataKeys.EDITOR)?.selectionModel?.selectedText?.takeIf(String::isNotBlank) ?: return
        val state = LocalizationActionContext.getInstance(project).snapshot()
        val scheme = state.schemes.firstOrNull { it.id == state.activeSchemeId } ?: return
        val dialog = SelectionTranslationDialog(project, scheme, state.entries, selectedText)
        if (!dialog.showAndGet()) return
        val request = dialog.request()
        CoroutineScopeHolder.getInstance(project).getPluginScope().launch {
            runCatching {
                val repository = LocalizationFrontendRepository(project)
                val preview = repository.previewSelectionTranslation(scheme.id, request)
                if (preview.files.isEmpty()) return@runCatching
                val editedFiles = withContext(Dispatchers.EDT) {
                    val disposable = com.intellij.openapi.util.Disposer.newDisposable("Language Manager editor selection preview")
                    try {
                        val finalDialog = ChangePreviewDialog(
                            project,
                            preview,
                            message("summary.selection.translation", request.mutations.size, request.replacementFiles.size),
                            disposable,
                            editableAfterEnabled = true,
                        )
                        if (finalDialog.showAndGet()) finalDialog.editedFiles() else null
                    } finally {
                        com.intellij.openapi.util.Disposer.dispose(disposable)
                    }
                }
                if (editedFiles != null) {
                    repository.applyPreviewedSelectionTranslation(
                        scheme.id,
                        request,
                        editedFiles,
                        preview.files.associate { it.filePath to it.beforeSha256 },
                    )
                    notify(project, message("notification.selection.translation.applied"), NotificationType.INFORMATION)
                }
            }.onFailure { error ->
                withContext(Dispatchers.EDT) {
                    Messages.showErrorDialog(project, safeMessage(error), message("app.title"))
                }
            }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance().getNotificationGroup("LanguageManager")
            .createNotification(message("app.title"), content, type).notify(project)
    }
}

private class SelectionTranslationDialog(
    private val project: Project,
    private val scheme: LanguageSchemeDto,
    entries: List<LanguageEntryDto>,
    selectedText: String,
) : DialogWrapper(project, true) {
    private val targets = TranslationEditorSupport.targets(scheme, entries)
    private val namespaces = targets.map { it.namespace }.distinct().ifEmpty { listOf("") }
    private val namespaceBox = ComboBox(namespaces.toTypedArray())
    private val keyField = JBTextField()
    private val selectedTextArea = JBTextArea(selectedText, 3, 60).apply { lineWrap = true; wrapStyleWord = true }
    private val localeEditors = linkedMapOf<TranslationEditorTarget, JBTextArea>()
    private val localePanel = JPanel(GridBagLayout())
    private val scanCheck = JBCheckBox(message("dialog.selection.scan.enable"))
    private val rulesPanel = JPanel().apply { layout = BoxLayout(this, BoxLayout.Y_AXIS) }
    private val ruleRows = mutableListOf<RuleRow>()
    private val candidateList = CheckBoxList<SelectionReplacementCandidateDto>().apply {
        selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
        visibleRowCount = 7
    }
    private val scanButton = JButton(message("dialog.selection.scan.preview"))
    private val stopScanButton = JButton(message("dialog.selection.scan.stop")).apply {
        isEnabled = false
        addActionListener { cancelScan("Cancelled from the modal stop button") }
    }
    private val scanStatus = JBLabel(message("dialog.selection.scan.not.run"))
    private val scanProgressBar = JProgressBar().apply {
        isStringPainted = true
        string = message("dialog.selection.scan.not.run")
        isVisible = false
    }
    private val scanControls = JPanel(BorderLayout())
    private var sourceLocale = ""
    private var scanJob: Job? = null
    private var scanIndicator: ProgressIndicator? = null

    init {
        title = message("dialog.selection.title")
        namespaceBox.renderer = NamespaceRenderer()
        namespaceBox.addActionListener { rebuildLocaleEditors() }
        scanCheck.addActionListener { updateScanVisibility() }
        scanButton.addActionListener { scanCandidates() }
        candidateList.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 2) {
                    candidateList.locationToIndex(event.point)
                        .takeIf { it >= 0 }
                        ?.let(candidateList::getItemAt)
                        ?.let(::previewCandidate)
                }
            }
        })
        addRule(ReplacementTemplateRuleDto("__('%key%')", ".php"))
        rebuildLocaleEditors()
        updateScanVisibility()
        init()
    }

    fun request(): SelectionTranslationRequestDto =
        SelectionTranslationRequestDto(
            mutations = mutations(),
            selectedText = selectedTextArea.text,
            replacementKey = qualifiedKey(),
            rules = if (scanCheck.isSelected) rules() else emptyList(),
            replacementFiles = if (scanCheck.isSelected) checkedReplacementFilePaths(candidateList) else emptyList(),
        )

    override fun createCenterPanel(): JComponent {
        val fields = JPanel(GridBagLayout())
        var row = 0
        fun add(label: String, component: JComponent) {
            fields.add(JBLabel(label), GridBagConstraints().apply { gridx = 0; gridy = row; anchor = GridBagConstraints.NORTHWEST; insets = JBUI.insets(4, 0, 4, 8) })
            fields.add(component, GridBagConstraints().apply { gridx = 1; gridy = row++; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; insets = JBUI.insets(4, 0) })
        }
        add(message("field.namespace"), namespaceBox)
        add(message("field.key"), keyField)
        add(message("dialog.selection.source.text"), JBScrollPane(selectedTextArea).apply { preferredSize = Dimension(JBUI.scale(600), JBUI.scale(72)) })

        val effectiveScanRoot = scheme.usageScanSettings.basePath.ifBlank { project.basePath.orEmpty() }
        scanControls.add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
            add(scanToolbar(), BorderLayout.NORTH)
            add(
                JBLabel(message("dialog.selection.scan.scope", effectiveScanRoot, scheme.usageScanSettings.excludedDirectories.size)).apply {
                    toolTipText = effectiveScanRoot
                },
                BorderLayout.SOUTH,
            )
        }, BorderLayout.NORTH)
        scanControls.add(JBScrollPane(rulesPanel).apply { preferredSize = Dimension(JBUI.scale(720), JBUI.scale(125)) }, BorderLayout.CENTER)
        scanControls.add(JPanel(BorderLayout()).apply {
            add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                add(scanStatus, BorderLayout.NORTH)
                add(scanProgressBar, BorderLayout.SOUTH)
            }, BorderLayout.NORTH)
            add(JBScrollPane(candidateList).apply { preferredSize = Dimension(JBUI.scale(720), JBUI.scale(130)) }, BorderLayout.CENTER)
        }, BorderLayout.SOUTH)

        return JPanel(BorderLayout(0, JBUI.scale(8))).apply {
            border = JBUI.Borders.empty(8)
            add(fields, BorderLayout.NORTH)
            add(JBScrollPane(localePanel).apply { verticalScrollBar.unitIncrement = JBUI.scale(16) }, BorderLayout.CENTER)
            add(JPanel(BorderLayout()).apply {
                border = JBUI.Borders.compound(JBUI.Borders.customLine(JBColor.border()), JBUI.Borders.empty(8))
                add(scanCheck, BorderLayout.NORTH)
                add(scanControls, BorderLayout.CENTER)
            }, BorderLayout.SOUTH)
            preferredSize = Dimension(JBUI.scale(860), JBUI.scale(760))
            minimumSize = Dimension(JBUI.scale(580), JBUI.scale(520))
        }
    }

    override fun doValidate(): ValidationInfo? = when {
        keyField.text.trim().isEmpty() -> ValidationInfo(message("error.translation.key.required"), keyField)
        selectedTextArea.text.isEmpty() -> ValidationInfo(message("dialog.selection.source.required"), selectedTextArea)
        localeEditors.isEmpty() -> ValidationInfo(message("error.translation.targets.none"), namespaceBox)
        scanCheck.isSelected && runCatching { validatedRules() }.isFailure -> ValidationInfo(message("dialog.selection.rule.invalid"), rulesPanel)
        else -> null
    }

    private fun mutations(): List<EntryMutationDto> = localeEditors.map { (target, editor) ->
        EntryMutationDto(filePath = target.filePath, locale = target.locale, namespace = target.namespace, key = keyField.text.trim(), value = editor.text)
    }

    private fun rebuildLocaleEditors() {
        localeEditors.clear()
        localePanel.removeAll()
        val namespace = namespaceBox.selectedItem?.toString().orEmpty()
        val selectedTargets = targets.filter { it.namespace == namespace }.groupBy { it.locale }.map { it.value.first() }.sortedBy { it.locale }
        sourceLocale = selectedTargets.firstOrNull { it.locale.equals("en", true) }?.locale ?: selectedTargets.firstOrNull()?.locale.orEmpty()
        selectedTargets.forEachIndexed { index, target ->
            val editor = JBTextArea(if (target.locale == sourceLocale) selectedTextArea.text else "", 3, 50).apply { lineWrap = true; wrapStyleWord = true }
            localeEditors[target] = editor
            localePanel.add(JPanel(BorderLayout(0, JBUI.scale(4))).apply {
                border = JBUI.Borders.emptyBottom(8)
                add(JBLabel(message("dialog.translation.locale.section", target.locale)), BorderLayout.NORTH)
                add(JBScrollPane(editor).apply { preferredSize = Dimension(JBUI.scale(650), JBUI.scale(72)) }, BorderLayout.CENTER)
                add(JBLabel(target.filePath).apply { toolTipText = target.filePath }, BorderLayout.SOUTH)
            }, GridBagConstraints().apply { gridx = 0; gridy = index; weightx = 1.0; fill = GridBagConstraints.HORIZONTAL; anchor = GridBagConstraints.NORTH })
        }
        localePanel.add(JPanel(), GridBagConstraints().apply { gridx = 0; gridy = selectedTargets.size; weightx = 1.0; weighty = 1.0; fill = GridBagConstraints.BOTH })
        localePanel.revalidate(); localePanel.repaint()
    }

    private fun scanToolbar(): JComponent = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(6), JBUI.scale(4))).apply {
        add(JButton(message("dialog.selection.rule.add")).apply { addActionListener { addRule() } })
        add(presetButton())
        add(scanButton)
        add(stopScanButton)
        add(JBLabel(message("dialog.selection.scan.help")))
    }

    private fun addRule(initial: ReplacementTemplateRuleDto = ReplacementTemplateRuleDto("", "")) {
        val row = RuleRow(initial)
        ruleRows += row
        rulesPanel.add(row.panel)
        rulesPanel.revalidate(); rulesPanel.repaint()
    }

    private fun removeRule(row: RuleRow) {
        ruleRows.remove(row); rulesPanel.remove(row.panel); rulesPanel.revalidate(); rulesPanel.repaint()
    }

    private fun rules(): List<ReplacementTemplateRuleDto> = ruleRows.map { ReplacementTemplateRuleDto(it.template.text.trim(), it.suffix.text.trim()) }

    private fun validatedRules(): List<ReplacementTemplateRuleDto> = rules().also { items ->
        require(items.isNotEmpty())
        items.forEach { require(it.template.length in 1..512 && it.template.windowed(5).count { part -> part == "%key%" } == 1); require(it.fileSuffix.matches(Regex("\\.[A-Za-z0-9._-]{1,63}"))) }
    }

    private fun qualifiedKey(): String = listOf(namespaceBox.selectedItem?.toString().orEmpty(), keyField.text.trim()).filter(String::isNotBlank).joinToString(".")

    private fun scanCandidates() {
        val validRules = runCatching { validatedRules() }.getOrElse { return Messages.showErrorDialog(project, message("dialog.selection.rule.invalid"), title) }
        val modalUiContext = Dispatchers.EDT + ModalityState.current().asContextElement()
        scanIndicator?.cancel()
        scanJob?.cancel(CancellationException("Superseded by a newer selection scan"))
        scanButton.isEnabled = false
        stopScanButton.isEnabled = true
        scanStatus.text = message("dialog.selection.scan.running")
        scanProgressBar.apply {
            isVisible = true
            isIndeterminate = true
            string = message("dialog.selection.scan.running")
        }
        val selectedSource = selectedTextArea.text
        object : Task.Backgroundable(project, message("dialog.selection.scan.background.title"), true) {
            override fun run(indicator: ProgressIndicator) {
                scanIndicator = indicator
                val job = CoroutineScopeHolder.getInstance(project).getPluginScope().launch(start = CoroutineStart.LAZY) {
                    val repository = LocalizationFrontendRepository(project)
                    try {
                        coroutineScope {
                            var observedCurrentScan = false
                            val reporting = launch {
                                repository.selectionScanProgress.collect { progress ->
                                    if (progress.schemeId != scheme.id) return@collect
                                    if (!observedCurrentScan) {
                                        observedCurrentScan = progress.stage in setOf(SelectionScanStage.PLANNING, SelectionScanStage.WALKING, SelectionScanStage.READING)
                                        if (!observedCurrentScan) return@collect
                                    }
                                    updateScanProgress(indicator, progress, modalUiContext)
                                }
                            }
                            try {
                                val result = repository.scanSelectionReplacements(scheme.id, selectedSource, validRules)
                                withContext(modalUiContext) {
                                    candidateList.setItems(result.files) { candidate -> "${candidate.filePath} (${candidate.occurrenceCount})" }
                                    result.files.forEach { candidate -> candidateList.setItemSelected(candidate, true) }
                                    if (result.files.isNotEmpty()) candidateList.selectedIndex = 0
                                    scanStatus.text = message(if (result.truncated) "dialog.selection.scan.result.truncated" else "dialog.selection.scan.result", result.files.size)
                                    scanProgressBar.isIndeterminate = false
                                    scanProgressBar.value = scanProgressBar.maximum
                                    scanProgressBar.string = scanStatus.text
                                    scanButton.isEnabled = true
                                    stopScanButton.isEnabled = false
                                }
                            } finally {
                                reporting.cancelAndJoin()
                            }
                        }
                    } catch (_: CancellationException) {
                        withContext(modalUiContext) {
                            scanStatus.text = message("dialog.selection.scan.cancelled")
                            scanProgressBar.isIndeterminate = false
                            scanProgressBar.value = 0
                            scanProgressBar.string = scanStatus.text
                            scanButton.isEnabled = true
                            stopScanButton.isEnabled = false
                        }
                    } catch (error: Exception) {
                        withContext(modalUiContext) {
                            scanButton.isEnabled = true
                            stopScanButton.isEnabled = false
                            scanStatus.text = message("dialog.selection.scan.failed")
                            scanProgressBar.isIndeterminate = false
                            scanProgressBar.value = 0
                            scanProgressBar.string = scanStatus.text
                            Messages.showErrorDialog(project, safeMessage(error), title)
                        }
                    } finally {
                        scanJob = null
                        scanIndicator = null
                    }
                }
                scanJob = job
                job.start()
                runBlocking { job.join() }
                indicator.checkCanceled()
            }

            override fun onCancel() {
                scanJob?.cancel(CancellationException("Cancelled from the IDE progress indicator"))
            }
        }.queue()
    }

    private suspend fun updateScanProgress(
        indicator: ProgressIndicator,
        progress: SelectionScanProgressDto,
        modalUiContext: CoroutineContext,
    ) {
        val stage = message("dialog.selection.scan.stage.${progress.stage.name.lowercase()}")
        val hasExactTotal = progress.totalEligibleFiles > 0 && progress.stage in setOf(SelectionScanStage.READING, SelectionScanStage.COMPLETED)
        val counters =
            if (hasExactTotal) {
                val percent = progress.processedEligibleFiles * 100 / progress.totalEligibleFiles
                message(
                    "dialog.selection.scan.progress.reading",
                    progress.processedEligibleFiles,
                    progress.totalEligibleFiles,
                    percent,
                    progress.matchedFiles,
                )
            } else {
                message(
                    "dialog.selection.scan.progress",
                    progress.visitedDirectories,
                    progress.visitedFiles,
                    progress.eligibleFiles,
                    progress.matchedFiles,
                )
            }
        val detail = if (progress.currentPath.isBlank()) counters else "$counters — ${progress.currentPath.takeLast(240)}"
        indicator.isIndeterminate = !hasExactTotal && progress.stage != SelectionScanStage.COMPLETED
        indicator.text = stage
        indicator.text2 = detail
        if (hasExactTotal) {
            indicator.fraction = progress.processedEligibleFiles.toDouble().div(progress.totalEligibleFiles).coerceIn(0.0, 1.0)
        }
        withContext(modalUiContext) {
            scanStatus.text = stage
            scanProgressBar.isIndeterminate = !hasExactTotal && progress.stage != SelectionScanStage.COMPLETED
            if (hasExactTotal) {
                scanProgressBar.minimum = 0
                scanProgressBar.maximum = progress.totalEligibleFiles
                scanProgressBar.value = progress.processedEligibleFiles.coerceAtMost(progress.totalEligibleFiles)
            } else if (progress.stage == SelectionScanStage.COMPLETED) {
                scanProgressBar.minimum = 0
                scanProgressBar.maximum = 1
                scanProgressBar.value = 1
            }
            scanProgressBar.string = detail
            scanProgressBar.toolTipText = progress.currentPath
        }
    }

    private fun cancelScan(reason: String) {
        stopScanButton.isEnabled = false
        scanIndicator?.cancel()
        scanJob?.cancel(CancellationException(reason))
        scanStatus.text = message("dialog.selection.scan.cancelling")
        scanProgressBar.string = scanStatus.text
    }

    private fun previewCandidate(candidate: SelectionReplacementCandidateDto) {
        val validRules = runCatching { validatedRules() }.getOrElse { return }
        val modalUiContext = Dispatchers.EDT + ModalityState.current().asContextElement()
        CoroutineScopeHolder.getInstance(project).getPluginScope().launch {
            runCatching { LocalizationFrontendRepository(project).previewSelectionReplacementFile(scheme.id, selectedTextArea.text, qualifiedKey(), validRules, candidate.filePath) }
                .onSuccess { change -> withContext(modalUiContext) {
                    val type = FileTypeManager.getInstance().getFileTypeByFileName(change.filePath)
                    val factory = DiffContentFactory.getInstance()
                    val marker = selectedTextArea.text.replace("\n", " ").take(80)
                    DiffManager.getInstance().showDiff(project, SimpleDiffRequest(
                        change.filePath,
                        factory.create(project, change.beforeContent, type),
                        factory.create(project, change.afterContent, type),
                        message("dialog.selection.diff.before", marker),
                        message("dialog.selection.diff.after", validRules.first { candidate.filePath.lowercase().endsWith(it.fileSuffix.lowercase()) }.template.replace("%key%", qualifiedKey()).take(80)),
                    ))
                }}.onFailure { error -> withContext(modalUiContext) { Messages.showErrorDialog(project, safeMessage(error), title) } }
        }
    }

    private fun updateScanVisibility() {
        scanControls.isVisible = scanCheck.isSelected
        scanControls.parent?.revalidate()
        scanControls.parent?.repaint()
    }

    override fun dispose() {
        cancelScan("Selection translation dialog closed")
        super.dispose()
    }

    private fun presetButton(): JButton {
        val presets = listOf(
            Triple("PHP / Laravel", "__('%key%')", ".php"), Triple("PHP / Laravel Blade", "@lang('%key%')", ".blade.php"),
            Triple("PHP / Symfony", "${'$'}translator->trans('%key%')", ".php"), Triple("Twig", "{{ '%key%'|trans }}", ".twig"),
            Triple("Java / Spring", "messageSource.getMessage(\"%key%\", null, locale)", ".java"), Triple("Java / ResourceBundle", "bundle.getString(\"%key%\")", ".java"),
            Triple("Kotlin / ResourceBundle", "bundle.getString(\"%key%\")", ".kt"), Triple("JetBrains Plugin", "message(\"%key%\")", ".kt"),
        )
        val menu = JPopupMenu()
        presets.groupBy { it.first.substringBefore(" / ") }.forEach { (group, items) -> menu.add(JMenu(group).apply {
            items.forEach { preset -> add(JMenuItem(preset.first).apply { addActionListener { addRule(ReplacementTemplateRuleDto(preset.second, preset.third)) } }) }
        }) }
        return JButton(message("dialog.selection.rule.recommended")).apply { addActionListener { menu.show(this, 0, height) } }
    }

    private inner class RuleRow(initial: ReplacementTemplateRuleDto) {
        val template = JBTextField(initial.template).apply { emptyText.text = "__('%key%')" }
        val suffix = JBTextField(initial.fileSuffix).apply { emptyText.text = ".php" }
        val panel = JPanel(BorderLayout(JBUI.scale(6), 0)).apply {
            border = JBUI.Borders.emptyBottom(5)
            add(template, BorderLayout.CENTER)
            add(JPanel(BorderLayout(JBUI.scale(4), 0)).apply { add(suffix, BorderLayout.CENTER); add(JButton(message("button.remove")).apply { addActionListener { removeRule(this@RuleRow) } }, BorderLayout.EAST); preferredSize = Dimension(JBUI.scale(260), preferredSize.height) }, BorderLayout.EAST)
        }
    }

    private class NamespaceRenderer : DefaultListCellRenderer() {
        override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, selected: Boolean, focus: Boolean): Component =
            super.getListCellRendererComponent(list, value?.toString()?.ifBlank { message("field.namespace.root") }, index, selected, focus)
    }
}

internal fun checkedReplacementFilePaths(candidateList: CheckBoxList<SelectionReplacementCandidateDto>): List<String> =
    candidateList.checkedItems.map(SelectionReplacementCandidateDto::filePath)

private fun safeMessage(error: Throwable): String = (error.message ?: error.javaClass.simpleName)
    .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "?").take(500)
