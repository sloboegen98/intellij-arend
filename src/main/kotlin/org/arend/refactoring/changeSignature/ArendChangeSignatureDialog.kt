package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.psi.*
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.changeSignature.*
import com.intellij.refactoring.ui.ComboBoxVisibilityPanel
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.Consumer
import org.arend.ArendFileType
import org.arend.psi.ArendPsiFactory

class ArendChangeSignatureDialog(
    project: Project,
    val descriptor: ArendSignatureDescriptor,
) : ChangeSignatureDialogBase<
        ArendParameterInfo,
        PsiElement,
        String,
        ArendSignatureDescriptor,
        ParameterTableModelItemBase<ArendParameterInfo>,
        ArendParameterTableModel
        >(project, descriptor, false, descriptor.method.context) {

    override fun getFileType() = ArendFileType

    override fun createParametersInfoModel(d: ArendSignatureDescriptor) =
        ArendParameterTableModel(d, myDefaultValueContext)

    override fun createRefactoringProcessor(): BaseRefactoringProcessor? = null

    override fun createReturnTypeCodeFragment(): PsiCodeFragment = createTypeCodeFragment(myMethod.method)

    override fun createCallerChooser(
        title: String?,
        treeToReuse: Tree?,
        callback: Consumer<MutableSet<PsiElement>>?
    ): CallerChooserBase<PsiElement>? = null

    override fun validateAndCommitData(): String {
        return "ValidateAndCommitData"
    }

    override fun calculateSignature(): String {
        return "calculateSignature"
    }

    override fun createVisibilityControl() = object : ComboBoxVisibilityPanel<String>("", arrayOf()) {}

    private fun createTypeCodeFragment(function: PsiElement): PsiCodeFragment {
        val factory = ArendPsiFactory(function.project)
        return factory.createFromText(function.text) as PsiCodeFragment
    }
}