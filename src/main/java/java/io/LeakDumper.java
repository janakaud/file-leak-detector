package java.io;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class LeakDumper {

    private static final AtomicInteger id = new AtomicInteger();

    private static final int GRACE;
    private static final int MIN_LIFE;
    static final boolean FILES;
    static final boolean FILTERS;

    private static final Supplier<PrintStream> DUMP_STREAM;

    static {
        // wait time before tracking starts - skips Java-level classloading, may need to be increased in case of Spring etc
        String grace = System.getenv("INITIAL_GRACE");
        GRACE = grace != null ? Integer.parseInt(grace) : 2000;

        // minimum life (maximum time allowed) for a stream to remain open, before it is considered as a leak
        String minLife = System.getenv("MIN_LIFE");
        MIN_LIFE = minLife != null ? Integer.parseInt(minLife) : 10000;

        // set to false if FileInputStreams should not be tracked
        String files = System.getenv("DETECT_FILES");
        FILES = files == null || Boolean.parseBoolean(files);

        // set to false if FilterInputStreams should not be tracked
        String filters = System.getenv("DETECT_FILTERS");
        FILTERS = filters == null || Boolean.parseBoolean(filters);

        // where to dump the diagnostic info - stdout or stderr
        // stdout and stderr may not be initialized at the time this class loads, so we use a real-time resolver
        String stream = System.getenv("DUMP_STREAM");
        DUMP_STREAM = () -> "stdout".equals(stream) || "out".equals(stream) ? System.out : System.err;

        // print a diagnostic line containing check parameters
        Timer logger = new Timer();
        logger.schedule(new TimerTask() {
            @Override
            public void run() {
                List<String> opts = new ArrayList<>();
                if (FILES) {
                    opts.add("FILES");
                }
                if (FILTERS) {
                    opts.add("FILTERS");
                }
                DUMP_STREAM.get().println("LeakDumper [" + opts.stream().collect(Collectors.joining(",")) + "]: " +
                        GRACE + "ms initial grace, " + MIN_LIFE + "ms min-life");
                logger.cancel();
            }
        }, 1000);
    }

    private int myId;
    private long created;
    private StackTraceElement[] stack;
    private Timer timer;

    public void start(String path) {
        // if we're within the initial grace period, don't track this stream
        if (ManagementFactory.getRuntimeMXBean().getUptime() < GRACE) {
            return;
        }

        // stream creation metadata
        myId = id.incrementAndGet();
        created = System.currentTimeMillis();
        stack = Thread.currentThread().getStackTrace();

        // unless cancelled (on stream close), this will dump the allocation metadata after MIN_LIFE expires
        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                long duration = System.currentTimeMillis() - created;
                if (duration > MIN_LIFE) {
                    String delim = "\n  ";
                    DUMP_STREAM.get().println(Arrays.stream(stack)
                            // skip LeakDumper-internal stacktrace elements
                            .skip(2).map(Object::toString)
                            .collect(Collectors.joining(delim,
                                    "\nid: " + myId + " created: " + created + "\n" + path + "\n" + delim, "\n")));
                    timer.cancel();
                }
            }
        }, 0, 2000);
    }

    public void cancel() {
        if (timer != null) {
            timer.cancel();
        }
    }
}
