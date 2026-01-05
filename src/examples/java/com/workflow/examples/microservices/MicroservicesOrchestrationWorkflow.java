package com.workflow.examples.microservices;

import com.workflow.*;
import com.workflow.context.WorkflowContext;
import com.workflow.exception.TaskExecutionException;
import com.workflow.policy.RetryPolicy;
import com.workflow.task.AbstractTask;
import com.workflow.task.TaskDescriptor;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

/**
 * Microservices Orchestration Example demonstrating how to coordinate multiple microservices with
 * fault tolerance, compensation logic, and the Saga pattern.
 *
 * <p><b>Business Scenario:</b> Travel booking system that coordinates multiple services:
 *
 * <ul>
 *   <li>Flight booking service
 *   <li>Hotel reservation service
 *   <li>Car rental service
 *   <li>Payment service
 *   <li>Notification service
 * </ul>
 *
 * <p><b>Challenges Addressed:</b>
 *
 * <ul>
 *   <li>Distributed transaction management (Saga pattern)
 *   <li>Service failure handling and compensation
 *   <li>Circuit breaker for unreliable services
 *   <li>Parallel service calls for performance
 *   <li>Timeout handling for slow services
 * </ul>
 *
 * <p><b>Workflow Structure:</b>
 *
 * <pre>
 * 1. Validate Request
 * 2. Check Availability (Parallel: Flight, Hotel, Car)
 * 3. Create Reservations (Sequential with compensation on failure)
 *    - Reserve Flight (compensate: Cancel Flight)
 *    - Reserve Hotel (compensate: Cancel Hotel)
 *    - Reserve Car (compensate: Cancel Car)
 * 4. Process Payment (Circuit Breaker protected)
 * 5. Confirm Bookings (Parallel)
 * 6. Send Notifications
 * </pre>
 *
 * <p><b>Example Usage:</b>
 *
 * <pre>{@code
 * MicroservicesOrchestrationWorkflow orchestrator = new MicroservicesOrchestrationWorkflow();
 * WorkflowContext context = new WorkflowContext();
 *
 * context.put("customerId", "CUST-123");
 * context.put("tripDetails", tripData);
 *
 * WorkflowResult result = orchestrator.execute(context);
 *
 * if (result.getStatus() == WorkflowStatus.SUCCESS) {
 *     String bookingId = context.getTyped("bookingId", String.class);
 *     System.out.println("Trip booked successfully: " + bookingId);
 * } else {
 *     // Compensating transactions have already been executed
 *     System.err.println("Booking failed and rolled back");
 * }
 * }</pre>
 */
@Slf4j
public class MicroservicesOrchestrationWorkflow {

  public static final String TRIP_DETAILS = "tripDetails";
  public static final String FLIGHT_RESERVATION_ID = "flightReservationId";
  public static final String HOTEL_RESERVATION_ID = "hotelReservationId";
  public static final String CAR_RESERVATION_ID = "carReservationId";
  public static final String PAYMENT_TRANSACTION_ID = "paymentTransactionId";
  public static final String TOTAL_COST = "totalCost";

  /**
   * Build the complete microservices orchestration workflow with Saga pattern and fault tolerance.
   *
   * @return configured workflow
   */
  public Workflow buildWorkflow() {
    // Stage 1: Request Validation
    Workflow validationWorkflow =
        SequentialWorkflow.builder()
            .name("RequestValidation")
            .task(new ValidateCustomerTask())
            .task(new ValidateDatesTask())
            .task(new ValidateBudgetTask())
            .build();

    // Stage 2: Availability Check (Parallel for performance)
    Workflow availabilityCheck =
        ParallelWorkflow.builder()
            .name("AvailabilityCheck")
            .workflow(
                createProtectedServiceCall(
                    "FlightAvailability", new CheckFlightAvailabilityTask(), 3))
            .workflow(
                createProtectedServiceCall(
                    "HotelAvailability", new CheckHotelAvailabilityTask(), 3))
            .workflow(
                createProtectedServiceCall("CarAvailability", new CheckCarAvailabilityTask(), 3))
            .failFast(true) // Fail immediately if any service is unavailable
            .build();

    // Stage 3: Reservations with Compensation (Saga pattern)
    // Each reservation step has a corresponding cancellation for rollback
    Workflow reservationWorkflow = buildSagaReservationWorkflow();

    // Stage 4: Payment Processing (Circuit Breaker protected)
    Workflow paymentWorkflow = createProtectedServiceCall("Payment", new ProcessPaymentTask(), 2);

    // Stage 5: Confirmation (Parallel)
    Workflow confirmationWorkflow =
        ParallelWorkflow.builder()
            .name("ConfirmBookings")
            .task(new ConfirmFlightTask())
            .task(new ConfirmHotelTask())
            .task(new ConfirmCarTask())
            .failFast(false) // Try to confirm all even if one fails
            .build();

    // Stage 6: Notifications
    Workflow notificationWorkflow =
        ParallelWorkflow.builder()
            .name("SendNotifications")
            .task(new SendBookingConfirmationTask())
            .task(new SendItineraryTask())
            .task(new UpdateLoyaltyPointsTask())
            .build();

    // Main Orchestration Pipeline
    return SequentialWorkflow.builder()
        .name("TravelBookingOrchestration")
        .workflow(validationWorkflow)
        .workflow(availabilityCheck)
        .workflow(reservationWorkflow)
        .workflow(paymentWorkflow)
        .workflow(confirmationWorkflow)
        .workflow(notificationWorkflow)
        .build();
  }

