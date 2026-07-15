package dev.saseq.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentPathPolicyTest {

    @TempDir
    Path temp;

    @Test
    void acceptsCanonicalFilesUnderTheBlobsRoot() throws Exception {
        Path root = Files.createDirectories(temp.resolve("blobs"));
        Path file = Files.writeString(Files.createDirectories(root.resolve("job")).resolve("receipt.png"), "image");

        try (AttachmentPathPolicy.ApprovedAttachment attachment = new AttachmentPathPolicy(root).open(file.toString())) {
            assertThat(attachment.filename()).isEqualTo("receipt.png");
            assertThat(new String(attachment.stream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("image");
        }
    }

    @Test
    void rejectsRelativeOutsideMissingAndDirectoryPaths() throws Exception {
        Path root = Files.createDirectories(temp.resolve("blobs"));
        Path outside = Files.writeString(temp.resolve("outside.txt"), "secret");
        AttachmentPathPolicy policy = new AttachmentPathPolicy(root);

        assertThatThrownBy(() -> policy.open("relative.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("absolute");
        assertThatThrownBy(() -> policy.open(outside.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        assertThatThrownBy(() -> policy.open(root.resolve("missing.png").toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readable file");
        assertThatThrownBy(() -> policy.open(root.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readable file");
    }

    @Test
    void rejectsSymlinksThatEscapeTheBlobsRoot() throws Exception {
        Path root = Files.createDirectories(temp.resolve("blobs"));
        Path outside = Files.writeString(temp.resolve("outside.txt"), "secret");
        Path link = Files.createSymbolicLink(root.resolve("escape.txt"), outside);

        assertThatThrownBy(() -> new AttachmentPathPolicy(root).open(link.toString()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readable file");
    }

    @Test
    void openedHandleCannotBeRedirectedByReplacingPathWithSymlink() throws Exception {
        Path root = Files.createDirectories(temp.resolve("blobs"));
        Path file = Files.writeString(root.resolve("receipt.txt"), "approved");
        Path outside = Files.writeString(temp.resolve("outside.txt"), "secret");

        try (AttachmentPathPolicy.ApprovedAttachment attachment = new AttachmentPathPolicy(root).open(file.toString())) {
            Files.move(file, root.resolve("original.txt"), StandardCopyOption.ATOMIC_MOVE);
            Files.createSymbolicLink(file, outside);

            assertThat(new String(attachment.stream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("approved");
        }
    }

    @Test
    void pinnedRootCannotBeRedirectedAfterPolicyConstruction() throws Exception {
        Path root = Files.createDirectories(temp.resolve("blobs"));
        Files.writeString(root.resolve("receipt.txt"), "approved");
        Path outside = Files.createDirectories(temp.resolve("outside"));
        Files.writeString(outside.resolve("receipt.txt"), "secret");

        try (AttachmentPathPolicy policy = new AttachmentPathPolicy(root)) {
            Files.move(root, temp.resolve("original-blobs"), StandardCopyOption.ATOMIC_MOVE);
            Files.createSymbolicLink(root, outside);

            try (AttachmentPathPolicy.ApprovedAttachment attachment = policy.open(root.resolve("receipt.txt").toString())) {
                assertThat(new String(attachment.stream().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("approved");
            }
        }
    }
}
