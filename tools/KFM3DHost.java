import java.applet.Applet;
import java.applet.AppletContext;
import java.applet.AppletStub;
import java.applet.AudioClip;
import java.awt.BorderLayout;
import java.awt.EventQueue;
import java.awt.Frame;
import java.awt.Image;
import java.awt.Label;
import java.awt.Panel;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Standalone host for the legacy KFM3D applet with basic AppletStub/AppletContext emulation.
 *
 * Run from the repository root with:
 *   java -cp jfroehl/zip/nnwj-applet.zip tools/KFM3DHost.java
 */
public final class KFM3DHost {
    private static final int APPLET_WIDTH = 400;
    private static final int APPLET_HEIGHT = 500;
    private static final AtomicBoolean THREAD_STOP_WARNING_EMITTED = new AtomicBoolean(false);
    private static final AtomicReference<HostContext> HOST_CONTEXT = new AtomicReference<>();

    private KFM3DHost() {
    }

    public static void main(String[] args) throws Exception {
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
                launch(codeBase, docBase, params);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        });
    }

    private static void launch(URL codeBase, URL docBase, Map<String, String> params) throws Exception {
        final Applet applet = (Applet) Class.forName("KFM3D").getDeclaredConstructor().newInstance();
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

        frame.setVisible(true);
        applet.init();
        applet.validate();
        Toolkit.getDefaultToolkit().sync();
        applet.start();
        context.showStatus("running");
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
