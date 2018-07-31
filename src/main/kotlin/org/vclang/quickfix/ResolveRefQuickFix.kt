package org.vclang.quickfix

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.util.containers.isNullOrEmpty
import com.jetbrains.jetpad.vclang.naming.reference.RedirectingReferable
import com.jetbrains.jetpad.vclang.naming.reference.Referable
import com.jetbrains.jetpad.vclang.naming.scope.*
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.util.LongName
import org.vclang.module.util.findVcFilesAndDirectories
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcReferenceElement
import java.util.Collections.singletonList

interface ResolveRefFixAction {
    fun execute(editor: Editor?)
    fun isValid(): Boolean = true
}

class ImportFileAction(private val importFile: VcFile, private val currentFile: VcFile, private val usingList: List<String>?): ResolveRefFixAction {
    override fun toString(): String {
        return "Import file " + importFile.fullName
    }

    override fun isValid(): Boolean {
        return !currentFile.module?.findVcFilesAndDirectories(importFile.modulePath).isNullOrEmpty()
    }


    override fun execute(editor: Editor?) {
        val fullName = importFile.fullName
        val factory = VcPsiFactory(importFile.project)
        val commandStatement = factory.createImportCommand(fullName + (if (usingList == null) "" else " ()"))

        if (currentFile.children.isEmpty())
            currentFile.add(commandStatement)
        var anchor = currentFile.children[0]
        var after = false

        val currFileCommands = currentFile.namespaceCommands.filter { it.importKw != null }
        if (currFileCommands.isNotEmpty()) {
            val name = LongName(currFileCommands[0].path).toString()
            anchor = currFileCommands[0].parent
            if (fullName >= name)
                after = true
        }

        if (after) for (nC in currFileCommands.drop(1)) {
            val name = LongName(nC.path).toString()
            if (fullName >= name)
                anchor = nC.parent else break
        }

        if (usingList != null)
            AddIdToUsingAction(commandStatement.statCmd!!, usingList).execute(editor)

        if (anchor.parent == currentFile) {
            if (after) {
                currentFile.addAfter(commandStatement, anchor)
                currentFile.addAfter(factory.createWhitespace("\n"), anchor)
            } else {
                val insertedCommand = currentFile.addBefore(commandStatement, anchor)
                currentFile.addAfter(factory.createWhitespace("\n"), insertedCommand)
            }
        }
    }
}

class AddIdToUsingAction(private val statCmd: VcStatCmd, private val idList: List<String>): ResolveRefFixAction {
    override fun toString(): String {
        val name = if (idList.size == 1) idList[0] else idList.toString()
        return "Add "+ name + " to "+ ResolveRefQuickFix.statCmdName(statCmd)+" import's \"using\" list"
    }

    private fun addId(id : String) {
        if (statCmd.importKw != null) {
            val project = statCmd.project
            val using = statCmd.nsUsing
            if (using != null) {
                val nsIds = using.nsIdList
                var anchor = using.lparen
                var needsCommaBefore = false

                for (nsId in nsIds) {
                    val name = nsId.refIdentifier.referenceName
                    if (name > id) break

                    anchor = nsId
                    needsCommaBefore = true
                }

                val factory = VcPsiFactory(project)
                val nsCmd = factory.createImportCommand("Dummy (a,$id)").statCmd
                val newNsUsing = nsCmd!!.nsUsing!!
                val nsId = newNsUsing.nsIdList[1]

                if (nsId != null) {
                    val comma = nsId.prevSibling //we will need the comma only once

                    if (anchor == null) {
                        anchor = using.usingKw ?: error("Can't find anchor within namespace command")
                        anchor = anchor.parent.addAfter(newNsUsing.lparen!!, anchor)
                        anchor.parent.addBefore(factory.createWhitespace(" "), anchor)
                        anchor.parent.addAfter(newNsUsing.rparen!!, anchor)
                    }

                    if (anchor != null) {
                        if (!needsCommaBefore && !nsIds.isEmpty()) {
                            anchor.parent.addAfter(factory.createWhitespace(" "), anchor)
                            anchor.parent.addAfter(comma, anchor)
                        }

                        anchor.parent.addAfter(nsId, anchor)
                        if (needsCommaBefore) {
                            anchor.parent.addAfter(factory.createWhitespace(" "), anchor)
                            anchor.parent.addAfter(comma, anchor)
                        }
                    }
                }
            }
        }
    }

    override fun execute(editor: Editor?) {
        for (id in idList)
            addId(id)
    }
}

class RemoveFromHidingAction(private val statCmd: VcStatCmd, val id: VcRefIdentifier): ResolveRefFixAction {
    override fun toString(): String {
        return "Remove "+ id.referenceName + " from " + ResolveRefQuickFix.statCmdName(statCmd) + " import's \"hiding\" list"
    }

