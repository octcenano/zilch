package com.zilch.anonsurf.tor

import android.content.Context
import android.util.Log
import com.zilch.anonsurf.config.TorConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TorManager — Embedded Tor process manager for standalone operation.
 *
 * ═══════════════════════════════════════════════════════════════════════════
 *  ARCHITECTURE
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * This manager enables the app to run Tor WITHOUT requiring Orbot.
 * It operates in a fallback chain:
 *
 * 1. LOCAL TOR BINARY: If a `tor` binary is found (pre-extracted in the
 *    app's files directory, or bundled in assets), it starts a local Tor
 *    process with a generated torrc configuration.
 *
 * 2. EXISTING ORBOT: If no local binary is available, it probes the
 *    standard Orbot SOCKS5 port (127.0.0.1:9050) and uses it as the
 *    Tor provider if reachable.
 *
 * 3. FAILURE: If neither is available, the engine cannot start.
 *
 * Lifecycle:
 *
 *   STOPPED → STARTING → BOOTSTRAPPING (0%...100%) → READY
 *                 ↓              ↓
 *               ERROR          ERROR
 *
 * Binary detection order:
 *   1. {filesDir}/tor                    (previously extracted)
 *   2. {filesDir}/tor_extracted/tor      (from assets extraction)
 *   3. {nativeLibraryDir}/libtor.so      (NDK prebuilt, unusual)
 *   4. assets/tor                        (extracted at runtime)
 *
 * Safety:
 * - The tor binary is chmod'd to 0755 before execution.
 * - A minimal torrc is generated in the app's private directory.
 * - The DataDirectory is created if it doesn't exist.
 * - Process stdout/stderr are monitored for bootstrap progress.
 * - On stop, the process is destroyed gracefully (SIGTERM, then SIGKILL).
 */
class TorManager(private val context: Context) {

    companion object {
        private const val TAG = "TorManager"

        /** Delay between proxy availability checks during startup (ms) */
        private const val PROBE_PROXY_INTERVAL_MS = 500L

        /** Maximum time to wait for local Tor to bootstrap (ms) */
        private const val BOOTSTRAP_TIMEOUT_MS = 120_000L

        /** Maximum time to wait for a process to start before giving up (ms) */
        private const val PROCESS_START_TIMEOUT_MS = 5_000L
    }

    // ── State ───────────────────────────────────────────────────────────

    /**
     * Lifecycle state of the Tor process.
     *
     * STOPPED      — No Tor process running, no connection available.
     * STARTING     — Binary found, process being launched.
     * BOOTSTRAPPING — Tor is bootstrapping (building circuits). The
     *                  [bootstrapProgress] flow provides 0–100 percentage.
     * READY        — Tor has completed bootstrap (100%). SOCKS5 proxy
     *                  is accepting connections on the configured port.
     * ERROR        — An unrecoverable error occurred. Check [errorMessage]
     *                  for details.
     */
    enum class TorState {
        STOPPED,
        STARTING,
        BOOTSTRAPPING,
        READY,
        ERROR
    }

    /**
     * The source of the Tor SOCKS5 proxy.
     * Used to report whether we're running our own process or using Orbot.
     */
    enum class TorSource {
        /** Running an embedded local Tor process */
        LOCAL,

        /** Connected to an external Orbot instance */
        ORBOT,

        /** No Tor source available */
        NONE
    }

    private val _state = MutableStateFlow(TorState.STOPPED)

    /** Observable lifecycle state of the Tor manager. */
    val state: StateFlow<TorState> = _state.asStateFlow()

    private val _bootstrapProgress = MutableStateFlow(0)

    /** Observable bootstrap progress (0–100). Only meaningful in BOOTSTRAPPING state. */
    val bootstrapProgress: StateFlow<Int> = _bootstrapProgress.asStateFlow()

    private val _source = MutableStateFlow(TorSource.NONE)

    /** Which Tor source is currently active. */
    val source: StateFlow<TorSource> = _source.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)

    /** Human-readable error message when state is ERROR, or null. */
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // ── Internal state ──────────────────────────────────────────────────

    private var torProcess: Process? = null
    private var monitorJob: Job? = null
    private var managerScope: CoroutineScope? = null
    private val isRunning = AtomicBoolean(false)

    // ── Public API ──────────────────────────────────────────────────────

    /**
     * Start the Tor manager. This will attempt to:
     *
     * 1. Find and launch a local Tor binary.
     * 2. If no binary is found, probe for an existing Orbot proxy.
     * 3. Wait for the proxy to become available.
     *
     * This method is non-blocking: it launches coroutines that update
     * [state] as Tor progresses through its lifecycle.
     *
     * @param scope Parent CoroutineScope (typically viewModelScope or
     *              applicationScope). The manager adds a SupervisorJob
     *              so child failures don't cancel the parent.
     * @return true if a local Tor binary was found and launched, false
     *         if relying on Orbot fallback (caller should check [state]).
     */
    fun start(scope: CoroutineScope): Boolean {
        if (isRunning.getAndSet(true)) {
            Log.w(TAG, "TorManager already running")
            return _source.value == TorSource.LOCAL
        }

        managerScope = CoroutineScope(
            scope.coroutineContext + SupervisorJob() + Dispatchers.IO
        )

        Log.i(TAG, "Starting TorManager...")

        val binary = findTorBinary()

        if (binary != null) {
            Log.i(TAG, "Found local Tor binary: ${binary.absolutePath}")
            _source.value = TorSource.LOCAL
            startLocalTor(binary)
            return true
        }

        // No local binary — fall back to Orbot detection
        Log.i(TAG, "No local Tor binary found. Probing for Orbot on ${TorConfig.PROXY_HOST}:${TorConfig.PROXY_PORT}")
        _source.value = TorSource.ORBOT
        startOrbotFallback()
        return false
    }

    /**
     * Stop the Tor manager and all associated processes.
     *
     * If we started a local Tor process, it is destroyed gracefully:
     * first SIGTERM, then SIGKILL after a short grace period.
     */
    fun stop() {
        if (!isRunning.getAndSet(false)) return

        Log.i(TAG, "Stopping TorManager...")
        monitorJob?.cancel()
        monitorJob = null

        torProcess?.let { process ->
            Log.d(TAG, "Destroying Tor process (PID: ${process.hashCode()})")
            try {
                process.destroy()
            } catch (e: Exception) {
                Log.w(TAG, "Error destroying process: ${e.message}")
            }

            // Give the process a moment to exit gracefully, then force-kill
            managerScope?.launch {
                delay(2_000)
                try {
                    if (process.isAlive) {
                        Log.w(TAG, "Tor process didn't exit gracefully, force killing")
                        process.destroyForcibly()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error force-killing process: ${e.message}")
                }
            }
        }
        torProcess = null

        _state.value = TorState.STOPPED
        _bootstrapProgress.value = 0
        _source.value = TorSource.NONE
        _errorMessage.value = null

        managerScope?.cancel()
        managerScope = null

        Log.i(TAG, "TorManager stopped")
    }

    /**
     * Check whether the SOCKS5 proxy is currently reachable.
     *
     * This performs a simple TCP connect to the proxy port.
     * It does NOT verify that the proxy is actually Tor — use
     * [com.zilch.anonsurf.verification.TorIpVerifier] for that.
     *
     * @return true if the SOCKS5 port is accepting TCP connections
     */
    fun isProxyAvailable(): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(
                    InetSocketAddress(TorConfig.PROXY_HOST, TorConfig.PROXY_PORT),
                    TorConfig.CONNECTION_TIMEOUT_MS.toInt()
                )
                socket.isConnected
            }
        } catch (e: Exception) {
            false
        }
    }

    // ── Local Tor process management ────────────────────────────────────

    /**
     * Start a local Tor process using the provided binary.
     *
     * Steps:
     * 1. Generate a minimal torrc configuration.
     * 2. Ensure the DataDirectory exists.
     * 3. Launch the process with ProcessBuilder.
     * 4. Start monitoring stdout/stderr for bootstrap progress.
     */
    private fun startLocalTor(binary: File) {
        managerScope?.launch {
            try {
                _state.value = TorState.STARTING

                // Ensure the binary is executable
                if (!binary.canExecute()) {
                    val success = binary.setExecutable(true, false)
                    if (!success) {
                        Log.w(TAG, "setExecutable returned false, trying chmod via shell")
                        try {
                            Runtime.getRuntime().exec(arrayOf("chmod", "755", binary.absolutePath)).waitFor()
                        } catch (e: Exception) {
                            setError("Failed to make Tor binary executable: ${e.message}")
                            return@launch
                        }
                    }
                }

                // Generate torrc
                val torrcFile = generateTorrc()
                Log.i(TAG, "Generated torrc at: ${torrcFile.absolutePath}")

                // Ensure data directory exists
                val dataDir = getDataDirectory()
                if (!dataDir.exists()) {
                    val created = dataDir.mkdirs()
                    if (!created) {
                        setError("Failed to create Tor data directory: ${dataDir.absolutePath}")
                        return@launch
                    }
                }

                // Launch the Tor process
                val processBuilder = ProcessBuilder(
                    binary.absolutePath,
                    "-f", torrcFile.absolutePath,
                    "--defaults-torrc", torrcFile.absolutePath
                )
                processBuilder.directory(dataDir)
                processBuilder.redirectErrorStream(true) // Merge stderr into stdout

                // Set environment
                val env = processBuilder.environment()
                env["HOME"] = dataDir.absolutePath
                env["TOR_DATA_DIRECTORY"] = dataDir.absolutePath

                Log.i(TAG, "Launching Tor: ${binary.absolutePath} -f ${torrcFile.absolutePath}")

                torProcess = processBuilder.start()
                _state.value = TorState.BOOTSTRAPPING

                // Start monitoring the process output
                monitorProcess(torProcess!!)

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start local Tor: ${e.message}", e)
                setError("Failed to start Tor process: ${e.message}")
            }
        }
    }

    /**
     * Monitor the Tor process stdout for bootstrap progress and lifecycle events.
     *
     * Tor outputs lines like:
     *   [notice] Bootstrapped 0% (conn_start): Connecting to a relay
     *   [notice] Bootstrapped 5% (conn_done): Connected to a relay
     *   ...
     *   [notice] Bootstrapped 100% (done): Done
     *
     * We parse these lines to update [bootstrapProgress] and detect
     * when Tor is fully operational.
     */
    private fun monitorProcess(process: Process) {
        monitorJob = managerScope?.launch {
            try {
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?

                while (isActive) {
                    line = reader.readLine() ?: break

                    Log.d(TAG, "tor: $line")

                    // Parse bootstrap progress
                    val progress = parseBootstrapProgress(line)
                    if (progress != null) {
                        _bootstrapProgress.value = progress
                        Log.i(TAG, "Bootstrap progress: $progress%")

                        if (progress >= 100) {
                            _state.value = TorState.READY
                            Log.i(
                                TAG,
                                "Tor is READY — SOCKS5 proxy active on ${TorConfig.PROXY_HOST}:${TorConfig.PROXY_PORT}"
                            )
                            return@launch
                        }
                    }

                    // Check for fatal errors in Tor output
                    if (line.contains("[err]") || line.contains("[fatal]")) {
                        Log.e(TAG, "Tor fatal error: $line")
                        setError("Tor error: $line")
                        return@launch
                    }

                    // Check for common startup errors
                    if (line.contains("Could not bind to") || line.contains("Address already in use")) {
                        Log.e(TAG, "Tor address bind failure: $line")
                        setError("Port ${TorConfig.PROXY_PORT} is already in use. Another Tor instance may be running.")
                        return@launch
                    }

                    if (line.contains("Failed to parse/validate conf")) {
                        Log.e(TAG, "Tor config error: $line")
                        setError("Tor configuration error: $line")
                        return@launch
                    }
                }

                // Process exited before reaching 100% bootstrap
                val exitCode = withContext(Dispatchers.IO) {
                    try {
                        process.waitFor()
                    } catch (e: Exception) {
                        -1
                    }
                }

                if (isRunning.get()) {
                    Log.e(TAG, "Tor process exited unexpectedly with code: $exitCode")
                    setError("Tor process exited with code $exitCode")
                }

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isRunning.get()) {
                    Log.e(TAG, "Error monitoring Tor process: ${e.message}", e)
                    setError("Error monitoring Tor process: ${e.message}")
                }
            }
        }
    }

    // ── Orbot fallback ──────────────────────────────────────────────────

    /**
     * Probe for an existing Orbot SOCKS5 proxy and wait for it to
     * become available.
     *
     * This is a non-blocking fallback: it periodically checks if the
     * SOCKS5 port is reachable. If Orbot starts later, we'll detect it.
     */
    private fun startOrbotFallback() {
        _state.value = TorState.STARTING

        managerScope?.launch {
            val deadline = System.currentTimeMillis() + BOOTSTRAP_TIMEOUT_MS

            while (isActive && System.currentTimeMillis() < deadline) {
                if (isProxyAvailable()) {
                    Log.i(TAG, "Orbot proxy detected on ${TorConfig.PROXY_HOST}:${TorConfig.PROXY_PORT}")
                    _state.value = TorState.READY
                    _bootstrapProgress.value = 100
                    return@launch
                }
                delay(PROBE_PROXY_INTERVAL_MS)
            }

            // Timeout — Orbot not found
            if (isRunning.get()) {
                Log.w(TAG, "Orbot not detected within timeout")
                setError(
                    "No Tor source available. Install Orbot or bundle a tor binary " +
                            "in the app's assets directory."
                )
            }
        }
    }

    // ── Tor binary detection ────────────────────────────────────────────

    /**
     * Search for a Tor binary in known locations.
     *
     * Search order:
     * 1. {filesDir}/tor                    — Previously extracted binary
     * 2. {filesDir}/tor_extracted/tor      — From assets extraction
     * 3. {nativeLibraryDir}/libtor.so      — NDK prebuilt
     * 4. assets/tor                        — Extracted at runtime
     *
     * @return The Tor binary File, or null if not found
     */
    private fun findTorBinary(): File? {
        // 1. Previously extracted binary in files dir
        val extractedBinary = File(context.filesDir, "tor")
        if (extractedBinary.exists() && extractedBinary.canExecute()) {
            Log.d(TAG, "Found pre-extracted binary: ${extractedBinary.absolutePath}")
            return extractedBinary
        }

        // 2. Extracted subdirectory
        val extractedSubdir = File(context.filesDir, "tor_extracted")
        val subdirBinary = File(extractedSubdir, "tor")
        if (subdirBinary.exists() && subdirBinary.canExecute()) {
            Log.d(TAG, "Found binary in subdirectory: ${subdirBinary.absolutePath}")
            return subdirBinary
        }

        // 3. Native library directory (unlikely for a standalone binary, but possible)
        val nativeLibDir = File(context.applicationInfo.nativeLibraryDir)
        val nativeBinary = File(nativeLibDir, "libtor.so")
        if (nativeBinary.exists()) {
            Log.d(TAG, "Found native library: ${nativeBinary.absolutePath}")
            return nativeBinary
        }

        // 4. Extract from assets
        val assetBinary = extractFromAssets()
        if (assetBinary != null) {
            Log.d(TAG, "Extracted binary from assets: ${assetBinary.absolutePath}")
            return assetBinary
        }

        Log.d(TAG, "No Tor binary found in any location")
        return null
    }

    /**
     * Attempt to extract the Tor binary from the app's assets.
     *
     * Looks for assets named "tor" or "tor_binary". If found,
     * extracts to {filesDir}/tor and marks as executable.
     *
     * @return The extracted binary File, or null if no asset found
     */
    private fun extractFromAssets(): File? {
        val assetNames = listOf("tor", "tor_binary", "tor.so")

        for (assetName in assetNames) {
            try {
                val assetManager = context.assets
                val files = assetManager.list("") ?: continue

                // Check if the asset exists (directly or in a subdirectory)
                val found = assetName in files
                if (!found) {
                    // Try assets/tor/ directory
                    val torAssets = assetManager.list("tor") ?: continue
                    if (assetName !in torAssets) continue
                }

                val targetDir = File(context.filesDir, "tor_extracted")
                targetDir.mkdirs()
                val targetFile = File(targetDir, "tor")

                context.assets.open(assetName).use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Make executable
                targetFile.setReadable(true, false)
                targetFile.setExecutable(true, false)

                // Also try chmod for robustness
                try {
                    Runtime.getRuntime().exec(arrayOf("chmod", "755", targetFile.absolutePath)).waitFor()
                } catch (e: Exception) {
                    Log.w(TAG, "chmod failed (may still work): ${e.message}")
                }

                Log.i(TAG, "Extracted tor binary from assets/$assetName to ${targetFile.absolutePath}")
                return targetFile

            } catch (e: java.io.FileNotFoundException) {
                // Asset doesn't exist, try next
                continue
            } catch (e: Exception) {
                Log.w(TAG, "Failed to extract asset $assetName: ${e.message}")
                continue
            }
        }

        return null
    }

    // ── Tor configuration generation ────────────────────────────────────

    /**
     * Generate a minimal torrc configuration file for the embedded process.
     *
     * The configuration is intentionally minimal and locked down:
     * - SocksPort on loopback only (127.0.0.1:9050)
     * - No control port (no remote control possible)
     * - No entry/exit node restrictions (Tor chooses optimal paths)
     * - Logging to stdout for bootstrap monitoring
     *
     * @return The torrc File (in the app's files directory)
     */
    private fun generateTorrc(): File {
        val torrcFile = File(context.filesDir, TorConfig.TOR_TORRC_FILE_NAME)

        val config = buildString {
            appendLine("# Auto-generated by Zilch TorManager — do not edit")
            appendLine("# Generated at: ${System.currentTimeMillis()}")
            appendLine()

            // SOCKS5 proxy — loopback only for security
            appendLine("SocksPort ${TorConfig.PROXY_HOST}:${TorConfig.PROXY_PORT}")
            appendLine()

            // Data directory for Tor state (cached consensus, keys, etc.)
            appendLine("DataDirectory ${getDataDirectory().absolutePath}")
            appendLine()

            // Logging — send to stdout so we can monitor bootstrap progress
            appendLine("Log notice stdout")
            appendLine()

            // Do NOT run as daemon — we manage the process lifecycle
            appendLine("RunAsDaemon 0")
            appendLine()

            // Disable control port — no remote control, no security risk
            appendLine("ControlPort 0")
            appendLine()

            // Disable cookie authentication (not needed with no control port)
            appendLine("CookieAuthentication 0")
            appendLine()

            // Connection timeout for circuits (seconds)
            appendLine("CircuitStreamTimeout ${TorConfig.TOR_CIRCUIT_STREAM_TIMEOUT}")
            appendLine()

            // Keep periods short for faster circuit renewal
            appendLine("KeepAlivePeriod 60")
            appendLine()

            // Use built-in consensus (don't try to fetch a new one immediately)
            appendLine("ClientUseConsensus 1")
            appendLine()

            // Don't act as a relay or directory — client only
            appendLine("ORPort 0")
            appendLine("DirPort 0")
            appendLine()

            // Disable bridge usage by default (can be enabled via config)
            appendLine("UseBridges 0")
            appendLine()

            // Disable SOCKS auth (the proxy client doesn't use auth)
            appendLine("SocksPolicy accept 127.0.0.1")
            appendLine("SocksPolicy reject *")
        }

        torrcFile.writeText(config)
        torrcFile.setReadable(true, false)

        Log.d(TAG, "Generated torrc (${config.length} bytes)")
        return torrcFile
    }

    // ── Utility methods ─────────────────────────────────────────────────

    /**
     * Get or create the Tor data directory.
     *
     * This directory stores cached consensus documents, keys, and
     * other persistent Tor state. It should persist across app restarts
     * to avoid re-downloading the entire consensus.
     */
    private fun getDataDirectory(): File {
        val dir = File(context.filesDir, TorConfig.TOR_DATA_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Parse a Tor log line to extract bootstrap progress percentage.
     *
     * Tor output format:
     *   [notice] Bootstrapped 5% (conn_done): Connected to a relay
     *
     * @return The percentage (0–100), or null if not a bootstrap line
     */
    private fun parseBootstrapProgress(line: String): Int? {
        // Match patterns like "Bootstrapped 45% (some_desc): ..."
        val regex = Regex("""Bootstrapped\s+(\d+)%""")
        val match = regex.find(line) ?: return null

        return match.groupValues[1].toIntOrNull()
    }

    /**
     * Set the manager into an error state with a descriptive message.
     */
    private fun setError(message: String) {
        Log.e(TAG, "ERROR: $message")
        _state.value = TorState.ERROR
        _errorMessage.value = message
        isRunning.set(false)
    }

    /**
     * Check if the process is still alive.
     *
     * Note: [Process.isAlive] was added in API 26, which is our minimum.
     */
    private val Process.isAlive: Boolean
        get() = try {
            // Try to get the exit value — if it throws, the process is still running
            exitValue()
            false
        } catch (e: IllegalThreadStateException) {
            true
        }
}
