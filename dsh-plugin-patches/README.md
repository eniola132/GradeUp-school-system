# Local patches to third-party DSH plugins

## dsh-self-upgrade v1.1.4 — `shell.run` does not exist on DSH 0.2.0-rc.2

**Symptom:** every `dsh_upgrade_*` tool fails immediately with
`Error: shell.run is not a function`.

**Cause:** the plugin calls `shell.run(spec)`, but the Host shell service
(`@deepseek-ai/dsh-shell`, seen via `cordis_inspect_query` Service `shell`)
exposes only:

- `resolve(request: ShellExecRequest): ShellExecSpec`
- `execute(spec: ShellExecSpec): Promise<ShellExecution>`

There is no `run()`. The plugin's own comment shows it was written against
`dsh 0.1.3-alpha.2`, and its version guard only checked `typeof shell.resolve`
before still calling the missing `run`.

**Fix** — in `node_modules/dsh-self-upgrade/index.js`, function `sh()`:

```js
// before
const r = await shell.run(spec);

// after
const execution = await shell.execute(spec);
const r = typeof execution.result === 'function' ? await execution.result() : execution;
```

This matches the official caller `@deepseek-ai/dsh-tool-bash`:

```js
const result = await (await ctx.shell.execute(ctx.shell.resolve(request))).result();
```

`ShellExecution.result()` resolves to `ShellRunResult` (`exitCode`, `timedOut`,
`stdout`/`stderr` as `CollectedOutput.text`), which is what `sh()` already read.

**Reinstalling the plugin overwrites this patch** (the installed copy is a real
directory with link count 1, not a hardlink to the pnpm store). Re-apply with:

```sh
bash /home/ak-david-dev/GradeUp/dsh-plugin-patches/apply-dsh-self-upgrade-shell-fix.sh
```
