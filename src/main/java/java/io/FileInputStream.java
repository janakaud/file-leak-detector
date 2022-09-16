package java.io;

import java.nio.channels.FileChannel;
import sun.nio.ch.FileChannelImpl;

/**
 * A shameless copy of {@link FileInputStream} from JDK 11.0.1, with the leak-dumping logic in place
 */
public class FileInputStream extends InputStream {

    private final FileDescriptor fd;

    private final String path;

    private volatile FileChannel channel;

    private final Object closeLock = new Object();

    private volatile boolean closed;

    private final Object altFinalizer;

    private final LeakDumper dumper = new LeakDumper();

    public FileInputStream(String name) throws FileNotFoundException {
        this(name != null ? new File(name) : null);
    }

    public FileInputStream(File file) throws FileNotFoundException {
        String name = (file != null ? file.getPath() : null);
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkRead(name);
        }
        if (name == null) {
            throw new NullPointerException();
        }
        if (file.isInvalid()) {
            throw new FileNotFoundException("Invalid file path");
        }
        fd = new FileDescriptor();
        fd.attach(this);
        path = name;
        open(name);
        altFinalizer = getFinalizer(this);
        if (altFinalizer == null) {
            FileCleanable.register(fd);       // open set the fd, register the cleanup
        }

        if (LeakDumper.FILES) {
            dumper.start(path);
        }
    }

    public FileInputStream(FileDescriptor fdObj) {
        SecurityManager security = System.getSecurityManager();
        if (fdObj == null) {
            throw new NullPointerException();
        }
        if (security != null) {
            security.checkRead(fdObj);
        }
        fd = fdObj;
        path = null;
        altFinalizer = null;

        if (LeakDumper.FILES) {
            dumper.start("<FD>");
        }

        fd.attach(this);
    }

    private native void open0(String name) throws FileNotFoundException;

    private void open(String name) throws FileNotFoundException {
        open0(name);
    }

    public int read() throws IOException {
        return read0();
    }

    private native int read0() throws IOException;

    private native int readBytes(byte b[], int off, int len) throws IOException;

    public int read(byte b[]) throws IOException {
        return readBytes(b, 0, b.length);
    }

    public int read(byte b[], int off, int len) throws IOException {
        return readBytes(b, off, len);
    }

    public long skip(long n) throws IOException {
        return skip0(n);
    }

    private native long skip0(long n) throws IOException;

    public int available() throws IOException {
        return available0();
    }

    private native int available0() throws IOException;

    public void close() throws IOException {
        if (closed) {
            return;
        }
        dumper.cancel();
        synchronized (closeLock) {
            if (closed) {
                return;
            }
            closed = true;
        }

        FileChannel fc = channel;
        if (fc != null) {
            fc.close();
        }

        fd.closeAll(new Closeable() {
            public void close() throws IOException {
                fd.close();
            }
        });
    }

    public final FileDescriptor getFD() throws IOException {
        if (fd != null) {
            return fd;
        }
        throw new IOException();
    }

    public FileChannel getChannel() {
        FileChannel fc = this.channel;
        if (fc == null) {
            synchronized (this) {
                fc = this.channel;
                if (fc == null) {
                    this.channel = fc = FileChannelImpl.open(fd, path, true,
                            false, false, this);
                    if (closed) {
                        try {
                            fc.close();
                        } catch (IOException ioe) {
                            throw new InternalError(ioe); // should not happen
                        }
                    }
                }
            }
        }
        return fc;
    }

    private static native void initIDs();

    static {
        initIDs();
    }

    @Deprecated
    protected void finalize() throws IOException {
    }

    private static Object getFinalizer(FileInputStream fis) {
        Class<?> clazz = fis.getClass();
        while (clazz != FileInputStream.class) {
            try {
                clazz.getDeclaredMethod("close");
                return new AltFinalizer(fis);
            } catch (NoSuchMethodException nsme) {
                // ignore
            }
            clazz = clazz.getSuperclass();
        }
        return null;
    }

    static class AltFinalizer {
        private final FileInputStream fis;

        AltFinalizer(FileInputStream fis) {
            this.fis = fis;
        }

        @Override
        @SuppressWarnings("deprecation")
        protected final void finalize() {
            try {
                if ((fis.fd != null) && (fis.fd != FileDescriptor.in)) {
                    fis.close();
                }
            } catch (IOException ioe) {
                // ignore
            }
        }
    }
}
