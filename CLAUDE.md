# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**All project guidance lives in [AGENTS.md](AGENTS.md)** — commands, architecture, toolchain
constraints, and testing. Read it before making changes. Keep it as the single source: add new
guidance there, not here.

This file is only for things specific to Claude Code.

---

An OpenAI Codex config exists at `~/.codex/config.toml` and has not been imported. `/import`
is not available over Remote Control, so it has to be run as `claude import` from a terminal.
Do not hand-copy the config.
