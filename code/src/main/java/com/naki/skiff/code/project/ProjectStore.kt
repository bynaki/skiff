package com.naki.skiff.code.project

import androidx.datastore.core.DataStore
import com.naki.skiff.code.data.SkiffCodeData
import com.naki.skiff.fs.FsPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A git working tree on a server, made a project by the user. [root] is canonical, as
 * [GitScopeFinder] returns it, so a file is matched against it by its canonical folder too.
 */
@Serializable
data class Project(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val root: String,
) {
    val name: String get() = FsPath.name(root)
}

/**
 * The projects, kept in the same JSON blob as the profiles they belong to. It takes the [DataStore]
 * rather than [com.naki.skiff.code.data.SkiffCodeStore], which shares it, so it can be tested on a
 * plain JVM the same way.
 */
class ProjectStore(private val dataStore: DataStore<SkiffCodeData>) {

    val projects: Flow<List<Project>> = dataStore.data.map { it.projects }

    /** The project with [root] on that server, made now unless there is one already. */
    suspend fun add(profileId: String, root: String): Project {
        var added: Project? = null
        dataStore.updateData { data ->
            added = data.projects.firstOrNull { it.profileId == profileId && it.root == root }
            if (added != null) return@updateData data
            val project = Project(profileId = profileId, root = root).also { added = it }
            data.copy(projects = data.projects + project)
        }
        return added!!
    }

    /** Forgets the project. Nothing on the server is touched: a project is only this record of it. */
    suspend fun remove(id: String) {
        dataStore.updateData { data -> data.copy(projects = data.projects.filterNot { it.id == id }) }
    }

    suspend fun byId(id: String): Project? = projects.first().firstOrNull { it.id == id }

    /** The project [dir] is in, the deepest one when roots nest. [dir] must be canonical. */
    suspend fun containing(profileId: String, dir: String): Project? =
        projects.first()
            .filter { it.profileId == profileId && FsPath.isAncestorOrSame(it.root, dir) }
            .maxByOrNull { it.root.length }
}
