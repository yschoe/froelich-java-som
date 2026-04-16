# Session Log (Redacted)

This is a detailed transcript-style summary of the troubleshooting and implementation session to run the legacy KFM3D applet.

Redaction policy:
- Any sensitive directory/user info is redacted as `[████]`.

## 1) Initial Request And Scope

- Goal: determine if this legacy Java applet project can still run, especially the Kohonen Feature Map applet.
- Suspected paths from user were checked; actual project paths were:
  - `jfroehl/diplom`
  - `jfroehl/zip`

## 2) Repository Analysis

- Enumerated project files and confirmed presence of:
  - `jfroehl/zip/nnwj-applet.zip`
  - `jfroehl/zip/nnwj-classes.zip`
  - `jfroehl/zip/nnwj-bpn.zip`
  - `jfroehl/diplom/e-sample-applet.html`
  - many legacy HTML docs and assets.
- Verified applet launcher HTML:
  - `<applet code="KFM3D.class" width=400 height=500>`
- Verified class format:
  - `KFM3D.class` version `45.3` (Java 1.1 era).

## 3) ZIP Payload Inspection

- `nnwj-applet.zip` contained full KFM3D runtime classes, including:
  - `KFM3D.class`
  - `ParametersFrame.class`
  - `ThreeD.class`
  - `Point3D.class`
  - neural net support classes.
- `nnwj-classes.zip` contained reusable network classes.
- `nnwj-bpn.zip` contained:
  - `BPN.class`, `BPN.java`, and data files.

## 4) Feasibility Checks

- Ran BPN demo from extracted ZIP and confirmed legacy classes execute on modern JVM.
- Confirmed applet tag exists and no modern browser plugin path is available.
- Confirmed modern alternatives:
  - OpenWebStart does not support applets.
  - Applet emulation/hosting or older Java runtime is required.

## 5) First Standalone Host Implementation

- Added `tools/KFM3DHost.java` as an AWT applet host.
- Added README instructions for running KFM3D standalone.
- Smoke-tested host startup.
- Initial lifecycle ordering issue was fixed (frame visibility/init/start ordering).

## 6) Parameter Investigation

- User observed blank window / ineffective parameter reset behavior.
- Investigated all `jfroehl/diplom/*.html` and `jfroehl/diplom/download/build/*.html`:
  - found no `<param>` tags for applet runtime configuration.
  - applet HTML only declared code and size.
- Inspected class strings for `KFM3D` and `ParametersFrame` to understand UI flow.

## 7) Applet Emulation Upgrade

- Replaced simple host with fuller emulation:
  - `AppletStub` implementation
  - `AppletContext` implementation
  - code/document base wiring
  - status display
  - uncaught exception logging.

## 8) Root Cause Found On Java 21

- Runtime logs showed repeated exceptions from applet event actions:
  - `UnsupportedOperationException` at `java.lang.Thread.stop(...)`
  - called from `KFM3D.action(...)`
- This explained why control interactions (like Reset / parameter apply) did not work correctly on Java 21.

## 9) Java Version Guidance And Host Compatibility

- Guidance changed to prefer Java 8-17, with Java 8 as best target for this applet.
- User attempted Java 8 run and hit class/source invocation confusion:
  - corrected with compile + run class workflow.
- User then hit Java 8 compile error:
  - `Runtime.version()` unavailable on Java 8.
- Patched host to detect Java feature version using Java 8-compatible parsing of:
  - `java.specification.version`.

## 10) Final Outcome

- User switched to Java 8 and confirmed:
  - display works
  - parameter changes/reset now work as expected.

## 11) Files Added/Changed During Session

- Added:
  - `tools/KFM3DHost.java`
- Updated:
  - `README.md`

## 12) Representative Commands Used (Redacted Where Needed)

- File discovery and scans:
  - `rg --files jfroehl`
  - `rg -n "<applet|<param|code=|archive=" jfroehl/diplom/*.html`
  - `unzip -l jfroehl/zip/nnwj-applet.zip`
- Runtime checks:
  - `java -version`
  - `java -cp jfroehl/zip/nnwj-bpn.zip BPN 5000`
  - `java -cp jfroehl/zip/nnwj-applet.zip tools/KFM3DHost.java`
- Java 8 run path:
  - `~/.sdkman/candidates/java/8.0.462-tem/bin/javac -cp jfroehl/zip/nnwj-applet.zip tools/KFM3DHost.java`
  - `~/.sdkman/candidates/java/8.0.462-tem/bin/java -cp jfroehl/zip/nnwj-applet.zip:tools KFM3DHost`

## 13) Redacted Absolute Paths Seen During Work

- `/home/[████]/git/froelich-java-som/...`
- `/home/[████]/...`
