package com.gymos.communications.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.automations.infrastructure.OutboundMessageRepository;
import com.gymos.automations.infrastructure.OutboundMessageRepository.OutboundMessageRecord;
import com.gymos.communications.api.OutboundChannelSender;
import com.gymos.communications.api.OutboundChannelSender.SendResult;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboundMessageDispatcherTests {
    @Test
    void marksAcceptedWhenChannelProviderAcceptsMessage() {
        OutboundMessageRepository repository = mock(OutboundMessageRepository.class);
        OutboundChannelSender sender = mock(OutboundChannelSender.class);
        OutboundMessageRecord message = message("EMAIL", 0);
        when(sender.channel()).thenReturn("EMAIL");
        when(sender.available()).thenReturn(true);
        when(sender.send(any())).thenReturn(SendResult.accepted("mail-provider", "external-1"));
        when(repository.due(10)).thenReturn(List.of(message));
        when(repository.claim(message.id())).thenReturn(true);

        new OutboundMessageDispatcher(repository, List.of(sender)).dispatchDue(10);

        verify(repository).markAccepted(message.id(), "mail-provider", "external-1");
    }

    @Test
    void schedulesFailureWhenChannelHasNoConfiguredProvider() {
        OutboundMessageRepository repository = mock(OutboundMessageRepository.class);
        OutboundMessageRecord message = message("WHATSAPP", 2);
        when(repository.due(10)).thenReturn(List.of(message));
        when(repository.claim(message.id())).thenReturn(true);

        new OutboundMessageDispatcher(repository, List.of()).dispatchDue(10);

        verify(repository).markFailed(
                eq(message.id()),
                contains("WHATSAPP"),
                eq(true),
                eq(3)
        );
    }

    @Test
    void doesNotSendWhenAnotherWorkerAlreadyClaimedMessage() {
        OutboundMessageRepository repository = mock(OutboundMessageRepository.class);
        OutboundChannelSender sender = mock(OutboundChannelSender.class);
        OutboundMessageRecord message = message("EMAIL", 0);
        when(sender.channel()).thenReturn("EMAIL");
        when(repository.due(10)).thenReturn(List.of(message));
        when(repository.claim(message.id())).thenReturn(false);

        new OutboundMessageDispatcher(repository, List.of(sender)).dispatchDue(10);

        verify(repository).claim(message.id());
    }

    private OutboundMessageRecord message(String channel, int attemptCount) {
        return new OutboundMessageRecord(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                channel, channel.equals("EMAIL") ? "member@example.com" : "+524921234567",
                "Subject", "Body", "idem-key", attemptCount
        );
    }
}
