package org.jetbrains.cabal

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.content.Content
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.content.MessageView
import org.jetbrains.cabal.tool.CabalMessageView
import org.jetbrains.haskell.util.ProcessRunner
import javax.swing.*
import java.io.IOException
import com.intellij.openapi.util.Key
import com.intellij.notification.Notifications
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.psi.PsiFile
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.module.Module
import com.intellij.psi.PsiManager
import java.util.ArrayList
import java.util.TreeMap
import org.jetbrains.haskell.util.*
import java.io.File
import java.util.LinkedList
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.haskell.config.HaskellSettings
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.progress.ProgressIndicator

private val KEY: Key<CabalMessageView> = Key.create("CabalMessageView.KEY")!!

public class CabalPackageShort(
        val name: String,
        val availableVersions: List<String>,
        val isInstalled: Boolean
){}

val cabalLock = Object()

public open class CabalInterface(val project: Project) {


    fun getProbramPath() : String {
        return HaskellSettings.getInstance().getState().cabalPath!!
    }

    fun getDataPath() : String {
        return HaskellSettings.getInstance().getState().cabalDataPath!!
    }


    private open fun runCommand(canonicalPath: String, vararg commands: String): Process {
        val command = LinkedList<String>();
        command.add(getProbramPath())
        for (c in commands) {
            command.add(c)
        }
        val process = ProcessRunner(canonicalPath).getProcess(command)
        ApplicationManager.getApplication()!!.invokeLater({
            val ijMessageView = MessageView.SERVICE.getInstance(project)!!
            for (content in ijMessageView.getContentManager()!!.getContents()) {
                val cabalMessageView = content.getUserData(KEY)
                if (cabalMessageView != null) {
                    ijMessageView.getContentManager()?.removeContent(content, true)
                }
            }
            val cabalMessageView = CabalMessageView(project, process)
            val content: Content = ContentFactory.SERVICE.getInstance()!!.createContent(cabalMessageView.getComponent(), "Cabal console", true)
            content.putUserData(KEY, cabalMessageView)

            ijMessageView.getContentManager()!!.addContent(content)
            ijMessageView.getContentManager()!!.setSelectedContent(content)

            val messageToolWindow = ToolWindowManager.getInstance(project)?.getToolWindow(ToolWindowId.MESSAGES_WINDOW)
            messageToolWindow?.activate(null)
        })

        return process
    }

    public fun checkVersion() : Boolean {
        try {
            val process = ProcessRunner(null).getProcess(listOf(getProbramPath(), "-V"))
            process.waitFor()
            return true;
        } catch (e : IOException) {
            return false;
        }
    }

    public open fun configure(cabalFile: VirtualFile): Process {
        return runCommand(cabalFile.getParent()!!.getCanonicalPath()!!, "configure")
    }

    public open fun build(cabalFile: VirtualFile): Process {
        return runCommand(cabalFile.getParent()!!.getCanonicalPath()!!, "build")
    }

    public open fun clean(cabalFile: VirtualFile): Process {
        return runCommand(cabalFile.getParent()!!.getCanonicalPath()!!, "clean")
    }

    private open fun findCabal(): String? {
        for (file: VirtualFile? in project.getBaseDir()!!.getChildren()!!) {
            if ("cabal".equals(file?.getExtension())) {
                val cachedDocument: Document? = FileDocumentManager.getInstance()?.getCachedDocument(file!!)
                if (cachedDocument != null) {
                    ApplicationManager.getApplication()!!.runWriteAction(object : Runnable {
                        public override fun run(): Unit {
                            FileDocumentManager.getInstance()?.saveDocument(cachedDocument)
                        }


                    })
                }
                return file.getParent()?.getCanonicalPath()!!
            }

        }
        Notifications.Bus.notify(Notification("Cabal.Error", "Cabal error", "Can't find cabal file.", NotificationType.ERROR))
        return null
    }

    fun getPsiFile(cabalFile: VirtualFile): CabalFile {
        return PsiManager.getInstance(project).findFile(cabalFile) as CabalFile
    }


    public fun getPackagesList(): List<CabalPackageShort> {
        try {
            val path = if (SystemInfo.isMac) {
                joinPath(getDataPath(),
                        "repo-cache",
                        "hackage.haskell.org",
                        "00-index.cache")
            } else {
                joinPath(getDataPath(),
                        "packages",
                        "hackage.haskell.org",
                        "00-index.cache")
            }

            val result = ArrayList<CabalPackageShort>()

            val map = TreeMap<String, MutableList<String>>()

            for (str in fileToIterable(File(path))) {
                val strings = str.split(' ')
                if (strings[0] == "pkg:") {
                    val key = strings[1]
                    val value = strings[2]

                    map.getOrPut(key) { ArrayList<String>() }.add(value)
                }
            }
            // Checking for packages installation

            for ((key, value) in map) {
                result.add(CabalPackageShort(key, value, false))
            }

            return result
        } catch (e: IOException) {
            Notifications.Bus.notify(Notification("Cabal error", "cabal", "Can't read cabal package list.", NotificationType.ERROR))
            return listOf()
        }
    }

    public fun getInstalledPackagesList(): List<CabalPackageShort> {
        try {
            val output = ProcessRunner().execute("ghc-pkg", "--simple-output", "list")

            val map = TreeMap<String, MutableList<String>>()
            output.split(' ').forEach { pkgVer ->
                val pkg = pkgVer.substring(0, pkgVer.lastIndexOf('-'))
                val ver = pkgVer.substring(pkgVer.lastIndexOf('-') + 1)

                map.getOrPut(pkg) { ArrayList<String>() }.add(ver)
            }

            val result = ArrayList<CabalPackageShort>()
            for ((key, value) in map) {
                result.add(CabalPackageShort(key, value, true))
            }
            return result

        } catch (e: IOException) {
            Notifications.Bus.notify(Notification("Cabal error", "cabal", "Can't read installed package list using GHC-PKC.", NotificationType.ERROR))
            return listOf()
        }
    }

    public fun update(): Unit {
        ProgressManager.getInstance()!!.run(object : Task.Backgroundable(project, "cabal update", false) {
            override fun run(indicator: ProgressIndicator) {
                synchronized(cabalLock) {
                    val process = runCommand(project.getBasePath().toString(), "update")
                    process.waitFor();
                }
            }
        });
    }

    public fun install(pkg: String) {
        ProgressManager.getInstance()!!.run(object : Task.Backgroundable(project, "cabal install " + pkg, false) {
            override fun run(indicator: ProgressIndicator) {
                synchronized(cabalLock) {
                    val process = runCommand(project.getBasePath().toString(), "install", pkg)
                    process.waitFor()
                }
            }
        });
    }


}


public fun findCabal(module: Module): VirtualFile? {
    val children = module.getModuleFile()?.getParent()?.getChildren()
    var cabalFile: VirtualFile? = null;

    if (children != null) {
        for (file in children) {
            if ("cabal".equals(file.getExtension())) {
                cabalFile = file;
                break;
            }
        }
    }
    if (cabalFile != null) {
        return cabalFile!!
    }
    return null;
}

public fun findCabal(file: PsiFile, project: Project): VirtualFile? {
    val projectFileIndex = ProjectRootManager.getInstance(project)!!.getFileIndex()
    return findCabal(projectFileIndex.getModuleForFile(file.getVirtualFile()!!)!!)
}