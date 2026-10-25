package readerswriters;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;


public class ReadersWriters {
    private static final int READER_COUNT = 5;
    private static final int WRITER_COUNT = 3;
    private static final int OPERATIONS_PER_THREAD = 3;

    private static final Semaphore roomEmpty = new Semaphore(1, true);

    private static final ReentrantLock readersMutex = new ReentrantLock(true);

    private static int readerCount = 0;
    private static int sharedValue = 0;

    private static void pauseBriefly() {
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static class Reader implements Runnable {
        private final int id;

        Reader(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                readersMutex.lock();
                try {
                    readerCount++;
                    if (readerCount == 1) {
                        roomEmpty.acquireUninterruptibly();
                    }
                } finally {
                    readersMutex.unlock();
                }

                try {
                    System.out.println("Reader " + id + " reads value " + sharedValue);
                    pauseBriefly();
                } finally {
                    readersMutex.lock();
                    try {
                        readerCount--;
                        if (readerCount == 0) {
                            roomEmpty.release();
                        }
                    } finally {
                        readersMutex.unlock();
                    }
                }
                pauseBriefly();
            }
        }
    }

    private static class Writer implements Runnable {
        private final int id;

        Writer(int id) {
            this.id = id;
        }

        @Override
        public void run() {
            for (int i = 0; i < OPERATIONS_PER_THREAD; i++) {
                roomEmpty.acquireUninterruptibly();
                try {
                    sharedValue++;
                    System.out.println("Writer " + id + " writes value " + sharedValue);
                    pauseBriefly();
                } finally {
                    roomEmpty.release();
                }
                pauseBriefly();
            }
        }
    }

    public static void main(String[] args) {
        Thread[] readers = new Thread[READER_COUNT];
        Thread[] writers = new Thread[WRITER_COUNT];

        for (int i = 0; i < READER_COUNT; i++) {
            readers[i] = new Thread(new Reader(i + 1), "Reader-" + (i + 1));
        }
        for (int i = 0; i < WRITER_COUNT; i++) {
            writers[i] = new Thread(new Writer(i + 1), "Writer-" + (i + 1));
        }

        for (Thread reader : readers) {
            reader.start();
        }
        for (Thread writer : writers) {
            writer.start();
        }

        try {
            for (Thread reader : readers) {
                reader.join();
            }
            for (Thread writer : writers) {
                writer.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Main thread was interrupted before all workers finished.");
            return;
        }

        System.out.println("Final shared value: " + sharedValue);
    }
}
