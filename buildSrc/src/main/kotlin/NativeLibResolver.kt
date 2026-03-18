import java.io.File

object NativeLibResolver {

    data class LibFlags(
        val compilerOpts: List<String>,
        val linkerOpts: List<String>
    ) {
        /** Only -L paths (for linker search directories) */
        val linkerPaths: List<String> get() = linkerOpts.filter { it.startsWith("-L") }
    }

    val isMacOS = System.getProperty("os.name") == "Mac OS X"

    private val pkgConfigPath: String by lazy {
        val paths = mutableListOf<String>()
        System.getenv("CONDA_PREFIX")?.let { paths.add("$it/lib/pkgconfig") }
        if (File("/opt/homebrew/lib/pkgconfig").exists()) {
            paths.add("/opt/homebrew/lib/pkgconfig")
        }
        File("/opt/homebrew/opt").takeIf { it.exists() }?.listFiles()?.forEach { dir ->
            val pc = File(dir, "lib/pkgconfig")
            if (pc.exists()) paths.add(pc.absolutePath)
        }
        paths.joinToString(":")
    }

    /** Collected linker search paths (-L...) and rpath entries from all resolved libraries, for use in binary config */
    val macOsLinkerPaths = mutableSetOf<String>()

    fun resolve(pkgName: String): LibFlags? {
        if (!isMacOS) return null
        val env = if (pkgConfigPath.isNotEmpty()) "PKG_CONFIG_PATH=$pkgConfigPath " else ""
        val cflags = CommandLine.exec("${env}pkg-config --cflags $pkgName").takeIf { it.isNotBlank() }
        val libs = CommandLine.exec("${env}pkg-config --libs $pkgName").takeIf { it.isNotBlank() }
        if (cflags == null && libs == null) return null
        val flags = LibFlags(
            compilerOpts = cflags?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
            linkerOpts = libs?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()
        )
        macOsLinkerPaths.addAll(flags.linkerPaths)
        // Add -rpath for each -L so the dynamic linker finds dylibs at runtime
        flags.linkerPaths.forEach { lPath ->
            val dir = lPath.removePrefix("-L")
            macOsLinkerPaths.add("-Wl,-rpath,$dir")
        }
        return flags
    }
}
