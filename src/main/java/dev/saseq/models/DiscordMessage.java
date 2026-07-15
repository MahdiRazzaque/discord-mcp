package dev.saseq.models;

import java.time.OffsetDateTime;
import java.util.List;

public record DiscordMessage(
        String id,
        String authorId,
        boolean authorBot,
        String content,
        List<DiscordEmbed> embeds,
        List<Attachment> attachments,
        OffsetDateTime createdAt
) {
    public record Attachment(
            String id,
            String filename,
            int size,
            String contentType,
            String url,
            String proxyUrl
    ) { }
}
