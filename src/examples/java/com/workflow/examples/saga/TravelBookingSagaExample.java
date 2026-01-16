package com.workflow.examples.saga;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.saga.SagaStep;
import com.workflow.task.Task;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Real-world example demonstrating SagaWorkflow for a travel booking system.
 *
 * <p><b>Business Scenario:</b> A travel booking platform that coordinates multiple services to
 * create a complete trip package. The booking involves:
 *
 * <ul>
 *   <li>Flight reservation
 *   <li>Hotel booking
 *   <li>Car rental
 *   <li>Payment processing
 *   <li>Travel insurance
 * </ul>
 *
 * <p><b>Challenge:</b> If any step fails (e.g., payment declined), all previous bookings must be
 * canceled to maintain consistency across distributed services.
 *
 * <p><b>Solution:</b> SagaWorkflow ensures that if any step fails, compensating actions
 * (cancellations) are executed automatically in reverse order.
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * TravelBookingSagaExample example = new TravelBookingSagaExample();
 * WorkflowContext context = new WorkflowContext();
 *
 * TripRequest request = TripRequest.builder()
 *     .customerId("CUST-123")
 *     .destination("Paris")
 *     .checkIn(LocalDate.of(2026, 6, 1))
 *     .checkOut(LocalDate.of(2026, 6, 7))
 *     .passengers(2)
 *     .build();
 *
 * context.put("tripRequest", request);
 * context.put("customerEmail", "customer@example.com");
 *
 * WorkflowResult result = example.getWorkflow().execute(context);
 *
 * if (result.isSuccess()) {
 *     BookingConfirmation confirmation = context.getTyped("bookingConfirmation", BookingConfirmation.class);
 *     System.out.println("Trip booked successfully: " + confirmation.getBookingId());
 * } else {
 *     System.err.println("Booking failed and rolled back: " + result.getError().getMessage());
 * }
 * }</pre>
 */
@Slf4j
public class TravelBookingSagaExample {

  // Context keys
  private static final String TRIP_REQUEST = "tripRequest";
  private static final String FLIGHT_BOOKING_ID = "flightBookingId";
  private static final String HOTEL_BOOKING_ID = "hotelBookingId";
  private static final String CAR_RENTAL_ID = "carRentalId";
  private static final String PAYMENT_TRANSACTION_ID = "paymentTransactionId";
  private static final String INSURANCE_POLICY_ID = "insurancePolicyId";
  private static final String BOOKING_CONFIRMATION = "bookingConfirmation";
  private static final String TOTAL_COST = "totalCost";
  public static final String CUSTOMER_EMAIL = "customerEmail";
  public static final String CUSTOMER_NAME = "customerName";

  /**
   * Creates the travel booking saga workflow.
   *
   * @return configured SagaWorkflow
   */
  public Workflow getWorkflow() {
    return SagaWorkflow.builder()
        .name("TravelBookingSaga")
        // Step 1: Reserve Flight
        .step(
            SagaStep.builder()
                .name("ReserveFlight")
                .action(new ReserveFlightTask())
                .compensation(new CancelFlightTask())
                .build())
        // Step 2: Book Hotel
        .step(
            SagaStep.builder()
                .name("BookHotel")
                .action(new BookHotelTask())
                .compensation(new CancelHotelTask())
                .build())
        // Step 3: Rent Car
        .step(
            SagaStep.builder()
                .name("RentCar")
                .action(new RentCarTask())
                .compensation(new CancelCarRentalTask())
                .build())
        // Step 4: Calculate Total Cost
        .step(
            SagaStep.builder()
                .name("CalculateTotalCost")
                .action(new CalculateTotalCostTask())
                .build()) // No compensation needed for calculation
        // Step 5: Process Payment
        .step(
            SagaStep.builder()
                .name("ProcessPayment")
                .action(new ProcessPaymentTask())
                .compensation(new RefundPaymentTask())
                .build())
        // Step 6: Purchase Travel Insurance
        .step(
            SagaStep.builder()
                .name("PurchaseInsurance")
                .action(new PurchaseInsuranceTask())
                .compensation(new CancelInsuranceTask())
                .build())
        // Step 7: Generate Confirmation
        .step(
            SagaStep.builder()
                .name("GenerateConfirmation")
                .action(new GenerateConfirmationTask())
                .build()) // No compensation needed
        .build();
  }

