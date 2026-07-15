package com.gymos.clients.application.result;

import com.gymos.clients.infrastructure.ClientRepository.ClientDetail;
import com.gymos.clients.infrastructure.ClientRepository.ClientTimelineEntry;
import java.util.List;

public record ClientDetailResult(ClientDetail client, List<ClientTimelineEntry> timeline) {
}
