package com.netflix.java.refactor

import com.netflix.java.refactor.find.*
import com.netflix.java.refactor.fix.*
import com.sun.tools.javac.tree.JCTree
import java.io.File
import java.util.*

class Refactorer(var cu: JCTree.JCCompilationUnit, val parser: AstParser, val dryRun: Boolean = false) {
    var changedFile = false
    
    fun source() = File(cu.sourceFile.toUri().path)
    fun sourceText() = source().readText()
    
    internal var lastCommitChangedFile = false

    fun tx() = tx(false) // Java support
    
    fun tx(autoCommit: Boolean = false): RefactorTransaction {
        val tx = RefactorTransaction(this, autoCommit)
        
        if(lastCommitChangedFile) {
            cu = parser.reparse(cu)
            lastCommitChangedFile = false
        }
        return tx
    }

    fun hasType(clazz: Class<*>): Boolean =
        HasType(clazz.name).scanner().scan(cu, parser.context)
    
    fun findField(clazz: Class<*>): Field =
        FindField(clazz.name).scanner().scan(cu, parser.context)
    
    fun hasField(clazz: Class<*>) = findField(clazz).exists
    
    fun findMethod(signature: String): Method =
        FindMethod(signature).scanner().scan(cu, parser.context)
    
    fun hasMethod(signature: String) = findMethod(signature).exists
    
    fun changeType(from: String, to: String) {
        tx().changeType(from, to).commit()
    }
    
    fun changeType(from: Class<*>, to: Class<*>) {
        tx().changeType(from, to).commit()
    }

    fun changeMethod(signature: String): ChangeMethodInvocation = tx(true).changeMethod(signature)

    fun changeField(clazz: Class<*>): ChangeField = tx(true).changeField(clazz)
    fun changeField(clazz: String): ChangeField = tx(true).changeField(clazz)
    
    fun removeImport(clazz: String) {
        tx().removeImport(clazz).commit()
    }
    
    fun removeImport(clazz: Class<*>) {
        tx().removeImport(clazz).commit()
    }

    fun addImport(clazz: String) {
        tx().addImport(clazz).commit()
    }

    fun addImport(clazz: Class<*>) {
        tx().addImport(clazz).commit()
    }
    
    fun addStaticImport(clazz: String, method: String) {
        tx().addStaticImport(clazz, method).commit()
    }
    
    fun addStaticImport(clazz: Class<*>, method: String) {
        tx().addStaticImport(clazz, method).commit()
    }
}


class RefactorTransaction(val refactorer: Refactorer, val autoCommit: Boolean = false) {
    private val ops = ArrayList<FixingOperation>()
    
    fun changeType(from: String, to: String): RefactorTransaction {
        ops.add(ChangeType(from, to))
        return this
    }

    fun changeType(from: Class<*>, to: Class<*>) = changeType(from.name, to.name)

    fun changeMethod(signature: String): ChangeMethodInvocation {
        val changeMethod = ChangeMethodInvocation(signature, this)
        ops.add(changeMethod)
        return changeMethod
    }

    fun changeField(clazz: Class<*>): ChangeField = changeField(clazz.name)
    
    fun changeField(clazz: String): ChangeField {
        val changeField = ChangeField(clazz, this)
        ops.add(changeField)
        return changeField
    }

    fun removeImport(clazz: String): RefactorTransaction {
        ops.add(RemoveImport(clazz))
        return this
    }

    fun removeImport(clazz: Class<*>) = removeImport(clazz.name)

    fun addImport(clazz: String): RefactorTransaction {
        ops.add(AddImport(clazz))
        return this
    }

    fun addImport(clazz: Class<*>) = addImport(clazz.name)
    
    fun addStaticImport(clazz: String, method: String): RefactorTransaction {
        ops.add(AddImport(clazz, method))
        return this
    }
    
    fun addStaticImport(clazz: Class<*>, method: String) = addStaticImport(clazz.name, method)
    
    fun commit() {
        val fixes = ops.flatMap { it.scanner().scan(refactorer.cu, refactorer.parser.context) }
        
        if(!refactorer.dryRun && fixes.isNotEmpty()) {
            try {
                val sourceText = refactorer.sourceText()
                val sortedFixes = fixes.sortedBy { it.position.last }.sortedBy { it.position.start }
                var source = sortedFixes.foldIndexed("") { i, source, fix ->
                    val prefix = if (i == 0)
                        sourceText.substring(0, fix.position.first)
                    else sourceText.substring(sortedFixes[i - 1].position.last, fix.position.start)
                    source + prefix + (fix.changes ?: "")
                }
                if (sortedFixes.last().position.last < sourceText.length) {
                    source += sourceText.substring(sortedFixes.last().position.last)
                }
                refactorer.source().writeText(source)
            } catch(t: Throwable) {
                // TODO how can we throw a better exception?
                t.printStackTrace()
            }
        }
        
        if(fixes.isNotEmpty()) {
            refactorer.changedFile = true
            refactorer.lastCommitChangedFile = true
        }
    }
}