  /**
   * Build Saga pattern workflow with compensation logic for reservations.
   *
   * <p>If any reservation fails, all previous reservations are compensated (canceled) in reverse
   * order.
   */
  private Workflow buildSagaReservationWorkflow() {
    return new AbstractWorkflow() {

      @Override
      protected WorkflowResult doExecute(WorkflowContext context, ExecutionContext execContext) {
        List<String> completedSteps = new ArrayList<>();

        try {
          // Step 1: Reserve Flight
          log.info("Step 1: Reserving flight...");
          new ReserveFlightTask().execute(context);
          completedSteps.add("flight");

          // Step 2: Reserve Hotel
          log.info("Step 2: Reserving hotel...");
          new ReserveHotelTask().execute(context);
          completedSteps.add("hotel");

          // Step 3: Reserve Car
          log.info("Step 3: Reserving car...");
          new ReserveCarTask().execute(context);
          completedSteps.add("car");

          log.info("All reservations completed successfully");
          return execContext.success();

        } catch (Exception e) {
          log.error("Reservation failed, executing compensation for: {}", completedSteps, e);

          // Compensate in reverse order
          compensate(context, completedSteps);

          return execContext.failure(
              new TaskExecutionException(
                  "Reservation failed and rolled back: " + e.getMessage(), e));
        }
      }

      private void compensate(WorkflowContext context, List<String> completedSteps) {
        // Compensate in reverse order
        Collections.reverse(completedSteps);

        for (String step : completedSteps) {
          try {
            log.warn("Compensating: {}", step);
            switch (step) {
              case "car":
                new CancelCarReservationTask().execute(context);
                break;
              case "hotel":
                new CancelHotelReservationTask().execute(context);
                break;
              case "flight":
                new CancelFlightReservationTask().execute(context);
                break;
              default:
                break;
            }
          } catch (Exception ce) {
            log.error("Compensation failed for {}: {}", step, ce.getMessage());
            // Log but continue compensating other steps
          }
        }
      }

      @Override
      public String getName() {
        return "SagaReservationWorkflow";
      }
    };
  }

  /**
   * Create a protected service call with retry policy and timeout.
   *
   * @param serviceName name of the service
   * @param task the task calling the service
   * @param maxRetries maximum number of retries
   * @return workflow with retry and timeout protection
   */
  private Workflow createProtectedServiceCall(
      String serviceName, AbstractTask task, int maxRetries) {
    TaskDescriptor descriptor =
        TaskDescriptor.builder()
            .task(task)
            .name(serviceName + "Task")
            .retryPolicy(
                RetryPolicy.limitedRetriesWithBackoff(
                    maxRetries, RetryPolicy.BackoffStrategy.exponentialWithJitter(500, 5000)))
            .build();

    return new TaskWorkflow(descriptor);
  }

