package git.more.profiles.ui

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.components.JBLabel
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.ListTableModel
import com.intellij.util.ui.UIUtil
import git.more.profiles.GitProfile
import git.more.profiles.GitProfilesBundle.message
import git.more.profiles.services.GitConfigOperations
import git.more.profiles.services.GitProfilesService
import git4idea.repo.GitRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.event.ItemEvent
import javax.swing.Action
import javax.swing.DefaultComboBoxModel
import javax.swing.JComponent

/**
 * The main dialog: the stored profiles table (with the global marker) and the list of
 * git repositories in the current project, each with its own profile selector.
 *
 * All choices are applied to git config immediately; the dialog has a single Close button.
 */
class GitProfilesDialog(
    private val project: Project,
    snapshot: Snapshot,
) : DialogWrapper(project) {

    /** Git config state read up front, before the dialog opens. */
    class Snapshot(
        val globalProfile: GitProfile?,
        val repositories: List<Pair<GitRepository, GitProfile?>>,
    )

    private val service = GitProfilesService.getInstance()
    private var globalProfile: GitProfile? = snapshot.globalProfile

    private val nameColumn = object : ColumnInfo<GitProfile, String>(message("dialog.column.name")) {
        override fun valueOf(item: GitProfile): String = item.name
    }
    private val emailColumn = object : ColumnInfo<GitProfile, String>(message("dialog.column.email")) {
        override fun valueOf(item: GitProfile): String = item.email
    }
    private val globalColumn = object : ColumnInfo<GitProfile, String>(message("dialog.column.global")) {
        override fun valueOf(item: GitProfile): String = if (item == globalProfile) "✓" else ""
        override fun getMaxStringValue(): String = message("dialog.column.global")
    }

    private val tableModel = ListTableModel<GitProfile>(nameColumn, emailColumn, globalColumn).apply {
        items = service.profiles.toMutableList()
    }
    private val table = TableView(tableModel)
    private val globalLabel = JBLabel()
    private val repoRows = snapshot.repositories.map { (repository, local) -> RepoRow(repository, local) }

    init {
        title = message("dialog.title")
        setOKButtonText(message("dialog.button.close"))
        table.setShowGrid(false)
        updateGlobalLabel()
        init()
    }

    /** Everything is applied immediately, so only a single Close button. */
    override fun createActions(): Array<Action> = arrayOf(okAction)

    override fun getPreferredFocusedComponent(): JComponent = table

    override fun createCenterPanel(): JComponent {
        val setGlobalAction = object : DumbAwareAction(
            message("dialog.button.set.global"), null, AllIcons.Actions.Checked,
        ) {
            override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

            override fun update(e: AnActionEvent) {
                e.presentation.isEnabled = table.selectedObject != null
            }

            override fun actionPerformed(e: AnActionEvent) = setSelectedAsGlobal()
        }

        val tablePanel = ToolbarDecorator.createDecorator(table)
            .setAddAction { addProfile() }
            .setEditAction { editProfile() }
            .setRemoveAction { removeProfile() }
            .addExtraAction(setGlobalAction)
            .disableUpDownActions()
            .createPanel()

        return panel {
            group(message("dialog.group.profiles")) {
                row {
                    cell(tablePanel).align(Align.FILL)
                }.resizableRow()
                row {
                    cell(globalLabel)
                }
            }
            group(message("dialog.group.repositories")) {
                if (repoRows.isEmpty()) {
                    row { label(message("dialog.repositories.empty")) }
                }
                for (repoRow in repoRows) {
                    row("${repoRow.repository.root.name}:") {
                        cell(repoRow.combo)
                        cell(repoRow.effectiveLabel)
                    }
                }
            }
        }.apply { preferredSize = JBUI.size(680, 480) }
    }

    private fun addProfile() {
        val dialog = ProfileEditorDialog(project, service.profiles, initial = null)
        if (dialog.showAndGet()) {
            service.add(dialog.editedProfile())
            refreshProfiles()
        }
    }

    private fun editProfile() {
        val selected = table.selectedObject ?: return
        val dialog = ProfileEditorDialog(project, service.profiles, initial = selected)
        if (dialog.showAndGet()) {
            service.update(selected, dialog.editedProfile())
            refreshProfiles()
        }
    }

    private fun removeProfile() {
        val selected = table.selectedObject ?: return
        service.remove(selected)
        refreshProfiles()
    }

    private fun refreshProfiles() {
        tableModel.items = service.profiles.toMutableList()
        repoRows.forEach { it.rebuild() }
    }

    private fun setSelectedAsGlobal() {
        val selected = table.selectedObject
        if (selected == null) {
            Messages.showInfoMessage(project, message("dialog.no.selection"), message("dialog.title"))
            return
        }
        val applied = runGitOperation(message("progress.applying.config")) {
            GitConfigOperations.setGlobalProfile(project, selected)
        }
        if (applied) {
            globalProfile = selected.copy()
            tableModel.fireTableDataChanged()
            updateGlobalLabel()
            repoRows.forEach { it.updateEffectiveLabel() }
        }
    }

    private fun updateGlobalLabel() {
        globalLabel.text = globalProfile?.let { message("dialog.global.current", it.toString()) }
            ?: message("dialog.global.none")
        globalLabel.foreground = UIUtil.getContextHelpForeground()
    }

    private fun runGitOperation(title: @NlsContexts.ProgressTitle String, operation: () -> Unit): Boolean =
        try {
            runWithModalProgressBlocking(ModalTaskOwner.project(project), title, TaskCancellation.nonCancellable()) {
                withContext(Dispatchers.IO) { operation() }
            }
            true
        } catch (e: Exception) {
            Messages.showErrorDialog(project, e.message ?: message("error.unknown"), message("error.title"))
            false
        }

    /** One row of the repositories section: a repo, its selector, and its effective identity. */
    private inner class RepoRow(val repository: GitRepository, private var localProfile: GitProfile?) {
        val combo = ComboBox<RepoChoice>()
        val effectiveLabel = JBLabel()
        private var updating = false

        init {
            combo.renderer = textListCellRenderer {
                it.displayText()
            }
            combo.setMinimumAndPreferredWidth(JBUI.scale(240))
            effectiveLabel.foreground = UIUtil.getContextHelpForeground()
            combo.addItemListener { event ->
                if (event.stateChange == ItemEvent.SELECTED && !updating) {
                    applyChoice(event.item as RepoChoice)
                }
            }
            rebuild()
        }

        fun rebuild() {
            updating = true
            try {
                val model = DefaultComboBoxModel<RepoChoice>()
                model.addElement(RepoChoice.InheritGlobal)
                service.profiles.forEach { model.addElement(RepoChoice.Stored(it)) }
                val local = localProfile
                val selection: RepoChoice = when {
                    local == null -> RepoChoice.InheritGlobal
                    local in service.profiles -> RepoChoice.Stored(local)
                    else -> RepoChoice.Custom(local).also { model.addElement(it) }
                }
                combo.model = model
                combo.selectedItem = selection
            } finally {
                updating = false
            }
            updateEffectiveLabel()
        }

        private fun applyChoice(choice: RepoChoice) {
            val applied = when (choice) {
                is RepoChoice.InheritGlobal -> runGitOperation(message("progress.applying.config")) {
                    GitConfigOperations.unsetLocalProfile(repository)
                }.also { if (it) localProfile = null }

                is RepoChoice.Stored -> runGitOperation(message("progress.applying.config")) {
                    GitConfigOperations.setLocalProfile(repository, choice.profile)
                }.also { if (it) localProfile = choice.profile.copy() }

                is RepoChoice.Custom -> true
            }
            if (applied) updateEffectiveLabel() else rebuild()
        }

        fun updateEffectiveLabel() {
            val local = localProfile
            val effective = local ?: globalProfile
            effectiveLabel.text = when {
                effective == null -> message("dialog.effective.none")
                local != null -> message("dialog.effective.local", effective.toString())
                else -> message("dialog.effective.global", effective.toString())
            }
        }
    }

    private sealed class RepoChoice {
        object InheritGlobal : RepoChoice()
        data class Stored(val profile: GitProfile) : RepoChoice()
        data class Custom(val profile: GitProfile) : RepoChoice()

        fun displayText(): String = when (this) {
            is InheritGlobal -> message("dialog.choice.inherit")
            is Stored -> profile.toString()
            is Custom -> message("dialog.choice.custom", profile.toString())
        }
    }
}
