import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.AWTEvent;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.Canvas;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.lang.reflect.Field;
import java.io.ByteArrayOutputStream;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone host for the legacy KFM3D applet with basic AppletStub/AppletContext emulation.
 *
 * Run from the repository root with:
 *   java -cp jfroehl/zip/nnwj-applet.zip tools/KFM3DHost.java
 * Add --font-scale 1.5 to enlarge text and windows by 50 percent.
 * Add --display-scale 2 with a display-patched ZIP to double visualization size.
 */
public final class KFM3DHost {
    private static final int APPLET_WIDTH = 400;
    private static final int APPLET_HEIGHT = 500;
    private static final AtomicBoolean THREAD_STOP_WARNING_EMITTED = new AtomicBoolean(false);
    private static final AtomicReference<HostContext> HOST_CONTEXT = new AtomicReference<>();
    private static double displayScale = 1.0;
    private static Color displayBackground;
    private static final String USAGE = "Usage: KFM3DHost [--font-scale FACTOR] [--display-scale FACTOR] (0.5 to 4.0; default 1.0)";

    private KFM3DHost() {
    }

    public static void main(String[] args) throws Exception {
        final double fontScale;
        if (args.length == 1 && ("--help".equals(args[0]) || "-h".equals(args[0]))) {
            System.out.println(USAGE);
            return;
        }
        try {
            Map<String, Double> scales = parseScales(args);
            fontScale = scales.get("--font-scale");
            displayScale = scales.get("--display-scale");
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println(USAGE);
            System.exit(2);
            return;
        }
        final URL codeBase = defaultCodeBase();
        final URL docBase = codeBase;
        final Map<String, String> params = new LinkedHashMap<>();
        final int javaFeature = detectJavaFeatureVersion();

        if (javaFeature >= 20) {
            System.err.println("[compat] Running on Java " + javaFeature + ".");
            System.err.println("[compat] KFM3D uses Thread.stop(); control actions like Reset may fail.");
            System.err.println("[compat] Use Java 8-17 for full applet behavior.");
        }

        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            if (isLegacyThreadStopFailure(error) && THREAD_STOP_WARNING_EMITTED.compareAndSet(false, true)) {
                String msg = "Legacy Thread.stop() failed on this Java. Use Java 8-17.";
                System.err.println("[compat] " + msg);
                HostContext context = HOST_CONTEXT.get();
                if (context != null) {
                    context.showStatus(msg);
                }
                return;
            }
            System.err.println("[uncaught:" + thread.getName() + "] " + error);
            error.printStackTrace(System.err);
        });

        EventQueue.invokeAndWait(() -> {
            try {
                launch(codeBase, docBase, params, fontScale);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
    }

    private static Map<String, Double> parseScales(String[] args) {
        Map<String, Double> scales = new LinkedHashMap<>();
        scales.put("--font-scale", 1.0);
        scales.put("--display-scale", 1.0);
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (int i = 0; i < args.length; i += 2) {
            String option = args[i];
            if (!scales.containsKey(option) || i + 1 >= args.length || !seen.add(option)) {
                throw new IllegalArgumentException("Expected each scale option once, followed by a number.");
            }
            double scale = Double.parseDouble(args[i + 1]);
            if (!Double.isFinite(scale) || scale < 0.5 || scale > 4.0) {
                throw new IllegalArgumentException(option + " must be between 0.5 and 4.0.");
            }
            scales.put(option, scale);
        }
        return scales;
    }

    // Called by patched KFM3D.draw(): scale primitives before rasterization.
    public static Graphics displayGraphics(Image image) {
        Graphics2D graphics = (Graphics2D) image.getGraphics();
        if (displayScale != 1.0) {
            int width = image.getWidth(null);
            int height = image.getHeight(null);
            graphics.setColor(displayBackground);
            graphics.fillRect(0, 0, width, height);
            graphics.translate(width / 2.0, height / 2.0);
            graphics.scale(displayScale, displayScale);
            graphics.translate(-width / 2.0, -height / 2.0);
        }
        return graphics;
    }

    private static Field appletField(Applet applet, String name) throws Exception {
        Field field = applet.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static void requireDisplayPatch(Class<?> appletClass) throws Exception {
        try (InputStream input = appletClass.getResourceAsStream("/KFM3D.class")) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int count;
            while (input != null && (count = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            if (!new String(bytes.toByteArray(), "ISO-8859-1").contains("displayGraphics")) {
                throw new IllegalArgumentException("--display-scale requires nnwj-applet-display.zip or "
                        + "nnwj-applet-patched-display.zip. Run python3 tools/patch_display.py.");
            }
        }
    }

    private static void refreshDisplay(Applet applet, Canvas canvas) throws Exception {
        synchronized (applet) {
            Dimension size = canvas.getSize();
            if (size.width <= 0 || size.height <= 0) {
                return;
            }
            Field dimension = appletField(applet, "d");
            Dimension old = (Dimension) dimension.get(applet);
            Object projection = appletField(applet, "td").get(applet);
            if (projection == null || size.equals(old)) {
                return;
            }
            Image image = canvas.createImage(size.width + 1, size.height + 1);
            if (image == null) {
                return;
            }
            Image previous = (Image) appletField(applet, "offscreen").get(applet);
            dimension.set(applet, size);
            appletField(applet, "offscreen").set(applet, image);
            for (String axis : new String[] {"midX", "midY"}) {
                Field center = projection.getClass().getDeclaredField(axis);
                center.setAccessible(true);
                center.setInt(projection, (("midX".equals(axis) ? size.width : size.height) + 1) / 2);
            }
            applet.getClass().getMethod("draw").invoke(applet);
            if (previous != null) {
                previous.flush();
            }
        }
    }

    private static void launch(URL codeBase, URL docBase, Map<String, String> params, double fontScale) throws Exception {
        Class<?> appletClass = Class.forName("KFM3D");
        if (displayScale != 1.0) {
            requireDisplayPatch(appletClass);
        }
        final Applet applet = (Applet) appletClass.getDeclaredConstructor().newInstance();
        displayBackground = (Color) appletField(applet, "sandColor").get(applet);
        final Frame frame = new Frame("3D Kohonen Feature Map (KFM3D)");
        final Label status = new Label("Status: starting...");

        final HostContext context = new HostContext(status, applet);
        HOST_CONTEXT.set(context);
        final HostStub stub = new HostStub(codeBase, docBase, params, context, applet);
        applet.setStub(stub);

        frame.setLayout(new BorderLayout());
        Panel root = new Panel(new BorderLayout());
        applet.setSize(APPLET_WIDTH, APPLET_HEIGHT);
        root.add(applet, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        frame.add(root, BorderLayout.CENTER);
        frame.setSize(APPLET_WIDTH + 20, APPLET_HEIGHT + 80);

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown(applet, frame);
            }
        });

        // ParametersFrame is a separate, unowned legacy window and may be created later.
        final Map<Window, Boolean> scaledWindows = new java.util.WeakHashMap<>();
        if (fontScale != 1.0) {
            Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
                if (event.getID() == WindowEvent.WINDOW_OPENED) {
                    Window window = ((WindowEvent) event).getWindow();
                    if ("ParametersFrame".equals(window.getClass().getName())) {
                        EventQueue.invokeLater(() -> scaleWindowOnce(window, fontScale, scaledWindows));
                    }
                }
            }, AWTEvent.WINDOW_EVENT_MASK);
        }

        frame.setVisible(true);
        applet.init();
        if (fontScale != 1.0) {
            scaleWindowOnce(frame, fontScale, scaledWindows);
            for (Window window : Window.getWindows()) {
                if ("ParametersFrame".equals(window.getClass().getName())) {
                    scaleWindowOnce(window, fontScale, scaledWindows);
                }
            }
        }
        if (displayScale != 1.0) {
            frame.validate();
            Canvas canvas = (Canvas) appletField(applet, "drawCanvas").get(applet);
            Dimension size = canvas.getSize();
            frame.setSize(frame.getWidth() + (int) Math.ceil(size.width * (displayScale - 1.0)),
                    frame.getHeight() + (int) Math.ceil(size.height * (displayScale - 1.0)));
            frame.validate();
            canvas.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent event) {
                    try {
                        refreshDisplay(applet, canvas);
                    } catch (Exception error) {
                        throw new IllegalStateException("Could not resize the display buffer", error);
                    }
                }
            });
        }
        applet.validate();
        Toolkit.getDefaultToolkit().sync();
        applet.start();
        context.showStatus("running");
    }

    private static void scaleWindowOnce(Window window, double scale, Map<Window, Boolean> scaled) {
        if (scaled.put(window, Boolean.TRUE) != null) {
            return;
        }
        // Capture inherited fonts before changing parents, avoiding compounded scaling.
        Map<Component, Font> fonts = new IdentityHashMap<>();
        collectFonts(window, fonts);
        for (Map.Entry<Component, Font> entry : fonts.entrySet()) {
            Font font = entry.getValue();
            entry.getKey().setFont(font.deriveFont((float) (font.getSize2D() * scale)));
        }
        Dimension original = window.getSize();
        window.invalidate();
        Dimension preferred = window.getPreferredSize();
        window.setSize(Math.max((int) Math.ceil(original.width * scale), preferred.width),
                Math.max((int) Math.ceil(original.height * scale), preferred.height));
        window.validate();
    }

    private static void collectFonts(Component component, Map<Component, Font> fonts) {
        Font font = component.getFont();
        if (font != null) {
            fonts.put(component, font);
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                collectFonts(child, fonts);
            }
        }
    }

    private static URL defaultCodeBase() throws Exception {
        File base = new File("jfroehl/diplom/download/build").getCanonicalFile();
        return base.toURI().toURL();
    }

    private static int detectJavaFeatureVersion() {
        String spec = System.getProperty("java.specification.version", "");
        if (spec.startsWith("1.")) {
            spec = spec.substring(2);
        }
        int dot = spec.indexOf('.');
        if (dot >= 0) {
            spec = spec.substring(0, dot);
        }
        try {
            return Integer.parseInt(spec);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static boolean isLegacyThreadStopFailure(Throwable error) {
        if (!(error instanceof UnsupportedOperationException)) {
            return false;
        }
        StackTraceElement[] stack = error.getStackTrace();
        for (StackTraceElement ste : stack) {
            if ("java.lang.Thread".equals(ste.getClassName()) && "stop".equals(ste.getMethodName())) {
                return true;
            }
        }
        return false;
    }

    private static void shutdown(Applet applet, Frame frame) {
        try {
            applet.stop();
        } catch (Throwable t) {
            System.err.println("[shutdown] stop failed: " + t);
        }
        try {
            applet.destroy();
        } catch (Throwable t) {
            System.err.println("[shutdown] destroy failed: " + t);
        }
        frame.dispose();
        System.exit(0);
    }

    private static final class HostStub implements AppletStub {
        private final URL codeBase;
        private final URL docBase;
        private final Map<String, String> params;
        private final AppletContext context;
        private final Applet applet;
        private volatile boolean active = true;

        private HostStub(URL codeBase, URL docBase, Map<String, String> params, AppletContext context, Applet applet) {
            this.codeBase = codeBase;
            this.docBase = docBase;
            this.params = params;
            this.context = context;
            this.applet = applet;
        }

        @Override
        public boolean isActive() {
            return active;
        }

        @Override
        public URL getDocumentBase() {
            return docBase;
        }

        @Override
        public URL getCodeBase() {
            return codeBase;
        }

        @Override
        public String getParameter(String name) {
            return params.get(name);
        }

        @Override
        public AppletContext getAppletContext() {
            return context;
        }

        @Override
        public void appletResize(int width, int height) {
            applet.setSize(width, height);
            applet.validate();
        }
    }

    private static final class HostContext implements AppletContext {
        private final Label status;
        private final Applet applet;

        private HostContext(Label status, Applet applet) {
            this.status = status;
            this.applet = applet;
        }

        @Override
        public AudioClip getAudioClip(URL url) {
            return null;
        }

        @Override
        public Image getImage(URL url) {
            return Toolkit.getDefaultToolkit().createImage(url);
        }

        @Override
        public Applet getApplet(String name) {
            return null;
        }

        @Override
        public Enumeration<Applet> getApplets() {
            return new Enumeration<Applet>() {
                private boolean seen = false;

                @Override
                public boolean hasMoreElements() {
                    return !seen;
                }

                @Override
                public Applet nextElement() {
                    seen = true;
                    return applet;
                }
            };
        }

        @Override
        public void showDocument(URL url) {
            System.out.println("[showDocument] " + url);
        }

        @Override
        public void showDocument(URL url, String target) {
            System.out.println("[showDocument:" + target + "] " + url);
        }

        @Override
        public void showStatus(String statusText) {
            if (status != null) {
                status.setText("Status: " + statusText);
            }
            System.out.println("[status] " + statusText);
        }

        @Override
        public void setStream(String key, InputStream stream) {
            // not used
        }

        @Override
        public InputStream getStream(String key) {
            return null;
        }

        @Override
        public Iterator<String> getStreamKeys() {
            return Collections.emptyIterator();
        }
    }
}