  public static void main(String[] args) {
    MicroservicesOrchestrationWorkflow orchestration = new MicroservicesOrchestrationWorkflow();
    Workflow workflow = orchestration.buildWorkflow();

    // Prepare booking request
    WorkflowContext context = new WorkflowContext();

    // Customer info
    context.put("customerId", "CUST-98765");
    context.put("customerEmail", "traveler@example.com");

    // Trip details
    Map<String, Object> tripDetails = new HashMap<>();
    tripDetails.put("destination", "New York");
    tripDetails.put("startDate", "2025-03-15");
    tripDetails.put("endDate", "2025-03-20");
    tripDetails.put("passengers", 2);
    context.put(TRIP_DETAILS, tripDetails);

    // Budget
    context.put("maxBudget", 5000.0);

    // Flight preferences
    Map<String, Object> flightPrefs = new HashMap<>();
    flightPrefs.put("class", "ECONOMY");
    flightPrefs.put("airline", "ANY");
    context.put("flightPreferences", flightPrefs);

    // Hotel preferences
    Map<String, Object> hotelPrefs = new HashMap<>();
    hotelPrefs.put("stars", 4);
    hotelPrefs.put("amenities", Arrays.asList("WiFi", "Parking", "Breakfast"));
    context.put("hotelPreferences", hotelPrefs);

    // Car preferences
    Map<String, Object> carPrefs = new HashMap<>();
    carPrefs.put("type", "SUV");
    carPrefs.put("automatic", true);
    context.put("carPreferences", carPrefs);

    // Execute workflow
    log.info("=".repeat(70));
    log.info("Starting Travel Booking Orchestration Workflow");
    log.info("=".repeat(70));

    Instant start = Instant.now();

    try {
      WorkflowResult result = workflow.execute(context);

      Instant end = Instant.now();
      Duration duration = Duration.between(start, end);

      log.info("=".repeat(70));
      log.info("Workflow Status: {}", result.getStatus());
      log.info("Execution Time: {} ms", duration.toMillis());
      log.info("=".repeat(70));

      if (result.getStatus() == WorkflowStatus.SUCCESS) {
        log.info("✓ Booking ID: {}", context.get("bookingId"));
        log.info("✓ Flight Reservation: {}", context.get(FLIGHT_RESERVATION_ID));
        log.info("✓ Hotel Reservation: {}", context.get(HOTEL_RESERVATION_ID));
        log.info("✓ Car Reservation: {}", context.get(CAR_RESERVATION_ID));
        log.info("✓ Payment Transaction: {}", context.get(PAYMENT_TRANSACTION_ID));
        log.info("✓ Total Cost: ${}", context.get(TOTAL_COST));
      } else {
        log.error("✗ Booking Failed: {}", result.getError().getMessage());
        log.error("✗ Compensations Applied: {}", context.get("compensationsApplied"));
      }

    } catch (Exception e) {
      log.error("Unexpected error during orchestration", e);
    }
  }

  // ==================== Task Implementations ====================

