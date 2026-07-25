package git.more.profiles.frontend.ui

import git.more.profiles.GitProfile
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import git.more.profiles.frontend.GitProfilesBundle.message
import javax.swing.JComponent

/** Add/edit dialog for a single profile with validation and duplicate rejection. */
class ProfileEditorDialog(
    project: Project,
    existingProfiles: List<GitProfile>,
    private val initial: GitProfile?,
) : DialogWrapper(project) {

    private val otherProfiles = existingProfiles.filter { it != initial }

    private var name: String = initial?.name.orEmpty()
    private var email: String = initial?.email.orEmpty()
    private lateinit var nameField: JBTextField

    init {
        title = if (initial == null) message("profile.editor.title.add") else message("profile.editor.title.edit")
        init()
    }

    override fun createCenterPanel(): JComponent = panel {
        row(message("profile.editor.name")) {
            textField()
                .bindText(::name)
                .columns(30)
                .applyToComponent { nameField = this }
                .validationOnApply { field ->
                    if (field.text.trim().isEmpty()) error(message("profile.editor.error.name.empty")) else null
                }
                .focused()
        }
        row(message("profile.editor.email")) {
            textField()
                .bindText(::email)
                .columns(30)
                .validationOnApply { field ->
                    val emailText = field.text.trim()
                    val nameText = nameField.text.trim()
                    when {
                        // No format validation: git itself accepts any value here.
                        emailText.isEmpty() -> error(message("profile.editor.error.email.empty"))
                        otherProfiles.any { it.name == nameText && it.email == emailText } ->
                            error(message("profile.editor.error.duplicate"))
                        else -> null
                    }
                }
        }
    }

    fun editedProfile(): GitProfile = GitProfile(name.trim(), email.trim())
}
