package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiTypeCodeFragment
import com.intellij.psi.impl.source.PsiTypeCodeFragmentImpl
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import org.arend.ArendFileType
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class ArendParameterTableModel(
    val descriptor: ArendSignatureDescriptor,
    defaultValueContext: PsiElement,
) : ParameterTableModelBase<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
    descriptor.method,
    defaultValueContext,
    ArendNameColumn(descriptor),
    ArendTypeColumn(descriptor),
    ArendImplicitnessColumn()
) {
    override fun createRowItem(parameterInfo: ArendParameterInfo?): ParameterTableModelItemBase<ArendParameterInfo> {
        val resultParameterInfo = if (parameterInfo == null) {
            val newParameter = ArendParameterInfo.createEmpty()
            descriptor.addNewParameter(newParameter)
            newParameter
        } else parameterInfo

        val typeCodeFragment = PsiTypeCodeFragmentImpl(
            myTypeContext.project,
            true,
            "fragment.ard",
            resultParameterInfo.typeText,
            0,
            myTypeContext
        )

        return object : ParameterTableModelItemBase<ArendParameterInfo>(
            resultParameterInfo,
            typeCodeFragment,
            null
        ) {
            override fun isEllipsisType(): Boolean = false
        }
    }

    @Suppress("UnstableApiUsage")
    override fun removeRow(idx: Int) {
        descriptor.removeParameter(idx)
        super.removeRow(idx)
    }

    private class ArendNameColumn(private val descriptor: ArendSignatureDescriptor) :
        NameColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(descriptor.method.project) {
        override fun setValue(item: ParameterTableModelItemBase<ArendParameterInfo>, value: String?) {
            value ?: return
            val oldName = item.parameter.name

            // update all types where current element appears
            for (parameter in descriptor.parameters) {
                val typeText = parameter.typeText ?: continue
                // TODO: rewrite checking
                if (typeText.contains(oldName) && oldName.isNotEmpty()) {
                    parameter.setType(typeText.replace(oldName, value))
                }
            }

            val returnType = descriptor.getReturnType()
            if (returnType != null && returnType.contains(oldName) && oldName.isNotEmpty()) {
                descriptor.setReturnType(returnType.replace(oldName, value))
            }

            super.setValue(item, value)
        }
    }

    private class ArendTypeColumn(descriptor: ArendSignatureDescriptor) :
        TypeColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
            descriptor.method.project,
            ArendFileType
        ) {
        override fun setValue(item: ParameterTableModelItemBase<ArendParameterInfo>?, value: PsiCodeFragment) {
            val fragment = value as? PsiTypeCodeFragment ?: return
            item?.parameter?.setType(fragment.text)
        }
    }

    private class ArendImplicitnessColumn :
        ColumnInfoBase<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>, Boolean>("Explicit") {
        override fun valueOf(item: ParameterTableModelItemBase<ArendParameterInfo>): Boolean =
            item.parameter.isExplicit()

        override fun setValue(item: ParameterTableModelItemBase<ArendParameterInfo>, value: Boolean?) {
            if (value == null) return
            item.parameter.switchExplicit()
        }

        override fun isCellEditable(item: ParameterTableModelItemBase<ArendParameterInfo>): Boolean = true

        override fun doCreateRenderer(item: ParameterTableModelItemBase<ArendParameterInfo>): TableCellRenderer =
            BooleanTableCellRenderer()

        override fun doCreateEditor(item: ParameterTableModelItemBase<ArendParameterInfo>): TableCellEditor =
            BooleanTableCellEditor()
    }
}