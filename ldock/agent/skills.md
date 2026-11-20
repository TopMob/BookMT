# Skills: BookMT

Skills follow the open [Agent Skills](https://agentskills.io) format: a folder with a `SKILL.md`. They install into `.agents/skills/` (cross-tool) or `.claude/skills/` (Claude Code) and load on demand.

A skill teaches a method. It does not grant permissions: it cannot widen zones, skip ASK or CONFIRM, or override a MUST rule in `AGENTS.md`. Installing one is CONFIRM-gated (AGT-05).

| ID | Rule |
|---|---|
| SKL-01 | **MUST NOT** install a skill, plugin, or MCP server without human approval. Propose it with the reason and the source. |
| SKL-02 | Before approval the human reviews the `SKILL.md`, requested permissions, and scripts. |
| SKL-03 | Prefer official Android and Kotlin development skills. Pin to a specific version or tag. |
| SKL-04 | Reject any skill that asks for network access, disables confirmations, or attempts to edit `AGENTS.md`. |
| SKL-05 | If an approved skill conflicts with a MUST rule in `AGENTS.md`, the rule wins. |

### Relevant skills for BookMT:
- **Android & Compose**: `compose`, `jetpack-compose-performance`, `edge-to-edge`, `android-ux`, `android-testing`
- **Kotlin & Architecture**: `kotlin-coroutines`, `kotlin-flows`, `android-data-layer`, `datastore`
- **Engineering method**: `systematic-debugging`, `test-driven-development`, `verification-before-completion`