  static void main() {
    TravelBookingSagaExample example = new TravelBookingSagaExample();

    // Success scenario
    log.info("=== Scenario 1: Successful Booking ===");
    runSuccessScenario(example);

    log.info("\n=== Scenario 2: Payment Failure with Compensation ===");
    runPaymentFailureScenario(example);

    log.info("\n=== Scenario 3: Insurance Failure with Compensation ===");
    runInsuranceFailureScenario(example);
  }

  private static void runSuccessScenario(TravelBookingSagaExample example) {
    WorkflowContext context = new WorkflowContext();

    TripRequest request =
        TripRequest.builder()
            .customerId("CUST-001")
            .destination("Paris, France")
            .origin("New York, USA")
            .checkIn(LocalDate.of(2026, 6, 1))
            .checkOut(LocalDate.of(2026, 6, 7))
            .passengers(2)
            .build();

    context.put(TRIP_REQUEST, request);
    context.put(CUSTOMER_EMAIL, "john.doe@example.com");
    context.put(CUSTOMER_NAME, "John Doe");

    WorkflowResult result = example.getWorkflow().execute(context);

    if (result.isSuccess()) {
      BookingConfirmation confirmation =
          context.getTyped(BOOKING_CONFIRMATION, BookingConfirmation.class);
      log.info("✅ Success! Booking ID: {}", confirmation.getBookingId());
      log.info("   Flight: {}", context.get(FLIGHT_BOOKING_ID));
      log.info("   Hotel: {}", context.get(HOTEL_BOOKING_ID));
      log.info("   Car: {}", context.get(CAR_RENTAL_ID));
      log.info("   Total Cost: ${}", context.get(TOTAL_COST));
    } else {
      log.error("❌ Booking failed: {}", result.getError().getMessage());
    }
  }

  private static void runPaymentFailureScenario(TravelBookingSagaExample example) {
    WorkflowContext context = new WorkflowContext();

    TripRequest request =
        TripRequest.builder()
            .customerId("CUST-002")
            .destination("Tokyo, Japan")
            .origin("Los Angeles, USA")
            .checkIn(LocalDate.of(2026, 7, 15))
            .checkOut(LocalDate.of(2026, 7, 22))
            .passengers(1)
            .build();

    context.put(TRIP_REQUEST, request);
    context.put(CUSTOMER_EMAIL, "jane.smith@example.com");
    context.put(CUSTOMER_NAME, "Jane Smith");
    context.put("simulatePaymentFailure", true); // Trigger payment failure

    WorkflowResult result = example.getWorkflow().execute(context);

    log.info("Status: {}", result.getStatus());
    log.info("Error: {}", result.getError().getMessage());
    log.info(
        "Note: Flight, Hotel, and Car reservations have been automatically canceled due to payment"
            + " failure");
  }

  private static void runInsuranceFailureScenario(TravelBookingSagaExample example) {
    WorkflowContext context = new WorkflowContext();

    TripRequest request =
        TripRequest.builder()
            .customerId("CUST-003")
            .destination("London, UK")
            .origin("Boston, USA")
            .checkIn(LocalDate.of(2026, 8, 10))
            .checkOut(LocalDate.of(2026, 8, 17))
            .passengers(3)
            .build();

    context.put(TRIP_REQUEST, request);
    context.put(CUSTOMER_EMAIL, "bob.jones@example.com");
    context.put(CUSTOMER_NAME, "Bob Jones");
    context.put("simulateInsuranceFailure", true); // Trigger insurance failure

    WorkflowResult result = example.getWorkflow().execute(context);

    log.info("Status: {}", result.getStatus());
    log.info("Error: {}", result.getError().getMessage());
    log.info(
        "Note: Payment refunded, and all reservations canceled due to insurance purchase failure");
  }