  static class ValidateCustomerTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Validating customer...");
      String customerId = require(context, "customerId", String.class);
      // Simulate customer validation
      if (customerId.isEmpty()) {
        throw new TaskExecutionException("Invalid customer ID");
      }
      context.put("customerValidated", true);
      log.info("✓ Customer validated: {}", customerId);
    }
  }

  static class ValidateDatesTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Validating travel dates...");
      @SuppressWarnings("unchecked")
      Map<String, Object> tripDetails = context.getTyped(TRIP_DETAILS, Map.class);
      log.info("Trip Details: {}", tripDetails);
      // Simulate date validation
      context.put("datesValidated", true);
      log.info("✓ Dates validated");
    }
  }

  static class ValidateBudgetTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Validating budget...");
      Double budget = context.getTyped("maxBudget", Double.class);
      context.put("budgetValidated", true);
      log.info("✓ Budget validated: ${}", budget);
    }
  }

  static class CheckFlightAvailabilityTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Checking flight availability...");
      simulateNetworkCall(200);
      context.put("flightAvailable", true);
      context.put("flightPrice", 450.0);
      log.info("✓ Flight available");
    }
  }

  static class CheckHotelAvailabilityTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Checking hotel availability...");
      simulateNetworkCall(150);
      context.put("hotelAvailable", true);
      context.put("hotelPrice", 150.0);
      log.info("✓ Hotel available");
    }
  }

  static class CheckCarAvailabilityTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Checking car availability...");
      simulateNetworkCall(100);
      context.put("carAvailable", true);
      context.put("carPrice", 75.0);
      log.info("✓ Car available");
    }
  }

  static class ReserveFlightTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Reserving flight...");
      simulateNetworkCall(300);

      // Simulate occasional failure for demonstration
      if (Math.random() < 0.1) {
        throw new TaskExecutionException("Flight reservation service unavailable");
      }

      String reservationId = "FLT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(FLIGHT_RESERVATION_ID, reservationId);
      log.info("✓ Flight reserved: {}", reservationId);
    }
  }

  static class ReserveHotelTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Reserving hotel...");
      simulateNetworkCall(250);

      // Simulate occasional failure
      if (Math.random() < 0.05) {
        throw new TaskExecutionException("Hotel reservation service unavailable");
      }

      String reservationId = "HTL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(HOTEL_RESERVATION_ID, reservationId);
      log.info("✓ Hotel reserved: {}", reservationId);
    }
  }

  static class ReserveCarTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Reserving car...");
      simulateNetworkCall(200);

      String reservationId = "CAR-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(CAR_RESERVATION_ID, reservationId);
      log.info("✓ Car reserved: {}", reservationId);
    }
  }

  static class ProcessPaymentTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) throws TaskExecutionException {
      log.info("Processing payment...");
      simulateNetworkCall(400);

      Double flightPrice = context.getTyped("flightPrice", Double.class);
      Double hotelPrice = context.getTyped("hotelPrice", Double.class);
      Double carPrice = context.getTyped("carPrice", Double.class);

      @SuppressWarnings("unchecked")
      Map<String, Object> tripDetails = context.getTyped(TRIP_DETAILS, Map.class);
      int nights = 5; // Simplified
      int passengers = (int) tripDetails.get("passengers");

      double totalCost = (flightPrice * passengers) + (hotelPrice * nights) + (carPrice * nights);

      // Simulate payment
      String transactionId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put(PAYMENT_TRANSACTION_ID, transactionId);
      context.put(TOTAL_COST, totalCost);
      log.info("✓ Payment processed: {} for ${}", transactionId, totalCost);
    }
  }

  static class ConfirmFlightTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Confirming flight reservation...");
      simulateNetworkCall(100);
      context.put("flightConfirmed", true);
      log.info("✓ Flight confirmed");
    }
  }

  static class ConfirmHotelTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Confirming hotel reservation...");
      simulateNetworkCall(100);
      context.put("hotelConfirmed", true);
      log.info("✓ Hotel confirmed");
    }
  }

  static class ConfirmCarTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Confirming car reservation...");
      simulateNetworkCall(100);
      context.put("carConfirmed", true);
      log.info("✓ Car confirmed");
    }
  }

  static class SendBookingConfirmationTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Sending booking confirmation...");
      String bookingId = "BKG-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
      context.put("bookingId", bookingId);
      log.info("✓ Confirmation sent: {}", bookingId);
    }
  }

  static class SendItineraryTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Sending itinerary...");
      context.put("itinerarySent", true);
      log.info("✓ Itinerary sent");
    }
  }

  static class UpdateLoyaltyPointsTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.info("Updating loyalty points...");
      Double totalCost = context.getTyped(TOTAL_COST, Double.class);
      int points = (int) (totalCost / 10); // 1 point per $10
      context.put("loyaltyPointsEarned", points);
      log.info("✓ Loyalty points updated: +{} points", points);
    }
  }

  // Compensation Tasks

  static class CancelFlightReservationTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.warn("⚠ Cancelling flight reservation...");
      String reservationId = context.getTyped(FLIGHT_RESERVATION_ID, String.class);
      simulateNetworkCall(150);
      log.warn("✓ Flight cancelled: {}", reservationId);
    }
  }

  static class CancelHotelReservationTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.warn("⚠ Cancelling hotel reservation...");
      String reservationId = context.getTyped(HOTEL_RESERVATION_ID, String.class);
      simulateNetworkCall(150);
      log.warn("✓ Hotel cancelled: {}", reservationId);
    }
  }

  static class CancelCarReservationTask extends AbstractTask {
    @Override
    protected void doExecute(WorkflowContext context) {
      log.warn("⚠ Cancelling car reservation...");
      String reservationId = context.getTyped(CAR_RESERVATION_ID, String.class);
      simulateNetworkCall(150);
      log.warn("✓ Car cancelled: {}", reservationId);
    }
  }

  // Helper method to simulate network latency
  private static void simulateNetworkCall(int millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException _) {
      Thread.currentThread().interrupt();
    }
  }
}
