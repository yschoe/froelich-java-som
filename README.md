# forelich-java-som

Edited by ChatGPT Codex (GPT-6).
Neural Networks with Java, by Jochen Froehlich (Copyright 1996-1997, All Rights Reserved)
- [Initial version (wayback machine)](https://web.archive.org/web/19991013082458/http://rfhs8012.fh-regensburg.de/~saj39122/jfroehl/diplom/e-index.html)
- [2004 edition](https://nnwj.de/)

Porting to Python using ChatGPT Codex, by Yoonsuck Choe (The Unlicense). 
- Java 8 officially supported
- Up to Java 17 tested
- ***New***: See the ***Experimental*** section at the end for Java 21.

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

### Larger fonts

Add `--font-scale 1.5` for 50% larger fonts and windows (default: `1.0`, range: `0.5` to `4.0`):

```bash
javac -cp jfroehl/zip/nnwj-applet.zip tools/KFM3DHost.java
java -cp jfroehl/zip/nnwj-applet.zip:tools KFM3DHost --font-scale 1.5
```

This also scales the parameters window and preserves bold text. The same argument works with `nnwj-applet-patched.zip`. Use `--help` for usage.

### Larger visualization

Use `--display-scale` to enlarge the map geometry, input markers, and lines independently of fonts. Both scale options default to `1.0` and accept `0.5` through `4.0`.

The display option requires a rendering-patched archive and the updated host. Generate the two archives with:

```bash
python3 tools/patch_display.py
```

This creates `jfroehl/zip/nnwj-applet-display.zip` from the original applet and `jfroehl/zip/nnwj-applet-patched-display.zip` from the Java 21 compatibility version. The input ZIPs are preserved. The display-patched classes call a rendering helper in `KFM3DHost` and require that host on the classpath.

Compile into `java/`, then run with both options:

```bash
javac -source 8 -target 8 -d java tools/KFM3DHost.java
java -cp jfroehl/zip/nnwj-applet-patched-display.zip:java KFM3DHost --font-scale 1.5 --display-scale 2
```

On this machine, the compiler executable is absent; use its installed Java 21 module instead:

```bash
/usr/lib/jvm/java-21-openjdk-amd64/bin/java com.sun.tools.javac.Main -source 8 -target 8 -d java tools/KFM3DHost.java
```

Display scaling transforms drawing primitives before rasterization, preserving simulation inputs and learning parameters. It expands the canvas at startup and refreshes the rendering buffer and projection center on subsequent window resizing. Reset and parameter changes retain the selected scale. Very small manually resized windows can still clip the map.

Rendering integration checks (requires a graphical display):

```bash
mkdir -p scratch
javac -source 8 -target 8 -cp java -d scratch tools/DisplayScaleCheck.java
java -cp java:scratch:jfroehl/zip/nnwj-applet-patched-display.zip DisplayScaleCheck --font-scale 1.5 --display-scale 2
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

## Experimental

Java 21 is now supported. 
- Instead of `jfroehl/zip/nnwj-applet.zip`, use `jfroehl/zip/nnwj-applet-patched.zip`. 
- The patched zip was created using `tools/patchclass.py` (unzip, and patch the `KFM3D.class` file to replace `Thread.stop()` with `Thread.interrupt()`, the zip). 
- This is a temporary fix.

