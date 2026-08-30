import Foundation

/**
 Centralized logging for the JWPlayer React Native module on iOS.

 The iOS counterpart of `android/src/main/java/com/jwplayer/rnjwplayer/utils/JWLog.java`,
 with the same `Mode` semantics and the same `ERROR` default, so the two platforms no
 longer diverge on logging policy.

 WHY THIS EXISTS
 ---------------
 The iOS sources previously used 112 bare `print` calls with no gate of any kind. On a
 real device that output is discarded -- Swift `print` writes to stdout, which is not
 connected to the system log, so it never reaches Console.app, sysdiagnose, or a crash
 report. Nothing leaked. But the string interpolation ran on every call before the result
 was thrown away, and there was no way to ask for less (or more).

 `message` is an `@autoclosure`, so when a level is disabled the interpolation is NEVER
 evaluated -- `JWLog.d("position=\(expensive())")` costs one boolean check. That is the
 whole point: production pays nothing, diagnostics stay in the source.

 ENABLING IT WITHOUT A REBUILD
 -----------------------------
 A compile-time strip would have made the iOS test suites impossible, because their only
 oracle is this output (there is no `dumpsys media_session` equivalent on iOS, and
 `log stream` is unavailable to them). So the mode is read from the environment at first
 use, and `devicectl` can inject it at launch:

     xcrun devicectl device process launch --console \
       --environment-variables '{"JW_LOG":"ALL"}' \
       --device <id> org.ou.alldaf

 Accepted values: `ALL`, `ERROR`, `DISABLED` (case-insensitive). Absent means `ERROR`.

 Usage:
     JWLog.d("Sequence advanced: \(seq)")
     JWLog.e("setCategory failed: \(error)")
     JWLog.force("always printed, ignores the mode")
 */
public final class JWLog {

    public enum Mode: String {
        case all = "ALL"
        case error = "ERROR"
        case disabled = "DISABLED"
    }

    /// Default matches the Android committed default (`Mode.ERROR`).
    /// Read once from the environment so a test run can raise it without a rebuild.
    private static var mode: Mode = {
        let raw = ProcessInfo.processInfo.environment["JW_LOG"]?.uppercased() ?? ""
        return Mode(rawValue: raw) ?? .error
    }()

    private static let lock = NSLock()

    private init() {}

    // MARK: - control

    public static func setLoggingMode(_ newMode: Mode) {
        lock.lock(); mode = newMode; lock.unlock()
    }

    public static func getLoggingMode() -> Mode {
        lock.lock(); defer { lock.unlock() }
        return mode
    }

    private static func allowAll() -> Bool { getLoggingMode() == .all }

    private static func allowErrors() -> Bool {
        let m = getLoggingMode()
        return m == .all || m == .error
    }

    // MARK: - levels
    // `message` is @autoclosure on purpose: a disabled level must not build the string.

    public static func d(_ message: @autoclosure () -> String, tag: String = "RNJWPlayer") {
        if allowAll() { emit("D", tag, message()) }
    }

    public static func i(_ message: @autoclosure () -> String, tag: String = "RNJWPlayer") {
        if allowAll() { emit("I", tag, message()) }
    }

    public static func v(_ message: @autoclosure () -> String, tag: String = "RNJWPlayer") {
        if allowAll() { emit("V", tag, message()) }
    }

    public static func w(_ message: @autoclosure () -> String, tag: String = "RNJWPlayer") {
        if allowAll() { emit("W", tag, message()) }
    }

    /// Errors survive the default mode, matching Android where ERROR is the committed default.
    public static func e(_ message: @autoclosure () -> String, tag: String = "RNJWPlayer") {
        if allowErrors() { emit("E", tag, message()) }
    }

    /// Ignores the mode entirely. For verifying that logging reaches a capture at all.
    public static func force(_ message: @autoclosure () -> String, tag: String = "RNJWPlayer") {
        emit("F", tag, message())
    }

    // MARK: - output

    /// Deliberately `print` (stdout) and not `os_log`.
    ///
    /// `devicectl device process launch --console` captures the app's raw stdio, which is
    /// what every iOS suite reads. `os_log` writes to the unified log instead, which those
    /// suites cannot reach: `/usr/bin/log stream` is unavailable to them. Switching to
    /// `os_log` would silently blind the entire iOS test layer.
    ///
    /// The level and tag are a PREFIX, so existing marker strings remain intact as
    /// substrings and the suites' contract checks keep matching.
    private static func emit(_ level: String, _ tag: String, _ message: String) {
        print("JWLog/\(level) \(tag): \(message)")
    }
}
