package com.naki.skiff.code.project

import com.naki.skiff.code.project.GitChange.Kind.Modified
import com.naki.skiff.code.project.GitChange.Kind.Untracked
import org.junit.Assert.assertEquals
import org.junit.Test

/** `git status --porcelain=v1 -z` as `git` writes it, read the way the sidebar colors it. */
class GitChangeTest {

    private fun parse(vararg records: String) = GitChange.parse(records.joinToString("\u0000", postfix = "\u0000"))

    @Test
    fun `nothing is nothing`() {
        assertEquals(emptyList<GitChange>(), GitChange.parse(""))
    }

    @Test
    fun `a change in the tree or the index is a modification`() {
        assertEquals(
            listOf(GitChange("a", Modified), GitChange("b", Modified), GitChange("c", Modified), GitChange("d", Modified)),
            parse(" M a", "M  b", "MM c", " T d"),
        )
    }

    @Test
    fun `a path HEAD does not have is new`() {
        assertEquals(
            listOf(GitChange("a", Untracked), GitChange("b", Untracked), GitChange("c", Untracked)),
            parse("?? a", "A  b", "AM c"),
        )
    }

    @Test
    fun `a rename keeps its new name and drops the old one`() {
        assertEquals(
            listOf(GitChange("new name", Untracked), GitChange("copy", Untracked), GitChange("after", Modified)),
            parse("R  new name", "old name", "C  copy", "original", " M after"),
        )
    }

    @Test
    fun `a folder git does not track is one line, marked`() {
        assertEquals(listOf(GitChange("notes/deeper", Untracked, directory = true)), parse("?? notes/deeper/"))
    }

    @Test
    fun `a path not in the tree is left out`() {
        assertEquals(emptyList<GitChange>(), parse(" D a", "D  b", "MD c", "AD d", "DD e"))
    }

    @Test
    fun `a conflict is a modification, even one side's deletion`() {
        assertEquals(
            listOf("a", "b", "c", "d", "e", "f").map { GitChange(it, Modified) },
            parse("UU a", "AA b", "AU c", "UA d", "UD e", "DU f"),
        )
    }

    @Test
    fun `a path is taken as it is`() {
        assertEquals(
            listOf(GitChange("line\nbreak", Untracked), GitChange("한글 이름.md", Modified), GitChange(" spaced ", Modified)),
            parse("?? line\nbreak", " M 한글 이름.md", " M  spaced "),
        )
    }
}
