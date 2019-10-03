package com.tuna;

import org.junit.*;

import java.io.*;

public class FileLeakTest {

    private static File tempFile;
    private static ByteArrayOutputStream err;

    @BeforeClass
    public static void init() throws IOException {
        tempFile = File.createTempFile("prefix", "suffix");
    }

    @AfterClass
    public static void destroy() {
        if (tempFile != null && !tempFile.delete()) {
            tempFile.deleteOnExit();
        }
    }

    @Before
    public void setUp() {
        err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err));
    }

    @Test
    public void testLeak() throws IOException, InterruptedException {
        // skip initial grace
        Thread.sleep(2000);

        // we close the stream as a best practice - still, it would have been open for more than the max allowed period
        try (FileInputStream in = new FileInputStream(tempFile)) {
            Thread.sleep(10100);
        }

        // check the captured stderr, and verify our 'leak' is in there
        System.out.println(err.toString());
        Assert.assertTrue(err.toString().contains(" " + this.getClass().getCanonicalName() + ".testLeak"));
    }
}