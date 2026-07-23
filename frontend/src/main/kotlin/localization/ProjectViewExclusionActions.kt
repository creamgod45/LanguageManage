package cg.creamgod45.localization.ui

import cg.creamgod45.CoroutineScopeHolder
import cg.creamgod45.LanguageManagerBundle.message
import cg.creamgod45.localization.LocalizationStateDto
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Service(Service.Level.PROJECT)
internal class LocalizationActionContext(
    project: Project,
    coroutineScope: CoroutineScope,
) {
    @Volatile
    private var localizationState = LocalizationStateDto()

    init {
        coroutineScope.launch(CoroutineName("Language Manager project-view action state")) {
            LocalizationFrontendRepository(project).state.collect { localizationState = it }
        }
    }

    fun hasActiveScheme(): Boolean = localizationState.activeSchemeId != null

    companion object {
        fun getInstance(project: Project): LocalizationActionContext = project.getService(LocalizationActionContext::class.java)
    }
}

class LanguageManagerProjectViewActionGroup :
    DefaultActionGroup(),
    DumbAware {
    override fun update(event: AnActionEvent) {
        event.presentation.text = message("action.project.view.group")
        event.presentation.isVisible = event.project != null
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

class ExcludeFoldersFromActiveSchemeAction : DumbAwareAction() {
    override fun update(event: AnActionEvent) {
        event.presentation.text = message("action.project.view.exclude")
        val project = event.project
        val folders = event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY).orEmpty()
        val hasActiveScheme = project?.let { LocalizationActionContext.getInstance(it).hasActiveScheme() } == true
        event.presentation.isEnabled =
            project != null &&
            folders.isNotEmpty() &&
            folders.all { it.isDirectory } &&
            hasActiveScheme
        event.presentation.description =
            if (project != null && !hasActiveScheme) {
                message("action.project.view.exclude.no.scheme")
            } else {
                message("action.project.view.exclude.description")
            }
    }

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project ?: return
        if (!LocalizationActionContext.getInstance(project).hasActiveScheme()) return
        val folderPaths =
            event.getData(CommonDataKeys.VIRTUAL_FILE_ARRAY)
                .orEmpty()
                .filter { it.isDirectory }
                .map { it.path }
                .distinct()
        if (folderPaths.isEmpty()) return
        CoroutineScopeHolder.getInstance(project).getPluginScope().launch {
            runCatching { LocalizationFrontendRepository(project).addActiveSchemeExcludedDirectories(folderPaths) }
                .onSuccess { result ->
                    val text =
                        if (result.addedDirectories.isEmpty()) {
                            message("notification.exclusion.already.exists", result.schemeName)
                        } else {
                            message("notification.exclusion.added", result.addedDirectories.size, result.schemeName)
                        }
                    notify(project, text, NotificationType.INFORMATION)
                }.onFailure { error ->
                    notify(project, safeActionMessage(error), NotificationType.ERROR)
                }
        }
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    private fun notify(
        project: Project,
        content: String,
        type: NotificationType,
    ) {
        NotificationGroupManager
            .getInstance()
            .getNotificationGroup("LanguageManager")
            .createNotification(message("app.title"), content, type)
            .notify(project)
    }

    private fun safeActionMessage(error: Throwable): String =
        (error.message ?: error.javaClass.simpleName)
            .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F]"), "?")
            .take(500)
}
