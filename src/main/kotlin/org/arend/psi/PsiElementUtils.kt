package org.arend.psi

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import org.arend.mapFirstNotNull
import org.arend.module.ArendModuleType
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.scope.LexicalScope
import org.arend.prelude.Prelude
import org.arend.term.group.Group
import org.arend.typechecking.TypeCheckingService

val PsiElement.ancestors: Sequence<PsiElement>
    get() = generateSequence(this) { it.parent }

val PsiElement.ancestorsUntilFile: Sequence<PsiElement>
    get() = generateSequence(this) { if (it is PsiFile) null else it.parent }

val PsiElement.module: Module?
    get() = ModuleUtilCore.findModuleForPsiElement(this)

val PsiElement.moduleScopeProvider: ModuleScopeProvider
    get() {
        val module = module
        val config = module?.let { if (ArendModuleType.has(it)) ArendModuleConfigService.getConfig(it) else null }
        val typecheckingService = TypeCheckingService.getInstance(module?.project ?: project)
        return ModuleScopeProvider { modulePath ->
            val file = if (modulePath == Prelude.MODULE_PATH) {
                typecheckingService.prelude
            } else {
                if (config == null) {
                    typecheckingService.libraryManager.registeredLibraries.mapFirstNotNull { it.getModuleGroup(modulePath) }
                } else {
                    config.forAvailableConfigs { it.findArendFile(modulePath) }
                }
            }
            file?.let { LexicalScope.opened(it) }
        }
    }

inline fun <reified T : PsiElement> PsiElement.parentOfType(
        strict: Boolean = true,
        minStartOffset: Int = -1
): T? = PsiTreeUtil.getParentOfType(this, T::class.java, strict, minStartOffset)

inline fun <reified T : PsiElement> PsiElement.childOfType(
        strict: Boolean = true
): T? = PsiTreeUtil.findChildOfType(this, T::class.java, strict)

fun Group.findGroupByFullName(fullName: List<String>): Group? =
    if (fullName.isEmpty()) this else (subgroups.find { it.referable.textRepresentation() == fullName[0] } ?: dynamicSubgroups.find { it.referable.textRepresentation() == fullName[0] })?.findGroupByFullName(fullName.drop(1))