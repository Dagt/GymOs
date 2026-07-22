package com.gymos.sales.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.gymos.sales.api.events.PaymentCompletedV1;
import com.gymos.sales.infrastructure.SalePaymentCompletionRepository;
import com.gymos.sales.infrastructure.SalePaymentCompletionRepository.SaleContext;
import com.gymos.sales.infrastructure.SalesRepository;
import com.gymos.sales.infrastructure.SalesRepository.SaleHeader;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class SalePaymentCompletionServiceTests {
	@Test
	void recalculatesPaidSaleAndPublishesCompletionOnce() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalePaymentCompletionRepository repository = mock(SalePaymentCompletionRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		SalePaymentCompletionService service = new SalePaymentCompletionService(salesRepository, repository, eventPublisher);
		UUID organizationId = UUID.randomUUID();
		UUID branchId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		UUID clientId = UUID.randomUUID();
		UUID actorUserId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now();
		SaleHeader sale = new SaleHeader(
				saleId,
				"S-TEST",
				branchId,
				clientId,
				"Cliente",
				"PENDING_PAYMENT",
				5_000,
				0,
				5_000,
				0,
				5_000,
				"MXN",
				null,
				now,
				now,
				now
		);
		when(salesRepository.saleHeader(organizationId, saleId)).thenReturn(Optional.of(sale));
		when(repository.completedPaymentTotal(organizationId, saleId)).thenReturn(5_000L);
		when(repository.markPaymentCompleted(org.mockito.ArgumentMatchers.eq(organizationId), org.mockito.ArgumentMatchers.eq(saleId), any(OffsetDateTime.class))).thenReturn(true);
		when(repository.context(organizationId, saleId)).thenReturn(new SaleContext("POS", null, null));
		when(repository.items(organizationId, saleId)).thenReturn(List.of());

		service.recalculate(organizationId, saleId, actorUserId);

		verify(repository).updateFinancials(
				org.mockito.ArgumentMatchers.eq(organizationId),
				org.mockito.ArgumentMatchers.eq(saleId),
				org.mockito.ArgumentMatchers.eq("PAID"),
				org.mockito.ArgumentMatchers.eq(5_000L),
				org.mockito.ArgumentMatchers.eq(0L),
				org.mockito.ArgumentMatchers.eq(actorUserId),
				any(OffsetDateTime.class)
		);
		ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
		verify(eventPublisher).publishEvent(eventCaptor.capture());
		assertThat(eventCaptor.getValue()).isInstanceOf(PaymentCompletedV1.class);
	}

	@Test
	void alreadyPublishedCompletionDoesNotPublishDuplicateEvent() {
		SalesRepository salesRepository = mock(SalesRepository.class);
		SalePaymentCompletionRepository repository = mock(SalePaymentCompletionRepository.class);
		ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
		SalePaymentCompletionService service = new SalePaymentCompletionService(salesRepository, repository, eventPublisher);
		UUID organizationId = UUID.randomUUID();
		UUID saleId = UUID.randomUUID();
		when(repository.markPaymentCompleted(org.mockito.ArgumentMatchers.eq(organizationId), org.mockito.ArgumentMatchers.eq(saleId), any(OffsetDateTime.class))).thenReturn(false);

		service.publishIfNeeded(organizationId, saleId);

		verify(eventPublisher, never()).publishEvent(any());
		verify(salesRepository, never()).saleHeader(any(), any());
		verify(repository, never()).context(any(), any());
		verify(repository, never()).items(any(), any());
	}
}
