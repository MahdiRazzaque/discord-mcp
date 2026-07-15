package dev.saseq.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The single Discord embed schema shared by send, edit, and structured reads.
 * Discord, rather than this transport, remains responsible for embed limit validation.
 */
public record DiscordEmbed(
        @JsonProperty(required = false) String title,
        @JsonProperty(required = false) String description,
        @JsonProperty(required = false) String url,
        @JsonProperty(required = false) Object color,
        @JsonProperty(required = false) OffsetDateTime timestamp,
        @JsonProperty(required = false) Author author,
        @JsonProperty(required = false) Footer footer,
        @JsonProperty(required = false) Thumbnail thumbnail,
        @JsonProperty(required = false) Image image,
        @JsonProperty(required = false) List<Field> fields
) {
    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("black", 0x000000),
            Map.entry("white", 0xffffff),
            Map.entry("red", 0xff0000),
            Map.entry("green", 0x00ff00),
            Map.entry("blue", 0x0000ff),
            Map.entry("yellow", 0xffff00),
            Map.entry("orange", 0xffa500),
            Map.entry("purple", 0x800080),
            Map.entry("pink", 0xffc0cb),
            Map.entry("grey", 0x808080),
            Map.entry("gray", 0x808080),
            Map.entry("cyan", 0x00ffff),
            Map.entry("magenta", 0xff00ff)
    );

    public record Author(String name,
                         @JsonProperty(required = false) String url,
                         @JsonProperty(required = false) String icon_url) { }
    public record Footer(String text,
                         @JsonProperty(required = false) String icon_url) { }
    public record Thumbnail(String url) { }
    public record Image(String url) { }
    public record Field(String name, String value,
                        @JsonProperty(required = false) Boolean inline) { }

    public MessageEmbed toMessageEmbed() {
        EmbedBuilder builder = new EmbedBuilder();
        if (title != null || url != null) {
            builder.setTitle(title, url);
        }
        if (description != null) {
            builder.setDescription(description);
        }
        if (color != null) {
            builder.setColor(parseColor(color));
        }
        if (timestamp != null) {
            builder.setTimestamp(timestamp);
        }
        if (author != null) {
            builder.setAuthor(author.name(), author.url(), author.icon_url());
        }
        if (footer != null) {
            builder.setFooter(footer.text(), footer.icon_url());
        }
        if (thumbnail != null) {
            builder.setThumbnail(thumbnail.url());
        }
        if (image != null) {
            builder.setImage(image.url());
        }
        if (fields != null) {
            for (Field field : fields) {
                builder.addField(field.name(), field.value(), Boolean.TRUE.equals(field.inline()));
            }
        }
        return builder.build();
    }

    public static DiscordEmbed fromMessageEmbed(MessageEmbed embed) {
        MessageEmbed.AuthorInfo authorInfo = embed.getAuthor();
        MessageEmbed.Footer footerInfo = embed.getFooter();
        MessageEmbed.Thumbnail thumbnailInfo = embed.getThumbnail();
        MessageEmbed.ImageInfo imageInfo = embed.getImage();
        List<Field> fieldData = embed.getFields().stream()
                .map(field -> new Field(field.getName(), field.getValue(), field.isInline()))
                .toList();

        return new DiscordEmbed(
                embed.getTitle(),
                embed.getDescription(),
                embed.getUrl(),
                embed.getColor() == null ? null : embed.getColorRaw(),
                embed.getTimestamp(),
                authorInfo == null ? null : new Author(authorInfo.getName(), authorInfo.getUrl(), authorInfo.getIconUrl()),
                footerInfo == null ? null : new Footer(footerInfo.getText(), footerInfo.getIconUrl()),
                thumbnailInfo == null ? null : new Thumbnail(thumbnailInfo.getUrl()),
                imageInfo == null ? null : new Image(imageInfo.getUrl()),
                fieldData
        );
    }

    private static int parseColor(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof List<?> rgb) {
            if (rgb.size() != 3 || rgb.stream().anyMatch(component -> !(component instanceof Number))) {
                throw new IllegalArgumentException("Embed color RGB array must contain exactly three integers");
            }
            return new Color(
                    ((Number) rgb.get(0)).intValue(),
                    ((Number) rgb.get(1)).intValue(),
                    ((Number) rgb.get(2)).intValue()
            ).getRGB() & 0xffffff;
        }
        if (value instanceof String text) {
            String normalized = text.trim().toLowerCase(Locale.ROOT);
            Integer named = NAMED_COLORS.get(normalized);
            if (named != null) {
                return named;
            }
            try {
                if (normalized.startsWith("#")) {
                    return Integer.parseInt(normalized.substring(1), 16);
                }
                if (normalized.startsWith("0x")) {
                    return Integer.parseInt(normalized.substring(2), 16);
                }
                return Integer.parseInt(normalized);
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Unsupported embed color: " + text, ex);
            }
        }
        throw new IllegalArgumentException("Embed color must be an integer, hex string, RGB array, or color name");
    }
}
