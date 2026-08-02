package com.gymos.communications.application;

import com.gymos.automations.infrastructure.OutboundMessageRepository;
import com.gymos.automations.infrastructure.OutboundMessageRepository.OutboundMessageRecord;
import com.gymos.communications.api.OutboundChannelSender;
import com.gymos.communications.api.OutboundChannelSender.SendCommand;
import com.gymos.communications.api.OutboundChannelSender.SendResult;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class OutboundMessageDispatcher {
    private final OutboundMessageRepository repository;
    private final Map<String, OutboundChannelSender> senders;

    public OutboundMessageDispatcher(
            OutboundMessageRepository repository,
            List<OutboundChannelSender> senders
    ) {
        this.repository = repository;
        this.senders = senders.stream().collect(Collectors.toUnmodifiableMap(
                sender -> sender.channel().toUpperCase(Locale.ROOT),
                Function.identity(),
                (first, second) -> {
                    throw new IllegalStateException("Multiple outbound senders configured for channel " + first.channel());
                }
        ));
    }

    public int dispatchDue(int limit) {
        int processed = 0;
        for (OutboundMessageRecord message : repository.due(limit)) {
            if (!repository.claim(message.id())) continue;
            processed++;
            dispatch(message);
        }
        return processed;
    }

    private void dispatch(OutboundMessageRecord message) {
        OutboundChannelSender sender = senders.get(message.channel().toUpperCase(Locale.ROOT));
        int currentAttempt = message.attemptCount() + 1;
        if (sender == null || !sender.available()) {
            repository.markFailed(
                    message.id(),
                    "No hay un adaptador disponible para el canal " + message.channel(),
                    true,
                    currentAttempt
            );
            return;
        }

        try {
            SendResult result = sender.send(new SendCommand(
                    message.id(),
                    message.organizationId(),
                    message.branchId(),
                    message.clientId(),
                    message.recipient(),
                    message.subject(),
                    message.body(),
                    message.idempotencyKey()
            ));
            switch (result.state()) {
                case ACCEPTED -> repository.markAccepted(message.id(), result.provider(), result.providerMessageId());
                case DELIVERED -> repository.markDelivered(message.id(), result.provider(), result.providerMessageId());
                case FAILED -> repository.markFailed(
                        message.id(),
                        errorText(result),
                        result.retryable(),
                        currentAttempt
                );
            }
        } catch (RuntimeException exception) {
            repository.markFailed(
                    message.id(),
                    exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage(),
                    true,
                    currentAttempt
            );
        }
    }

    private String errorText(SendResult result) {
        String code = result.errorCode() == null ? "CHANNEL_SEND_FAILED" : result.errorCode();
        String message = result.errorMessage() == null ? "No fue posible enviar la comunicacion." : result.errorMessage();
        return code + ": " + message;
    }
}
