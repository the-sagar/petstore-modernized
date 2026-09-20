package com.mdb.petstore.orderprocessing.order.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Workflow identifiers only; recipient and content come from the authoritative order. */
public record NotificationRequested(@NotBlank String notificationId, @NotBlank String orderId,
        @NotNull NotificationType notificationType, String shipmentEventId) {}
