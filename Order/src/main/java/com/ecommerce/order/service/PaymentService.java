
package com.ecommerce.order.service;

import com.ecommerce.order.model.Payment;
import com.ecommerce.order.model.PaymentStatus;
import com.ecommerce.order.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository paymentRepository;
    @Value("${payment.simulation.fail:false}")
    private boolean simulatePaymentFailure;

    @Transactional
    public Payment createPendingPayment(Long orderId,
                                        java.math.BigDecimal amount) {
        var existing = paymentRepository.findByOrderId(orderId);

        if (existing.isPresent()) {
            Payment payment = existing.get();

            if (payment.getStatus() != PaymentStatus.PENDING) {
                throw new IllegalStateException(
                        "Payment already processed for order: " + orderId);
            }

            if (payment.getAmount().compareTo(amount) != 0) {
                throw new IllegalStateException(
                        "Payment amount mismatch for order: " + orderId);
            }

            return payment;
        }

        Payment payment = new Payment();
        payment.setOrderId(orderId);
        payment.setAmount(amount);
        payment.setStatus(PaymentStatus.PENDING);

        return paymentRepository.save(payment);
    }


    @Transactional
    public Payment processPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment record not found for order: " + orderId));

        if (payment.getStatus() == PaymentStatus.SUCCESS) {
            return payment;
        }

        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new IllegalStateException(
                    "Cannot process a refunded payment again");
        }

        if (payment.getStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        // Simulate a successful payment. No real money is charged.
        // Controlled payment failure simulation for local testing.
        if (simulatePaymentFailure) {
            payment.setStatus(PaymentStatus.FAILED);
            return paymentRepository.save(payment);
        }

        // Simulate a successful payment. No real money is charged.
        payment.setTransactionId(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.SUCCESS);

        return paymentRepository.save(payment);
    }


    @Transactional
    public Payment refundPayment(Long orderId) {
        Payment payment = paymentRepository.findByOrderIdForUpdate(orderId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Payment record not found for order: " + orderId));

        // Idempotency: a previously refunded payment needs no further action.
        if (payment.getStatus() == PaymentStatus.REFUNDED) {
            return payment;
        }

        // No refund is needed if payment never succeeded.
        if (payment.getStatus() == PaymentStatus.PENDING
                || payment.getStatus() == PaymentStatus.FAILED) {
            return payment;
        }

        if (payment.getStatus() != PaymentStatus.SUCCESS) {
            throw new IllegalStateException(
                    "Unsupported payment status for refund: " + payment.getStatus());
        }

        // Simulate a refund. No real money is transferred.
        payment.setStatus(PaymentStatus.REFUNDED);

        return paymentRepository.save(payment);
    }
}
