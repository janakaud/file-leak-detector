package java.io;

/**
 * A shameless copy of {@link FilterInputStream} from JDK 8, with the leak-dumping logic in place
 */
public class FilterInputStream extends InputStream {

    private LeakDumper dumper = new LeakDumper();
    protected volatile InputStream in;

    protected FilterInputStream(InputStream in) {
        if (LeakDumper.FILTERS) {
            dumper.start("--filter--");
        }
        this.in = in;
    }

    public int read() throws IOException {
        return in.read();
    }

    public int read(byte b[]) throws IOException {
        return read(b, 0, b.length);
    }

    public int read(byte b[], int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    public long skip(long n) throws IOException {
        return in.skip(n);
    }

    public int available() throws IOException {
        return in.available();
    }

    public void close() throws IOException {
        dumper.cancel();
        in.close();
    }

    public synchronized void mark(int readlimit) {
        in.mark(readlimit);
    }

    public synchronized void reset() throws IOException {
        in.reset();
    }

    public boolean markSupported() {
        return in.markSupported();
    }
}