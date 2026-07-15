package dev.saseq.models;

import net.dv8tion.jda.api.entities.MessageEmbed;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordEmbedTest {

    @Test
    void mapsTheSharedEmbedSchemaToJdaAndBack() {
        DiscordEmbed input = new DiscordEmbed(
                "Receipt status",
                "Awaiting confirmation",
                "https://example.test/receipt",
                "#12ab34",
                OffsetDateTime.parse("2026-07-15T20:15:30Z"),
                new DiscordEmbed.Author("Hermes", "https://example.test/author", "https://example.test/author.png"),
                new DiscordEmbed.Footer("Footer text", "https://example.test/footer.png"),
                new DiscordEmbed.Thumbnail("https://example.test/thumb.png"),
                new DiscordEmbed.Image("https://example.test/image.png"),
                List.of(new DiscordEmbed.Field("Amount", "£12.34", true))
        );

        MessageEmbed jda = input.toMessageEmbed();
        DiscordEmbed output = DiscordEmbed.fromMessageEmbed(jda);

        assertThat(output.title()).isEqualTo(input.title());
        assertThat(output.description()).isEqualTo(input.description());
        assertThat(output.url()).isEqualTo(input.url());
        assertThat(output.color()).isEqualTo(0x12ab34);
        assertThat(output.timestamp()).isEqualTo(input.timestamp());
        assertThat(output.author()).isEqualTo(input.author());
        assertThat(output.footer()).isEqualTo(input.footer());
        assertThat(output.thumbnail()).isEqualTo(input.thumbnail());
        assertThat(output.image()).isEqualTo(input.image());
        assertThat(output.fields()).containsExactlyElementsOf(input.fields());
    }

    @Test
    void acceptsRgbArrayAndNamedColourInputs() {
        assertThat(new DiscordEmbed(null, "rgb", null, List.of(1, 2, 3), null, null, null, null, null, null)
                .toMessageEmbed().getColorRaw()).isEqualTo(0x010203);
        assertThat(new DiscordEmbed(null, "named", null, "red", null, null, null, null, null, null)
                .toMessageEmbed().getColorRaw()).isEqualTo(0xff0000);
    }
}
