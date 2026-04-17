# forelich-java-som
Neural Networks with Java, by Jochen Froehlich (Copyright 1996-1997, All Rights Reserved)

Porting to Python using ChatGPT Codex, by Yoonsuck Choe (The Unlicense)


## KFM3D (Kohonen Applet) Setup And Run

The original project is a legacy Java applet project (1996-1997 era). Modern browsers no longer run Java applets directly, so this repository provides a standalone host:

`tools/KFM3DHost.java`

### 1) Install Java 8 (recommended)

The applet works best on Java 8. Newer Java releases (especially Java 21+) break some legacy thread APIs used by `KFM3D`.

Example with SDKMAN:

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 8.0.462-tem
sdk use java 8.0.462-tem
java -version
```

### 2) Compile the host

From the repository root:

```bash
javac -cp jfroehl/zip/nnwj-applet.zip tools/KFM3DHost.java
```

### 3) Run KFM3D

```bash
java -cp jfroehl/zip/nnwj-applet.zip:tools KFM3DHost
```

### Windows Notes (`cmd.exe` / PowerShell)

On Windows, use `;` as the classpath separator (not `:`).

`cmd.exe`:

```bat
javac -cp jfroehl\zip\nnwj-applet.zip tools\KFM3DHost.java
java -cp ".;tools;jfroehl\zip\nnwj-applet.zip" KFM3DHost
```

PowerShell:

```powershell
javac -cp jfroehl\zip\nnwj-applet.zip tools\KFM3DHost.java
java -cp '.;tools;jfroehl\zip\nnwj-applet.zip' KFM3DHost
```

If you have multiple Java versions installed, you can run explicitly with Java 8:

```bash
~/.sdkman/candidates/java/8.0.462-tem/bin/javac -cp jfroehl/zip/nnwj-applet.zip tools/KFM3DHost.java
~/.sdkman/candidates/java/8.0.462-tem/bin/java -cp jfroehl/zip/nnwj-applet.zip:tools KFM3DHost
```

## What Was Needed To Make It Run

1. Verified where the applet really lives:
- HTML launcher in `jfroehl/diplom/e-sample-applet.html`
- Full class payload in `jfroehl/zip/nnwj-applet.zip`

2. Confirmed there are no `<param>` runtime arguments in the legacy HTML, so blank output was not caused by missing HTML parameters.

3. Added `tools/KFM3DHost.java` to run the applet outside the browser with applet host emulation (`AppletStub` / `AppletContext`) and lifecycle handling.

4. Diagnosed Java 21 incompatibility (`Thread.stop()` throws `UnsupportedOperationException`), which prevented reset/parameter behavior.

5. Switched runtime to Java 8 and fixed host compatibility with Java 8 APIs, resulting in fully working KFM3D behavior.
