package com.naki.skiff.ui

import android.content.Intent
import android.os.Bundle
import android.webkit.MimeTypeMap
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.naki.skiff.fs.FileNode
import com.naki.skiff.fs.FsPath
import com.naki.skiff.fs.SourceId
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
                if (state.storageGranted) {
                    WorkspaceScreen(vm, onOpenExternally = ::openExternally)
                } else {
                    StorageGate(onGranted = vm::refreshStorageGrant)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // The permission can only be granted in Settings, so re-check whenever we come back.
        viewModel?.refreshStorageGrant()
    }

    /**
     * Until the built-in preview viewer lands, tapping a file hands it to whatever app
     * the system offers. Only local files can be shared this way; remote files will be
     * cached locally first once transfers exist.
     */
    private fun openExternally(node: FileNode) {
        if (viewModel?.state?.value == null) return
        val side = viewModel?.state?.value?.activeSide ?: return
        val paneState = viewModel?.controller(side)?.state?.value ?: return
        if (paneState.sourceId !is SourceId.Local) return

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
}
