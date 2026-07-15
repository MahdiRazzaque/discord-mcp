package dev.saseq.services;

import dev.saseq.models.DiscordEmbed;
import dev.saseq.models.MessageResult;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageHistory;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.MessageEditAction;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageServiceContractTest {
    @TempDir
    Path temp;

    private JDA jda;
    private TextChannel channel;
    private MessageService service;

    @BeforeEach
    void setUp() throws Exception {
        Path blobs = Files.createDirectories(temp.resolve("blobs"));
        jda = mock(JDA.class);
        channel = mock(TextChannel.class);
        when(jda.getTextChannelById("123")).thenReturn(channel);
        service = new MessageService(jda, blobs.toString());
    }

    @Test
    void sendsContentEmbedsAndFilesTogetherAndReturnsIds() throws Exception {
        Path file = Files.writeString(Files.createDirectories(temp.resolve("blobs/job")).resolve("receipt.png"), "image");
        Message sent = mock(Message.class);
        MessageCreateAction action = mock(MessageCreateAction.class);
        when(channel.sendMessage(any(MessageCreateData.class))).thenReturn(action);
        when(action.complete()).thenReturn(sent);
        when(sent.getId()).thenReturn("456");
        when(sent.getChannelId()).thenReturn("123");

        MessageResult result = service.sendMessage(
                "123",
                "Check this receipt",
                List.of(new DiscordEmbed("Receipt", "Body", null, null, null, null, null, null, null, null)),
                List.of(file.toString())
        );

        ArgumentCaptor<MessageCreateData> payload = ArgumentCaptor.forClass(MessageCreateData.class);
        verify(channel).sendMessage(payload.capture());
        assertThat(payload.getValue().getContent()).isEqualTo("Check this receipt");
        assertThat(payload.getValue().getEmbeds()).hasSize(1);
        assertThat(payload.getValue().getAttachments()).hasSize(1);
        assertThat(result).isEqualTo(new MessageResult("456", "123"));
    }

    @Test
    void validatesEveryFileBeforeSendingAnything() throws Exception {
        Path valid = Files.writeString(temp.resolve("blobs/valid.png"), "image");
        Path outside = Files.writeString(temp.resolve("outside.txt"), "secret");

        assertThatThrownBy(() -> service.sendMessage("123", "must not send", null, List.of(valid.toString(), outside.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
        verify(channel, never()).sendMessage(any(MessageCreateData.class));
    }

    @Test
    void rejectsAnEmptyPayloadBeforeSending() {
        assertThatThrownBy(() -> service.sendMessage("123", null, List.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one");
        verify(channel, never()).sendMessage(any(MessageCreateData.class));
    }

    @Test
    void generatedEmbedSchemasContainNoUnresolvedRootReferences() {
        ToolCallback[] callbacks = MethodToolCallbackProvider.builder().toolObjects(service).build().getToolCallbacks();
        for (ToolCallback callback : callbacks) {
            if (callback.getToolDefinition().name().equals("send_message")
                    || callback.getToolDefinition().name().equals("edit_message")) {
                assertThat(callback.getToolDefinition().inputSchema()).doesNotContain("#/$defs/");
            }
        }
    }

    @Test
    void editsContentAndEmbedsInPlaceAndReturnsIds() {
        Message original = mock(Message.class);
        Message edited = mock(Message.class);
        @SuppressWarnings("unchecked") RestAction<Message> retrieve = mock(RestAction.class);
        MessageEditAction editAction = mock(MessageEditAction.class);
        when(channel.retrieveMessageById("456")).thenReturn(retrieve);
        when(retrieve.complete()).thenReturn(original);
        when(original.editMessage(any(MessageEditData.class))).thenReturn(editAction);
        when(editAction.complete()).thenReturn(edited);
        when(edited.getId()).thenReturn("456");
        when(edited.getChannelId()).thenReturn("123");

        MessageResult result = service.editMessage(
                "123", "456", "Updated",
                List.of(new DiscordEmbed("Updated title", null, null, null, null, null, null, null, null, null))
        );

        ArgumentCaptor<MessageEditData> payload = ArgumentCaptor.forClass(MessageEditData.class);
        verify(original).editMessage(payload.capture());
        assertThat(payload.getValue().getContent()).isEqualTo("Updated");
        assertThat(payload.getValue().getEmbeds()).hasSize(1);
        assertThat(result).isEqualTo(new MessageResult("456", "123"));
    }

    @Test
    void structuredReadsExposeAuthorBotPlainContentAndSharedEmbeds() {
        Message message = mock(Message.class);
        User author = mock(User.class);
        MessageHistory history = mock(MessageHistory.class);
        MessageHistory.MessageRetrieveAction historyAction = mock(MessageHistory.MessageRetrieveAction.class);
        when(channel.getHistoryAfter("100", 25)).thenReturn(historyAction);
        when(historyAction.complete()).thenReturn(history);
        when(history.getRetrievedHistory()).thenReturn(List.of(message));
        when(message.getId()).thenReturn("456");
        when(message.getAuthor()).thenReturn(author);
        when(author.getId()).thenReturn("789");
        when(author.isBot()).thenReturn(true);
        when(message.getContentRaw()).thenReturn("plain body");
        when(message.getEmbeds()).thenReturn(List.of(new DiscordEmbed("Receipt", "Body", null, 0x123456, null, null, null, null, null, null).toMessageEmbed()));
        when(message.getAttachments()).thenReturn(List.of());
        when(message.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-07-15T20:15:30Z"));

        Object result = service.readMessages("123", "25", null, "100", null, true);

        assertThat(result).asList().singleElement().satisfies(item -> {
            assertThat(item).hasFieldOrPropertyWithValue("id", "456");
            assertThat(item).hasFieldOrPropertyWithValue("authorId", "789");
            assertThat(item).hasFieldOrPropertyWithValue("authorBot", true);
            assertThat(item).hasFieldOrPropertyWithValue("content", "plain body");
            assertThat(item).extracting("embeds").asList().hasSize(1);
        });
    }

    @Test
    void defaultReadsRemainByteForByteCompatibleWithUpstreamFormatter() {
        Message message = mock(Message.class);
        User author = mock(User.class);
        MessageHistory history = mock(MessageHistory.class);
        MessageHistory.MessageRetrieveAction historyAction = mock(MessageHistory.MessageRetrieveAction.class);
        when(channel.getHistoryAfter("100", 1)).thenReturn(historyAction);
        when(historyAction.complete()).thenReturn(history);
        when(history.getRetrievedHistory()).thenReturn(List.of(message));
        when(message.getId()).thenReturn("456");
        when(message.getAuthor()).thenReturn(author);
        when(author.getName()).thenReturn("bot");
        when(message.getContentDisplay()).thenReturn("legacy text");
        when(message.getAttachments()).thenReturn(List.of());
        when(message.getTimeCreated()).thenReturn(OffsetDateTime.parse("2026-07-15T20:15:30Z"));

        Object result = service.readMessages("123", "1", null, "100", null, false);

        assertThat(result).isEqualTo("**Retrieved 1 messages:** \n- (ID: 456) **[bot]** `2026-07-15T20:15:30Z`: ```legacy text```");
    }
}
