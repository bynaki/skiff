package com.naki.skiff.ui

import android.Manifest
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naki.skiff.data.store.ServerProfile
import com.naki.skiff.fs.FileKind
import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId
import com.naki.skiff.link.SkiffCodeLink
import androidx.compose.runtime.Composable
import com.naki.skiff.ui.theme.SkiffTheme
import com.naki.skiff.ui.workspace.StorageGate
import com.naki.skiff.ui.workspace.WorkspaceScreen
import com.naki.skiff.ui.workspace.WorkspaceViewModel
import java.io.File

class MainActivity : ComponentActivity() {

    private var viewModel: WorkspaceViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkiffTheme {
                val vm: WorkspaceViewModel = viewModel()
                viewModel = vm
                val state by vm.state.collectAsStateWithLifecycle()
                // Without this grant the foreground transfer notification is created but
                // never shown, so a background transfer looks like it silently stopped.
                RequestNotificationPermissionOnce()
                if (state.storageGranted) {
                    WorkspaceScreen(vm, onOpenExternally = ::openExternally)
                } else {
                    StorageGate(onGranted = vm::refreshStorageGrant)
                }
            }
        }
    }

    @Composable
    private fun RequestNotificationPermissionOnce() {
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { }
        LaunchedEffect(Unit) {
            // The permission only exists from API 33; below that notifications are implicit.
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The permission can only be granted in Settings, so re-check whenever we come back.
        viewModel?.refreshStorageGrant()
    }

    /**
     * Code, text and markdown go to Skiff Code as a `skiffcode://` link, from the phone or a
     * server alike. Anything else, or everything when Skiff Code is not installed or not signed by us, is handed to
     * whatever app the system offers — which only works for local files, since a remote one would
     * have to be downloaded first.
     */
    private fun openExternally(node: FileNode) {
        val state = viewModel?.state?.value ?: return
        val source = viewModel?.controller(state.activeSide)?.state?.value?.sourceId ?: return
        if (FileKind.of(node) in SKIFF_CODE_KINDS && openInSkiffCode(source, node, state.profiles)) return
        if (source !is SourceId.Local) return

        val uri = runCatching {
            FileProvider.getUriForFile(this, "$packageName.files", File(node.path))
        }.getOrNull() ?: return

        val extension = FsPath.extension(node.name)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        runCatching { startActivity(Intent.createChooser(intent, node.name)) }
    }

    /**
     * False when Skiff Code is not installed, is signed by another key, or the server's profile is
     * gone. The signature check keeps an app that only took Skiff Code's package name from
     * receiving server addresses and paths.
     */
    private fun openInSkiffCode(source: SourceId, node: FileNode, profiles: List<ServerProfile>): Boolean {
        if (packageManager.checkSignatures(packageName, SKIFF_CODE_PACKAGE) != PackageManager.SIGNATURE_MATCH) return false
        val link = when (source) {
            SourceId.Local -> SkiffCodeLink.local(node.path)
            is SourceId.Remote -> {
                val profile = profiles.firstOrNull { it.id == source.profileId } ?: return false
                // Only the address goes over. Skiff Code keeps its own password and host key.
                SkiffCodeLink.remote(profile.username, profile.host, profile.port, node.path, profile.name)
            }
        }
        val intent = Intent(Intent.ACTION_VIEW, link.toUri()).setPackage(SKIFF_CODE_PACKAGE)
        // Skiff Code opens a link's path without asking only when it can see the link came from
        // here, and the system tells it that only for a sender that shares its identity. Without
        // this, every file tapped in Skiff would be confirmed again on the other side. Android 15
        // is where the other side can read it; below that there is nothing to share it with.
        val options = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            ActivityOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
        } else {
            null
        }
        return try {
            startActivity(intent, options)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private companion object {
        const val SKIFF_CODE_PACKAGE = "com.naki.skiff.code"
        val SKIFF_CODE_KINDS = setOf(FileKind.CODE, FileKind.TEXT, FileKind.MARKDOWN)
    }
}