  // ==================== Domain Models ====================

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  static class TripRequest {
    private String customerId;
    private String origin;
    private String destination;
    private LocalDate checkIn;
    private LocalDate checkOut;
    private int passengers;
  }

  @Data
  @Builder
  @AllArgsConstructor
  @NoArgsConstructor
  static class BookingConfirmation {
    private String bookingId;
    private String flightBookingId;
    private String hotelBookingId;
    private String carRentalId;
    private String insurancePolicyId;
    private BigDecimal totalCost;
    private LocalDate bookingDate;
  }

  // ==================== Action Tasks ====================

  /** Reserves flight tickets. */
  static class ReserveFlightTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TripRequest request = context.getTyped(TRIP_REQUEST, TripRequest.class);
      log.info(
          "→ Reserving flight from {} to {} for {} passenger(s)",
          request.getOrigin(),
          request.getDestination(),
          request.getPassengers());

      // Simulate API call to flight booking service
      String bookingId = "FL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      BigDecimal flightCost = BigDecimal.valueOf(350.00 * request.getPassengers());

      context.put(FLIGHT_BOOKING_ID, bookingId);
      context.put("flightCost", flightCost);

      log.info("✓ Flight reserved: {} (Cost: ${})", bookingId, flightCost);
    }
  }

  /** Books hotel room. */
  static class BookHotelTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TripRequest request = context.getTyped(TRIP_REQUEST, TripRequest.class);
      long nights = request.getCheckOut().toEpochDay() - request.getCheckIn().toEpochDay();

      log.info("→ Booking hotel in {} for {} night(s)", request.getDestination(), nights);

      // Simulate API call to hotel booking service
      String bookingId = "HT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      BigDecimal hotelCost = BigDecimal.valueOf(150.00 * nights);

      context.put(HOTEL_BOOKING_ID, bookingId);
      context.put("hotelCost", hotelCost);

      log.info("✓ Hotel booked: {} (Cost: ${})", bookingId, hotelCost);
    }
  }

  /** Rents a car. */
  static class RentCarTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      TripRequest request = context.getTyped(TRIP_REQUEST, TripRequest.class);
      long days = request.getCheckOut().toEpochDay() - request.getCheckIn().toEpochDay();

      log.info("→ Renting car at {} for {} day(s)", request.getDestination(), days);

      // Simulate API call to car rental service
      String rentalId = "CR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      BigDecimal carCost = BigDecimal.valueOf(45.00 * days);

      context.put(CAR_RENTAL_ID, rentalId);
      context.put("carCost", carCost);

      log.info("✓ Car rented: {} (Cost: ${})", rentalId, carCost);
    }
  }

  /** Calculates total booking cost. */
  static class CalculateTotalCostTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      BigDecimal flightCost = context.getTyped("flightCost", BigDecimal.class);
      BigDecimal hotelCost = context.getTyped("hotelCost", BigDecimal.class);
      BigDecimal carCost = context.getTyped("carCost", BigDecimal.class);

      BigDecimal totalCost =
          flightCost.add(hotelCost).add(carCost).setScale(2, RoundingMode.HALF_UP);

      context.put(TOTAL_COST, totalCost);
      log.info("→ Total cost calculated: ${}", totalCost);
    }
  }

  /** Processes payment for the trip. */
  static class ProcessPaymentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      BigDecimal totalCost = context.getTyped(TOTAL_COST, BigDecimal.class);
      TripRequest request = context.getTyped(TRIP_REQUEST, TripRequest.class);

      log.info("→ Processing payment of ${} for customer {}", totalCost, request.getCustomerId());

      // Simulate payment failure scenario
      if (Boolean.TRUE.equals(context.get("simulatePaymentFailure"))) {
        throw new TaskExecutionException(
            "Payment declined: Insufficient funds in account for $" + totalCost);
      }

      // Simulate API call to payment gateway
      String transactionId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(PAYMENT_TRANSACTION_ID, transactionId);

      log.info("✓ Payment processed: {} (Amount: ${})", transactionId, totalCost);
    }
  }

  /** Purchases travel insurance. */
  static class PurchaseInsuranceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      BigDecimal totalCost = context.getTyped(TOTAL_COST, BigDecimal.class);
      BigDecimal insuranceCost = totalCost.multiply(BigDecimal.valueOf(0.05)); // 5% of total

      log.info("→ Purchasing travel insurance for ${}", insuranceCost);

      // Simulate insurance failure scenario
      if (Boolean.TRUE.equals(context.get("simulateInsuranceFailure"))) {
        throw new TaskExecutionException(
            "Insurance service unavailable: Unable to process insurance request");
      }

      // Simulate API call to insurance service
      String policyId = "INS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(INSURANCE_POLICY_ID, policyId);
      context.put("insuranceCost", insuranceCost);

      log.info("✓ Insurance purchased: {} (Cost: ${})", policyId, insuranceCost);
    }
  }

  /** Generates final booking confirmation. */
  static class GenerateConfirmationTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      log.info("→ Generating booking confirmation");

      BookingConfirmation confirmation =
          BookingConfirmation.builder()
              .bookingId("BK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
              .flightBookingId(context.getTyped(FLIGHT_BOOKING_ID, String.class))
              .hotelBookingId(context.getTyped(HOTEL_BOOKING_ID, String.class))
              .carRentalId(context.getTyped(CAR_RENTAL_ID, String.class))
              .insurancePolicyId(context.getTyped(INSURANCE_POLICY_ID, String.class))
              .totalCost(context.getTyped(TOTAL_COST, BigDecimal.class))
              .bookingDate(LocalDate.now())
              .build();

      context.put(BOOKING_CONFIRMATION, confirmation);
      log.info("✓ Booking confirmation generated: {}", confirmation.getBookingId());
    }
  }

  // ==================== Compensation Tasks ====================

  /** Cancels flight reservation. */
  static class CancelFlightTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String bookingId = context.getTyped(FLIGHT_BOOKING_ID, String.class);
      if (bookingId != null) {
        log.warn("↩ Canceling flight reservation: {}", bookingId);
        // Simulate API call to cancel flight
        context.remove(FLIGHT_BOOKING_ID);
        log.info("✓ Flight reservation canceled: {}", bookingId);
      }
    }
  }

  /** Cancels hotel booking. */
  static class CancelHotelTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String bookingId = context.getTyped(HOTEL_BOOKING_ID, String.class);
      if (bookingId != null) {
        log.warn("↩ Canceling hotel booking: {}", bookingId);
        // Simulate API call to cancel hotel
        context.remove(HOTEL_BOOKING_ID);
        log.info("✓ Hotel booking canceled: {}", bookingId);
      }
    }
  }

  /** Cancels car rental. */
  static class CancelCarRentalTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String rentalId = context.getTyped(CAR_RENTAL_ID, String.class);
      if (rentalId != null) {
        log.warn("↩ Canceling car rental: {}", rentalId);
        // Simulate API call to cancel car rental
        context.remove(CAR_RENTAL_ID);
        log.info("✓ Car rental canceled: {}", rentalId);
      }
    }
  }

  /** Refunds payment. */
  static class RefundPaymentTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String transactionId = context.getTyped(PAYMENT_TRANSACTION_ID, String.class);
      if (transactionId != null) {
        BigDecimal amount = context.getTyped(TOTAL_COST, BigDecimal.class);
        log.warn("↩ Refunding payment: {} (Amount: ${})", transactionId, amount);
        // Simulate API call to payment gateway for refund
        String refundId = "REF-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        context.put("refundId", refundId);
        context.remove(PAYMENT_TRANSACTION_ID);
        log.info("✓ Payment refunded: {} (Refund ID: {})", transactionId, refundId);
      }
    }
  }

  /** Cancels insurance policy. */
  static class CancelInsuranceTask implements Task {
    @Override
    public void execute(WorkflowContext context) {
      String policyId = context.getTyped(INSURANCE_POLICY_ID, String.class);
      if (policyId != null) {
        log.warn("↩ Canceling insurance policy: {}", policyId);
        // Simulate API call to cancel insurance
        context.remove(INSURANCE_POLICY_ID);
        log.info("✓ Insurance policy canceled: {}", policyId);
      }
    }
  }
}