    override fun execute(editor: Editor?) {
        var startSibling : PsiElement = id
        var endSibling : PsiElement = id

        if (startSibling.prevSibling is PsiWhiteSpace) startSibling = startSibling.prevSibling

        val leftEnd = startSibling.prevSibling.node.elementType == VcElementTypes.LPAREN

        while (endSibling.nextSibling is PsiWhiteSpace || endSibling.nextSibling.node.elementType == VcElementTypes.COMMA) {
            endSibling = endSibling.nextSibling
            if (endSibling.node.elementType == VcElementTypes.COMMA && !leftEnd)
                break
        }

        val rightEnd = endSibling.nextSibling.node.elementType == VcElementTypes.RPAREN

        if (rightEnd && startSibling.prevSibling.node.elementType == VcElementTypes.COMMA) {
            startSibling = startSibling.prevSibling
            if (startSibling.prevSibling is PsiWhiteSpace)
                startSibling = startSibling.prevSibling
        }

        if (leftEnd && rightEnd) {
            startSibling = startSibling.prevSibling
            endSibling = endSibling.nextSibling
            if (startSibling.prevSibling is PsiWhiteSpace)
                startSibling = startSibling.prevSibling
            if (startSibling.prevSibling.node.elementType == VcElementTypes.HIDING_KW)
                startSibling = startSibling.prevSibling
            if (startSibling.prevSibling is PsiWhiteSpace)
                startSibling = startSibling.prevSibling
        }

        id.parent.deleteChildRange(startSibling, endSibling)
    }
}

class RenameReferenceAction(private val element: VcReferenceElement, private val id: List<String>): ResolveRefFixAction {
    override fun toString(): String {
        return "Rename " + element.text + " to "+LongName(id).toString()
    }

    override fun execute(editor: Editor?) {
        if (element.parent is VcLongName) {
            val lName = LongName(id).toString()
            val factory = VcPsiFactory(element.project)
            val literal =  factory.createLiteral(lName)
            val longName = literal.longName
            val offset = element.textOffset
            if (longName != null) {
                element.parent.addRangeBefore(longName.firstChild, longName.lastChild, element)
                element.delete()
                editor?.caretModel?.moveToOffset(offset + lName.length)
            }
        }
    }
}

class ResolveRefFixData(val target: PsiLocatedReferable,
                        private val targetFullName: List<String>,
                        private val commandFixAction: ResolveRefFixAction?,
                        private val cursorFixAction: ResolveRefFixAction?): ResolveRefFixAction {

    override fun toString(): String {
        return LongName(targetFullName).toString() + (if (target.containingFile is VcFile)
            " in " + LongName((target.containingFile as VcFile).modulePath.toList()).toString() else
            "")
    }

    override fun execute(editor: Editor?) {
        commandFixAction?.execute(editor)
        cursorFixAction?.execute(editor)
    }
}

