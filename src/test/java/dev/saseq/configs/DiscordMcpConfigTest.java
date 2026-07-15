package dev.saseq.configs;

import net.dv8tion.jda.api.requests.GatewayIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DiscordMcpConfigTest {
    @Test
    void enablesMessageContentIntentRequiredByReadsAndIntake() {
        assertThat(DiscordMcpConfig.gatewayIntents()).contains(GatewayIntent.MESSAGE_CONTENT);
    }
}
