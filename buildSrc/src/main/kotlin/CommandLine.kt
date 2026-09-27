import java.io.InputStreamReader
import java.util.concurrent.TimeUnit


object CommandLine {

    /**
     * Runs [cmd] under bash and returns its trimmed stdout.
     *
     * stderr is inherited rather than piped. Piping it and never reading it meant a failing
     * command produced no output whatsoever - a broken `kubectl apply` looked identical to a
     * successful one - and risked deadlocking once the unread stderr buffer filled.
     */
    fun exec(cmd: String): String {
        val p = ProcessBuilder("/bin/bash", "-c", cmd)
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
        val stdout = InputStreamReader(p.inputStream).use {
            it.readText()
        }
        // Only ever log the first line: cmd can carry a whole secret payload.
        val label = cmd.lineSequence().first().take(80)
        if (!p.waitFor(60, TimeUnit.SECONDS)) {
            p.destroy()
            System.err.println("CommandLine: timed out after 60s: $label")
        } else if (p.exitValue() != 0) {
            // Not thrown on purpose - GitInfo relies on non-zero exits (detached HEAD, clean
            // tree) as ordinary signals and reads the empty stdout that comes back.
            System.err.println("CommandLine: exited ${p.exitValue()}: $label")
        }
        return stdout.trim()
    }
}