class ResolveRefQuickFix {
    companion object {

        fun statCmdName(statCmd : VcStatCmd) : String {
            val file = statCmd.longName?.refIdentifierList?.last()?.reference?.resolve()
            if (file is VcFile) {
                return file.fullName
            }
            return "???"
        }

        fun getDecision(target: PsiLocatedReferable, element: VcReferenceElement): ResolveRefFixData? {
            val targetFile = target.containingFile
            val currentFile = element.containingFile

            val fullName = ArrayList<String>()
            val alternativeFullName : ArrayList<String>? = if (target is VcClassFieldSyn || target is VcClassField || target is VcConstructor)
                ArrayList() else
                null
            var ignoreFlag = true

            var psi: PsiElement = target
            var targetTop: MutableList<Referable> = ArrayList()

            var modifyingImportsNeeded = false
            var fallbackImportAction : ResolveRefFixAction? = null
            val importActionMap: HashMap<List<String>, ResolveRefFixAction?> = HashMap()

            while (psi.parent != null) {
                if (psi is PsiReferable && psi !is VcFile) {
                    val name = psi.name ?: return null

                    fullName.add(0, name)
                    if (alternativeFullName != null) {
                        if (ignoreFlag && alternativeFullName.isNotEmpty()) {
                            ignoreFlag = false
                            targetTop.add(psi)
                        } else {
                            alternativeFullName.add(0, name)
                            targetTop = ArrayList()
                            targetTop.add(psi)
                        }
                    } else {
                        targetTop = ArrayList()
                        targetTop.add(psi)
                    }

                }
                psi = psi.parent
            }

            val fullNames = HashSet<List<String>>()
            fullNames.add(fullName)
            if (alternativeFullName != null) fullNames.add(alternativeFullName)

            if (currentFile is VcFile && targetFile is VcFile) {
                if (currentFile != targetFile) {
                    val fileGroup =  object: Group by currentFile {
                        override fun getSubgroups(): Collection<Group> = emptyList()
                    }
                    val importedScope = ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, false)

                    val cautiousMode = targetFile.subgroups.any { importedScope.resolveName(it.referable.textRepresentation()) != null } // True if imported scope of the current file has nonempty intersection with the scope of the target file

                    var suitableImport: VcStatCmd? = null
                    val aliases = HashMap<List<String>, HashSet<String>>()

                    for (fName in fullNames) {
                        aliases[fName] = HashSet()
                    }

                    for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.importKw != null) {
                        val fileIdent = namespaceCommand.longName?.refIdentifierList?.last()
                        if (fileIdent?.reference?.resolve() == targetFile) {
                            suitableImport = namespaceCommand // even if some of the members are unused or hidden we still can access them using "very long name"

                            val nsUsing = namespaceCommand.nsUsing
                            val hiddenList = namespaceCommand.refIdentifierList
                            val defaultNameHiddenFNames : HashSet<List<String>> = HashSet()

                            if (hiddenList.isNotEmpty()) for (ref in hiddenList) fullNames.filterTo(defaultNameHiddenFNames) { ref.referenceName == it[0] }

                            if (nsUsing != null) {
                                for (refIdentifier in nsUsing.nsIdList) {
                                    for (fName in fullNames) {
                                        val originalName = fName[0]
                                        if (refIdentifier.refIdentifier.text == originalName) {
                                            val defIdentifier = refIdentifier.defIdentifier
                                            aliases[fName]?.add(defIdentifier?.textRepresentation() ?: originalName)
                                        }
                                    }
                                }

                                if (nsUsing.usingKw != null)
                                    aliases.entries.filter { it.component2().isEmpty() && !defaultNameHiddenFNames.contains(it.component1()) }.forEach { it.component2().add(it.component1()[0]) }
                            } else
                                aliases.entries.filter { !defaultNameHiddenFNames.contains(it.component1()) }.forEach { it.component2().add(it.component1()[0]) }
                        }
                    }

                    fullNames.clear()

                    for ((fName, aliases2) in aliases.entries) {
                        for (alias in aliases2) {
                            val fName2 = ArrayList<String>()
                            fName2.addAll(fName)
                            fName2.removeAt(0)
                            fName2.add(0, alias)
                            fullNames.add(fName2)
                        }
                    }

                    if (fullNames.isEmpty()) { // target definition is inaccessible in current context
                        modifyingImportsNeeded = true

                        if (importedScope.resolveName(fullName[0]) == null)
                            fullNames.add(fullName)
                        if (alternativeFullName != null && importedScope.resolveName(alternativeFullName[0]) == null)
                            fullNames.add(alternativeFullName)

                        if (suitableImport != null) { // target definition is hidden or not included into using list but targetFile already has been imported
                            val nsUsing = suitableImport.nsUsing
                            val hiddenList = suitableImport.refIdentifierList

                            for (fName in fullNames) {
                                val hiddenRef : VcRefIdentifier? = hiddenList.lastOrNull { it.referenceName == fName[0] }
                                if (hiddenRef != null)
                                    importActionMap[fName] = RemoveFromHidingAction(suitableImport, hiddenRef)
                                else if (nsUsing != null)
                                    importActionMap[fName] = AddIdToUsingAction(suitableImport, singletonList(fName[0]))
                            }
                            fallbackImportAction = null
                        } else { // targetFile has not been imported
                            if (cautiousMode) {
                                fallbackImportAction = ImportFileAction(targetFile, currentFile, emptyList())
                                for (fName in fullNames)
                                    importActionMap[fName] = ImportFileAction(targetFile, currentFile, singletonList(fName[0]))
                            } else {
                                fallbackImportAction = ImportFileAction(targetFile, currentFile, null)
                                for (fName in fullNames)
                                    importActionMap[fName] = fallbackImportAction
                            }
                        }

                    }
                }
            } else
                return null

            var currentBlock : Map<List<String>, ResolveRefFixAction?>

            currentBlock = HashMap()
            for (fName in fullNames) currentBlock.put(fName, importActionMap[fName])

