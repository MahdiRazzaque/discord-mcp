package dev.saseq.security;

import com.sun.jna.Library;
import com.sun.jna.Native;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Opens attachments with Linux openat(2), refusing symlinks at every path component. */
public final class AttachmentPathPolicy implements AutoCloseable {
    private static final int O_RDONLY = 0;
    private static final int O_DIRECTORY = 0040000;
    private static final int O_NOFOLLOW = 0100000;
    private static final int O_NONBLOCK = 04000;
    private static final int O_CLOEXEC = 02000000;
    private static final LibC LIBC = Native.load("c", LibC.class);

    private final Path canonicalRoot;
    private final int rootFd;

    public AttachmentPathPolicy(Path root) {
        try {
            this.canonicalRoot = root.toRealPath();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Attachment blobs root is not accessible: " + root, ex);
        }
        int openedRoot = LIBC.open(canonicalRoot.toString(),
                O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
        if (openedRoot < 0) {
            throw new IllegalArgumentException("Attachment blobs root cannot be opened securely");
        }
        try {
            Path descriptorTarget = Path.of("/proc/self/fd", Integer.toString(openedRoot)).toRealPath();
            if (!descriptorTarget.equals(canonicalRoot)) {
                throw new IllegalArgumentException("Attachment blobs root changed while it was being opened");
            }
            this.rootFd = openedRoot;
        } catch (IOException | RuntimeException ex) {
            LIBC.close(openedRoot);
            throw new IllegalArgumentException("Attachment blobs root cannot be pinned securely", ex);
        }
    }

    public ApprovedAttachment open(String requestedPath) {
        if (requestedPath == null || requestedPath.isBlank()) {
            throw new IllegalArgumentException("Attachment path cannot be blank");
        }

        Path candidate = Path.of(requestedPath);
        if (!candidate.isAbsolute()) {
            throw new IllegalArgumentException("Attachment path must be absolute");
        }
        Path normalized = candidate.normalize();
        if (!normalized.startsWith(canonicalRoot)) {
            throw new IllegalArgumentException("Attachment path resolves outside the configured blobs root");
        }
        Path relative = canonicalRoot.relativize(normalized);
        if (relative.getNameCount() < 1) {
            throw new IllegalArgumentException("Attachment path must name an existing readable file");
        }

        int directoryFd = LIBC.dup(rootFd);
        if (directoryFd < 0) {
            throw unreadable();
        }
        int fileFd = -1;
        try {
            for (int index = 0; index < relative.getNameCount() - 1; index++) {
                int childFd = LIBC.openat(directoryFd, relative.getName(index).toString(),
                        O_RDONLY | O_DIRECTORY | O_NOFOLLOW | O_CLOEXEC);
                if (childFd < 0) {
                    throw unreadable();
                }
                LIBC.close(directoryFd);
                directoryFd = childFd;
            }

            String filename = relative.getFileName().toString();
            fileFd = LIBC.openat(directoryFd, filename,
                    O_RDONLY | O_NOFOLLOW | O_NONBLOCK | O_CLOEXEC);
            if (fileFd < 0) {
                throw unreadable();
            }

            Path openedDescriptor = Path.of("/proc/self/fd", Integer.toString(fileFd));
            if (!Files.isRegularFile(openedDescriptor)) {
                throw unreadable();
            }
            InputStream stream = Files.newInputStream(openedDescriptor);
            LIBC.close(fileFd);
            fileFd = -1;
            return new ApprovedAttachment(filename, stream);
        } catch (IOException | SecurityException ex) {
            throw new IllegalArgumentException("Attachment path must name an existing readable file", ex);
        } finally {
            if (fileFd >= 0) {
                LIBC.close(fileFd);
            }
            LIBC.close(directoryFd);
        }
    }

    private static IllegalArgumentException unreadable() {
        return new IllegalArgumentException(
                "Attachment path must name an existing readable file (errno " + Native.getLastError() + ")");
    }

    private interface LibC extends Library {
        int open(String path, int flags);
        int openat(int directoryFd, String path, int flags);
        int close(int fd);
        int dup(int fd);
    }

    @Override
    public void close() {
        LIBC.close(rootFd);
    }

    public static final class ApprovedAttachment implements AutoCloseable {
        private final String filename;
        private final InputStream stream;

        private ApprovedAttachment(String filename, InputStream stream) {
            this.filename = filename;
            this.stream = stream;
        }

        public String filename() {
            return filename;
        }

        public InputStream stream() {
            return stream;
        }

        @Override
        public void close() throws IOException {
            stream.close();
        }
    }
}