            if (fullName.size > 1) {
                val namespaceCommands = ArrayList<List<VcStatCmd>>()
                psi = element
                while (psi.parent != null) {
                    var statements : List<VcStatCmd>? = null

                    if (psi is VcWhere)
                        statements = psi.children.mapNotNull { (it as? VcStatement)?.statCmd }
                    else if (psi is VcFile)
                        statements = psi.namespaceCommands

                    if (statements != null)
                        namespaceCommands.add(0, statements.filter { it.openKw != null })

                    psi = psi.parent
                }

                for (commandBlock in namespaceCommands) {
                    val newBlock = HashMap<List<String>, ResolveRefFixAction?>()
                    newBlock.putAll(currentBlock)

                    for (command in commandBlock) {
                        val refIdentifiers = command.longName?.refIdentifierList?.map { it.referenceName }
                        var renamings : HashMap<String, String>? = null
                        val using = command.nsUsing

                        if (using != null) {
                            renamings = HashMap()
                            for (nsId in using.nsIdList) {
                                val oldName = nsId.refIdentifier.referenceName
                                val defIdentifier = nsId.defIdentifier
                                if (defIdentifier != null)
                                    renamings[oldName] = defIdentifier.textRepresentation()
                                else renamings[oldName] = oldName
                            }
                        }

                        if (refIdentifiers != null && refIdentifiers.isNotEmpty()) {
                            for (fName in currentBlock.keys) {
                                val i1 = fName.iterator()
                                val i2 = refIdentifiers.iterator()
                                var equals = true
                                while (i2.hasNext()) {
                                    if (i1.next() != i2.next()) {
                                        equals = false
                                        break
                                    }
                                }
                                if (equals && i1.hasNext()) {
                                    val fName2 = ArrayList<String>()
                                    while (i1.hasNext()) fName2.add(i1.next())
                                    if (renamings != null) {
                                        val newName = renamings[fName2[0]]
                                        if (newName != null) {
                                            fName2.removeAt(0)
                                            fName2.add(0, newName)
                                        } else {
                                            equals = false
                                        }
                                    }

                                    if (equals)
                                        newBlock[fName2] = currentBlock[fName]

                                }
                            }
                        }
                    }

                    currentBlock = newBlock
                }

                val newBlock = HashMap<List<String>, ResolveRefFixAction?>()

                for (fName in currentBlock.keys) {
                    var correctedScope = element.scope

                    if (modifyingImportsNeeded && targetTop.isNotEmpty()) { // calculate the scope imitating current scope after the imports have been fixed
                        val complementScope = object : ListScope(targetTop) {
                            override fun resolveNamespace(name: String?): Scope? = targetTop
                                .filterIsInstance<VcDefinition>()
                                .firstOrNull { name == it.textRepresentation() }
                                ?.let { LexicalScope.opened(it) }
                        }

                        correctedScope = MergeScope(correctedScope, complementScope)
                    }

                    var referable = Scope.Utils.resolveName(correctedScope, fName)
                    if (referable is RedirectingReferable) {
                        referable = referable.originalReferable
                    }

                    if (referable == target) {
                        newBlock[fName] = currentBlock[fName]
                    }

                }

                currentBlock = newBlock

            }

            if (currentBlock.isEmpty()) {
                val veryLongName = ArrayList<String>()
                veryLongName.addAll(targetFile.modulePath.toList())
                veryLongName.addAll(fullName)
                // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
                // Let us use the "longest possible name" when referring to the element
                currentBlock.put(veryLongName, fallbackImportAction)
            }

            // Determine shortest possible name in current scope
            val iterator = currentBlock.keys.iterator()
            var length = -1
            val resultNames : MutableList<Pair<List<String>, ResolveRefFixAction?>> = ArrayList()

            do {
                val lName = iterator.next()

                if (lName.size < length)
                    resultNames.clear()

                if (length == -1 || lName.size <= length) {
                    length = lName.size
                    resultNames.add(Pair(lName, currentBlock[lName]))
                }
            } while (iterator.hasNext())

            val comparator = Comparator<Pair<List<String>, ResolveRefFixAction?>> { o1, o2 ->
                if (o1 == null && o2 == null) return@Comparator 0
                if (o1 == null) return@Comparator -1
                if (o2 == null) return@Comparator 1

                val s1 = o1.first
                val s2 = o2.first
                (0 until minOf(s1.size, s2.size))
                        .filter { s1[it] != s2[it] }
                        .forEach { return@Comparator s1[it].compareTo(s2[it]) }
                if (s1.size != s2.size) return@Comparator s1.size.compareTo(s2.size)
                0
            }
            resultNames.sortedWith(comparator)

            return if (resultNames.size > 0) {
                val resultName = resultNames[0].first
                val importAction = resultNames[0].second

                if (importAction != null && !importAction.isValid())
                    return null //Perhaps current or target directory is not marked as a content root

                val renameAction = if ((resultName.size > 1 || (resultName[0] != element.referenceName))) RenameReferenceAction(element, resultName) else null
                ResolveRefFixData(target, fullName, importAction, renameAction)
            } else
                null
        }
    }
}