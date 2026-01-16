package com.workflow.examples.interpolation;

import com.workflow.interpolation.JakartaElStringInterpolator;
import java.util.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class InterpolationRealWorldExamples {

  public static final String PATIENT_NAME = "patientName";
  public static final String DUE_DATE = "dueDate";
  public static final String COURSE_NAME = "courseName";

  /** Example 1-10: E-Commerce Domain */
  public static void eCommerceExamples() {
    log.info("\n=== E-Commerce Examples ===\n");

    // Example 1: Order Confirmation Email
    Map<String, Object> orderVars =
        Map.of(
            "customerName",
            "Alice Johnson",
            "orderNumber",
            "ORD-2024-12345",
            "itemCount",
            3,
            "subtotal",
            159.97,
            "tax",
            12.80,
            "shipping",
            9.99,
            "total",
            182.76,
            "estimatedDelivery",
            "January 20, 2026");

    JakartaElStringInterpolator interpolator = JakartaElStringInterpolator.forVariables(orderVars);

    String orderConfirmation =
        interpolator.interpolate(
            """
                            Dear ${customerName},

                            Thank you for your order!

                            Order Number: ${orderNumber}
                            Items: ${itemCount}
                            Subtotal: $${subtotal}
                            Tax: $${tax}
                            Shipping: $${shipping}
                            Total: $${total}

                            Estimated Delivery: ${estimatedDelivery}
                            """);
    log.info("Example 1 - Order Confirmation:\n{}", orderConfirmation);

    // Example 2: Product Price with Discount
    Map<String, Object> productVars =
        Map.of(
            "productName",
            "Wireless Headphones",
            "originalPrice",
            199.99,
            "discountPercent",
            25,
            "finalPrice",
            149.99);

    interpolator = JakartaElStringInterpolator.forVariables(productVars);

    String productDisplay =
        interpolator.interpolate(
            "${productName} - Was $${originalPrice}, Now $${finalPrice} (Save ${discountPercent}%)");
    log.info("Example 2 - Product Discount: {}", productDisplay);

    // Example 3: Inventory Alert
    Map<String, Object> inventoryVars =
        Map.of(
            "productSKU",
            "WH-BT-1000",
            "currentStock",
            5,
            "threshold",
            10,
            "warehouseLocation",
            "Warehouse A");

    interpolator = JakartaElStringInterpolator.forVariables(inventoryVars);

    String inventoryAlert =
        interpolator.interpolate(
            "LOW STOCK ALERT: Product ${productSKU} in ${warehouseLocation} has only ${currentStock} units remaining ${currentStock < threshold ? '(Below threshold!)' : ''}");
    log.info("Example 3 - Inventory Alert: {}", inventoryAlert);

    // Example 4: Shopping Cart Summary
    Map<String, Object> cartVars =
        Map.of(
            "itemsInCart",
            7,
            "uniqueItems",
            4,
            "cartTotal",
            289.95,
            "freeShippingThreshold",
            300.0);

    interpolator = JakartaElStringInterpolator.forVariables(cartVars);

    String cartSummary =
        interpolator.interpolate(
            "Cart: ${uniqueItems} ${uniqueItems == 1 ? 'item' : 'items'} (${itemsInCart} total) | Subtotal: $${cartTotal} ${cartTotal >= freeShippingThreshold ? '✓ Free Shipping!' : '(Add $' += (freeShippingThreshold - cartTotal) += ' for free shipping)'}");
    log.info("Example 4 - Cart Summary: {}", cartSummary);

    // Example 5: Customer Loyalty Tier
    Map<String, Object> loyaltyVars =
        Map.of(
            "customerName",
            "Bob Smith",
            "lifetimeSpend",
            2500.00,
            "currentTier",
            "Gold",
            "pointsBalance",
            1250,
            "nextTier",
            "Platinum",
            "pointsToNextTier",
            750);

    interpolator = JakartaElStringInterpolator.forVariables(loyaltyVars);

    String loyaltyStatus =
        interpolator.interpolate(
            "${customerName}, you're a ${currentTier} member with ${pointsBalance} points. Spend ${pointsToNextTier} more points to reach ${nextTier} status!");
    log.info("Example 5 - Loyalty Status: {}", loyaltyStatus);

    // Example 6: Flash Sale Countdown
    Map<String, Object> saleVars =
        Map.of(
            "saleTitle", "Black Friday Mega Sale",
            "hoursRemaining", 4,
            "minutesRemaining", 37,
            "totalDeals", 152,
            "dealsClaimed", 89);

    interpolator = JakartaElStringInterpolator.forVariables(saleVars);

    String saleCountdown =
        interpolator.interpolate(
            "${saleTitle}: ${hoursRemaining}h ${minutesRemaining}m remaining! ${totalDeals - dealsClaimed} deals still available (${dealsClaimed} claimed)");
    log.info("Example 6 - Flash Sale: {}", saleCountdown);

    // Example 7: Shipping Status Update
    Map<String, Object> shippingVars =
        Map.of(
            "trackingNumber", "1Z999AA10123456784",
            "currentLocation", "Distribution Center - Los Angeles, CA",
            "status", "In Transit",
            "estimatedDays", 2);

    interpolator = JakartaElStringInterpolator.forVariables(shippingVars);

    String shippingUpdate =
        interpolator.interpolate(
            "Tracking ${trackingNumber}: ${status} at ${currentLocation}. Estimated delivery in ${estimatedDays} ${estimatedDays == 1 ? 'day' : 'days'}.");
    log.info("Example 7 - Shipping Status: {}", shippingUpdate);

    // Example 8: Product Review Summary
    Map<String, Object> reviewVars =
        Map.of(
            "productName", "Smart Watch Pro",
            "averageRating", 4.7,
            "totalReviews", 1523,
            "fiveStarCount", 1089,
            "oneStarCount", 45);

    interpolator = JakartaElStringInterpolator.forVariables(reviewVars);

    String reviewSummary =
        interpolator.interpolate(
            "${productName}: ${averageRating}/5.0 stars (${totalReviews} reviews) - ${fiveStarCount} five-star, ${oneStarCount} one-star ${averageRating >= 4.5 ? '🌟 Highly Rated' : ''}");
    log.info("Example 8 - Review Summary: {}", reviewSummary);

    // Example 9: Wishlist Notification
    Map<String, Object> wishlistVars =
        Map.of(
            "itemName", "Gaming Laptop X1",
            "originalPrice", 1499.99,
            "newPrice", 1199.99,
            "discountAmount", 300.00,
            "stockLevel", "Low");

    interpolator = JakartaElStringInterpolator.forVariables(wishlistVars);

    String wishlistAlert =
        interpolator.interpolate(
            "Price Drop Alert! ${itemName} from your wishlist is now $${newPrice} (was $${originalPrice}, save $${discountAmount}). Stock: ${stockLevel}${stockLevel == 'Low' ? ' - Order soon!' : ''}");
    log.info("Example 9 - Wishlist Alert: {}", wishlistAlert);

    // Example 10: Return/Refund Status
    Map<String, Object> returnVars =
        Map.of(
            "returnID", "RET-2024-98765",
            "orderNumber", "ORD-2024-54321",
            "refundAmount", 79.99,
            "processingDays", 3,
            "returnReason", "Defective item");

    interpolator = JakartaElStringInterpolator.forVariables(returnVars);

    String returnStatus =
        interpolator.interpolate(
            "Return ${returnID} for order ${orderNumber}: Refund of $${refundAmount} will be processed in ${processingDays} business days. Reason: ${returnReason}");
    log.info("Example 10 - Return Status: {}", returnStatus);
  }

  /** Example 11-20: Healthcare Domain */
  public static void healthcareExamples() {
    log.info("\n=== Healthcare Examples ===\n");

    // Example 11: Appointment Reminder
    Map<String, Object> appointmentVars =
        Map.of(
            PATIENT_NAME,
            "Jane Doe",
            "doctorName",
            "Dr. Smith",
            "specialty",
            "Cardiology",
            "appointmentDate",
            "January 20, 2026",
            "appointmentTime",
            "2:30 PM",
            "location",
            "Medical Plaza, Suite 200");

    JakartaElStringInterpolator interpolator =
        JakartaElStringInterpolator.forVariables(appointmentVars);

    String appointmentReminder =
        interpolator.interpolate(
            "Dear ${patientName}, this is a reminder of your ${specialty} appointment with ${doctorName} on ${appointmentDate} at ${appointmentTime}. Location: ${location}");
    log.info("Example 11 - Appointment Reminder: {}", appointmentReminder);

    // Example 12: Prescription Refill
    Map<String, Object> prescriptionVars =
        Map.of(
            "medicationName", "Lisinopril 10mg",
            "refillsRemaining", 2,
            "lastFillDate", "December 15, 2025",
            "nextRefillDate", "January 15, 2026",
            "pharmacyName", "City Pharmacy");

    interpolator = JakartaElStringInterpolator.forVariables(prescriptionVars);

    String prescriptionNotice =
        interpolator.interpolate(
            "Your prescription for ${medicationName} has ${refillsRemaining} ${refillsRemaining == 1 ? 'refill' : 'refills'} remaining. Next refill available: ${nextRefillDate}. Call ${pharmacyName} to refill.");
    log.info("Example 12 - Prescription Refill: {}", prescriptionNotice);

    // Example 13: Lab Results Notification
    Map<String, Object> labVars =
        Map.of(
            "testName", "Complete Blood Count",
            "orderDate", "January 10, 2026",
            "resultStatus", "Completed",
            "abnormalFlags", 0,
            "reviewRequired", false);

    interpolator = JakartaElStringInterpolator.forVariables(labVars);

    String labResults =
        interpolator.interpolate(
            "Lab Results: ${testName} (${orderDate}) - ${resultStatus}. ${abnormalFlags > 0 ? 'ATTENTION: ' += abnormalFlags += ' abnormal value(s) detected.' : 'All values within normal range.'}");
    log.info("Example 13 - Lab Results: {}", labResults);

    // Example 14: Patient Vitals Summary
    Map<String, Object> vitalsVars =
        Map.of(
            "systolic", 128,
            "diastolic", 82,
            "heartRate", 72,
            "temperature", 98.6,
            "oxygenLevel", 98);

    interpolator = JakartaElStringInterpolator.forVariables(vitalsVars);

    String vitalsReport =
        interpolator.interpolate(
            "Vitals: BP ${systolic}/${diastolic} ${systolic > 140 || diastolic > 90 ? '⚠ High' : '✓ Normal'}, HR ${heartRate}bpm, Temp ${temperature}°F, O2 ${oxygenLevel}%");
    log.info("Example 14 - Patient Vitals: {}", vitalsReport);

    // Example 15: Insurance Coverage Info
    Map<String, Object> insuranceVars =
        Map.of(
            "policyNumber", "POL-123456789",
            "coverageType", "PPO Gold",
            "deductible", 1500.00,
            "deductibleMet", 800.00,
            "outOfPocketMax", 5000.00,
            "outOfPocketMet", 2100.00);

    interpolator = JakartaElStringInterpolator.forVariables(insuranceVars);

    String insuranceInfo =
        interpolator.interpolate(
            "${coverageType} (${policyNumber}): Deductible $${deductibleMet}/$${deductible}, Out-of-Pocket $${outOfPocketMet}/$${outOfPocketMax}");
    log.info("Example 15 - Insurance Info: {}", insuranceInfo);

    // Example 16: Medication Interaction Alert
    Map<String, Object> interactionVars =
        Map.of(
            "medication1", "Warfarin",
            "medication2", "Aspirin",
            "severityLevel", "High",
            "riskDescription", "Increased bleeding risk");

    interpolator = JakartaElStringInterpolator.forVariables(interactionVars);

    String interactionAlert =
        interpolator.interpolate(
            "${severityLevel == 'High' ? '🔴 CRITICAL' : severityLevel == 'Medium' ? '🟡 WARNING' : '🟢 INFO'}: Drug interaction between ${medication1} and ${medication2}. ${riskDescription}. Consult physician.");
    log.info("Example 16 - Drug Interaction: {}", interactionAlert);

    // Example 17: Vaccination Record
    Map<String, Object> vaccineVars =
        Map.of(
            "vaccineName", "COVID-19 Booster",
            "administeredDate", "November 15, 2025",
            "nextDueDate", "May 15, 2026",
            "lotNumber", "LOT-XYZ-12345",
            "site", "Left deltoid");

    interpolator = JakartaElStringInterpolator.forVariables(vaccineVars);

    String vaccineRecord =
        interpolator.interpolate(
            "${vaccineName}: Administered ${administeredDate} (Lot ${lotNumber}, Site: ${site}). Next dose due: ${nextDueDate}");
    log.info("Example 17 - Vaccination Record: {}", vaccineRecord);

    // Example 18: Hospital Bed Availability
    Map<String, Object> bedVars =
        Map.of(
            "department", "ICU",
            "totalBeds", 20,
            "occupiedBeds", 17,
            "availableBeds", 3,
            "occupancyRate", 85.0);

    interpolator = JakartaElStringInterpolator.forVariables(bedVars);

    String bedStatus =
        interpolator.interpolate(
            "${department}: ${availableBeds}/${totalBeds} beds available (${occupancyRate}% occupied) ${occupancyRate >= 90 ? '⚠ Near Capacity' : ''}");
    log.info("Example 18 - Bed Availability: {}", bedStatus);

    // Example 19: Telemedicine Session
    Map<String, Object> telemedicineVars =
        Map.of(
            "sessionID",
            "TM-20260116-001",
            PATIENT_NAME,
            "John Williams",
            "doctorName",
            "Dr. Martinez",
            "scheduledTime",
            "3:00 PM",
            "platform",
            "SecureHealth Video",
            "sessionDuration",
            30);

    interpolator = JakartaElStringInterpolator.forVariables(telemedicineVars);

    String telemedicineInfo =
        interpolator.interpolate(
            "Telemedicine Session ${sessionID}: ${patientName} with ${doctorName} at ${scheduledTime} via ${platform} (${sessionDuration} min)");
    log.info("Example 19 - Telemedicine: {}", telemedicineInfo);

    // Example 20: Patient Billing Statement
    Map<String, Object> billingVars =
        Map.of(
            PATIENT_NAME,
            "Sarah Johnson",
            "accountNumber",
            "ACC-789456",
            "totalCharges",
            1250.00,
            "insurancePaid",
            875.00,
            "patientResponsibility",
            375.00,
            DUE_DATE,
            "February 10, 2026");

    interpolator = JakartaElStringInterpolator.forVariables(billingVars);

    String billingStatement =
        interpolator.interpolate(
            "Statement for ${patientName} (${accountNumber}): Total $${totalCharges}, Insurance paid $${insurancePaid}, Your responsibility $${patientResponsibility}. Due: ${dueDate}");
    log.info("Example 20 - Billing Statement: {}", billingStatement);
  }

  /** Example 21-30: Finance & Banking Domain */
  public static void financeExamples() {
    log.info("\n=== Finance & Banking Examples ===\n");

    // Example 21: Account Balance Alert
    Map<String, Object> balanceVars =
        Map.of(
            "accountNumber", "****1234",
            "accountType", "Checking",
            "currentBalance", 523.47,
            "availableBalance", 423.47,
            "lowBalanceThreshold", 500.00);

    JakartaElStringInterpolator interpolator =
        JakartaElStringInterpolator.forVariables(balanceVars);

    String balanceAlert =
        interpolator.interpolate(
            "${accountType} ${accountNumber}: Balance $${currentBalance} ${currentBalance < lowBalanceThreshold ? '⚠ Low Balance Alert' : ''} (Available: $${availableBalance})");
    log.info("Example 21 - Balance Alert: {}", balanceAlert);

    // Example 22: Transaction Notification
    Map<String, Object> transactionVars =
        Map.of(
            "merchantName", "Amazon.com",
            "amount", 89.99,
            "transactionType", "Debit",
            "cardLast4", "5678",
            "timestamp", "Jan 16, 2026 10:23 AM",
            "balance", 433.48);

    interpolator = JakartaElStringInterpolator.forVariables(transactionVars);

    String transactionNotice =
        interpolator.interpolate(
            "${transactionType}: $${amount} at ${merchantName} (Card ${cardLast4}) on ${timestamp}. New balance: $${balance}");
    log.info("Example 22 - Transaction: {}", transactionNotice);

    // Example 23: Loan Payment Reminder
    Map<String, Object> loanVars =
        Map.of(
            "loanType",
            "Auto Loan",
            "loanNumber",
            "LOAN-456789",
            "paymentAmount",
            435.50,
            DUE_DATE,
            "January 25, 2026",
            "remainingBalance",
            12580.00,
            "paymentsRemaining",
            29);

    interpolator = JakartaElStringInterpolator.forVariables(loanVars);

    String loanReminder =
        interpolator.interpolate(
            "${loanType} ${loanNumber}: Payment of $${paymentAmount} due ${dueDate}. Remaining: $${remainingBalance} (${paymentsRemaining} payments)");
    log.info("Example 23 - Loan Payment: {}", loanReminder);

    // Example 24: Credit Card Statement
    Map<String, Object> creditVars =
        Map.of(
            "cardName",
            "Platinum Rewards Card",
            "cardLast4",
            "9012",
            "statementBalance",
            2150.75,
            "minimumDue",
            65.00,
            DUE_DATE,
            "February 5, 2026",
            "creditLimit",
            10000.00,
            "availableCredit",
            7849.25,
            "rewardsEarned",
            2150);

    interpolator = JakartaElStringInterpolator.forVariables(creditVars);

    String creditStatement =
        interpolator.interpolate(
            "${cardName} ${cardLast4}: Balance $${statementBalance}, Min payment $${minimumDue} by ${dueDate}. Available credit: $${availableCredit}/${creditLimit}. Rewards: ${rewardsEarned} points");
    log.info("Example 24 - Credit Card: {}", creditStatement);

    // Example 25: Investment Portfolio Summary
    Map<String, Object> portfolioVars =
        Map.of(
            "totalValue", 125750.00,
            "dayChange", 1250.00,
            "dayChangePercent", 1.00,
            "ytdReturn", 8500.00,
            "ytdReturnPercent", 7.25);

    interpolator = JakartaElStringInterpolator.forVariables(portfolioVars);

    String portfolioSummary =
        interpolator.interpolate(
            "Portfolio: $${totalValue} ${dayChange >= 0 ? '🟢 +' : '🔴 '}$${dayChange} (${dayChangePercent >= 0 ? '+' : ''}${dayChangePercent}% today) | YTD: ${ytdReturnPercent >= 0 ? '+' : ''}${ytdReturnPercent}% ($${ytdReturn})");
    log.info("Example 25 - Portfolio: {}", portfolioSummary);

    // Example 26: Wire Transfer Confirmation
    Map<String, Object> wireVars =
        Map.of(
            "confirmationNumber", "WIRE-20260116-789",
            "amount", 5000.00,
            "recipientName", "Acme Corporation",
            "recipientAccount", "****4567",
            "recipientBank", "First National Bank",
            "fee", 25.00,
            "processingTime", "Same business day");

    interpolator = JakartaElStringInterpolator.forVariables(wireVars);

    String wireConfirmation =
        interpolator.interpolate(
            "Wire Transfer ${confirmationNumber}: $${amount} to ${recipientName} (${recipientBank}, Account ${recipientAccount}). Fee: $${fee}. Processing: ${processingTime}");
    log.info("Example 26 - Wire Transfer: {}", wireConfirmation);

    // Example 27: Fraud Alert
    Map<String, Object> fraudVars =
        Map.of(
            "alertType", "Suspicious Activity",
            "cardNumber", "****8901",
            "attemptedAmount", 1500.00,
            "merchantName", "Unknown Merchant - Foreign",
            "location", "Romania",
            "blocked", true);

    interpolator = JakartaElStringInterpolator.forVariables(fraudVars);

    String fraudAlert =
        interpolator.interpolate(
            "🚨 ${alertType}: Attempted charge of $${attemptedAmount} at ${merchantName} (${location}) on card ${cardNumber}. ${blocked ? 'Transaction BLOCKED. ' : ''}Contact us immediately.");
    log.info("Example 27 - Fraud Alert: {}", fraudAlert);

    // Example 28: Savings Goal Progress
    Map<String, Object> savingsVars =
        Map.of(
            "goalName", "Vacation Fund",
            "targetAmount", 5000.00,
            "currentAmount", 3250.00,
            "remainingAmount", 1750.00,
            "progressPercent", 65.0,
            "targetDate", "June 2026");

    interpolator = JakartaElStringInterpolator.forVariables(savingsVars);

    String savingsProgress =
        interpolator.interpolate(
            "${goalName}: $${currentAmount}/$${targetAmount} (${progressPercent}% complete). $${remainingAmount} to go by ${targetDate}. ${progressPercent >= 80 ? 'Almost there! 🎯' : ''}");
    log.info("Example 28 - Savings Goal: {}", savingsProgress);

    // Example 29: Mortgage Rate Quote
    Map<String, Object> mortgageVars =
        Map.of(
            "loanAmount", 350000.00,
            "interestRate", 6.75,
            "loanTerm", 30,
            "monthlyPayment", 2270.28,
            "totalInterest", 467500.80,
            "apr", 6.95);

    interpolator = JakartaElStringInterpolator.forVariables(mortgageVars);

    String mortgageQuote =
        interpolator.interpolate(
            "Mortgage Quote: $${loanAmount} at ${interestRate}% for ${loanTerm} years. Monthly payment: $${monthlyPayment}. Total interest: $${totalInterest}. APR: ${apr}%");
    log.info("Example 29 - Mortgage Quote: {}", mortgageQuote);

    // Example 30: Stock Trade Execution
    Map<String, Object> tradeVars =
        Map.of(
            "orderID",
            "TRD-20260116-456",
            "symbol",
            "AAPL",
            "action",
            "BUY",
            "shares",
            50,
            "price",
            185.50,
            "totalCost",
            9275.00,
            "commission",
            0.00,
            "executionTime",
            "10:35:22 AM EST");

    interpolator = JakartaElStringInterpolator.forVariables(tradeVars);

    String tradeConfirmation =
        interpolator.interpolate(
            "Order ${orderID}: ${action} ${shares} shares of ${symbol} @ $${price} = $${totalCost} ${commission > 0 ? '(+$' += commission += ' commission)' : '(commission-free)'}. Executed: ${executionTime}");
    log.info("Example 30 - Stock Trade: {}", tradeConfirmation);
  }

  /** Example 31-40: Education Domain */
  public static void educationExamples() {
    log.info("\n=== Education Examples ===\n");

    // Example 31: Course Enrollment Confirmation
    Map<String, Object> enrollmentVars =
        Map.of(
            "studentName",
            "Emily Brown",
            "courseCode",
            "CS-101",
            COURSE_NAME,
            "Introduction to Computer Science",
            "instructor",
            "Prof. Johnson",
            "schedule",
            "MWF 10:00-11:00 AM",
            "credits",
            3,
            "semester",
            "Spring 2026");

    JakartaElStringInterpolator interpolator =
        JakartaElStringInterpolator.forVariables(enrollmentVars);

    String enrollment =
        interpolator.interpolate(
            "Enrolled: ${studentName} in ${courseCode} - ${courseName} (${credits} credits) with ${instructor}. Schedule: ${schedule}, ${semester}");
    log.info("Example 31 - Enrollment: {}", enrollment);

    // Example 32: Grade Report
    Map<String, Object> gradeVars =
        Map.of(
            COURSE_NAME,
            "Advanced Mathematics",
            "midtermScore",
            88,
            "finalScore",
            92,
            "assignmentAvg",
            85,
            "finalGrade",
            "A-",
            "gpa",
            3.7);

    interpolator = JakartaElStringInterpolator.forVariables(gradeVars);

    String gradeReport =
        interpolator.interpolate(
            "${courseName}: Midterm ${midtermScore}%, Final ${finalScore}%, Assignments ${assignmentAvg}% = Final Grade: ${finalGrade} (GPA: ${gpa})");
    log.info("Example 32 - Grade Report: {}", gradeReport);

    // Example 33: Assignment Due Reminder
    Map<String, Object> assignmentVars =
        Map.of(
            "assignmentName",
            "Research Paper",
            COURSE_NAME,
            "English Literature",
            DUE_DATE,
            "January 22, 2026",
            "daysRemaining",
            6,
            "maxPoints",
            100,
            "submissionStatus",
            "Not Submitted");

    interpolator = JakartaElStringInterpolator.forVariables(assignmentVars);

    String assignmentReminder =
        interpolator.interpolate(
            "Reminder: ${assignmentName} for ${courseName} due ${dueDate} (${daysRemaining} ${daysRemaining == 1 ? 'day' : 'days'} remaining). Worth ${maxPoints} points. ${submissionStatus}${daysRemaining <= 2 ? ' ⚠ DUE SOON!' : ''}");
    log.info("Example 33 - Assignment Reminder: {}", assignmentReminder);

    // Example 34: Scholarship Award Notification
    Map<String, Object> scholarshipVars =
        Map.of(
            "recipientName",
            "Michael Chen",
            "scholarshipName",
            "Excellence in STEM Scholarship",
            "awardAmount",
            10000.00,
            "academicYear",
            "2026-2027",
            "renewable",
            true,
            "gpaRequirement",
            3.5);

    interpolator = JakartaElStringInterpolator.forVariables(scholarshipVars);

    String scholarshipNotice =
        interpolator.interpolate(
            "Congratulations ${recipientName}! You've been awarded the ${scholarshipName}: $${awardAmount} for ${academicYear}. ${renewable ? 'Renewable annually with ' += gpaRequirement += ' GPA.' : 'One-time award.'}");
    log.info("Example 34 - Scholarship: {}", scholarshipNotice);

    // Example 35: Class Attendance Report
    Map<String, Object> attendanceVars =
        Map.of(
            "studentName",
            "Jessica Martinez",
            "courseCode",
            "BIO-201",
            "totalClasses",
            30,
            "attended",
            27,
            "absent",
            3,
            "attendanceRate",
            90.0,
            "minimumRequired",
            80.0);

    interpolator = JakartaElStringInterpolator.forVariables(attendanceVars);

    String attendanceReport =
        interpolator.interpolate(
            "${studentName} - ${courseCode}: ${attended}/${totalClasses} classes attended (${attendanceRate}%). Absences: ${absent}. ${attendanceRate >= minimumRequired ? '✓ Meeting requirement' : '⚠ Below minimum ' += minimumRequired += '%'}");
    log.info("Example 35 - Attendance: {}", attendanceReport);
  }

  static void main() {
    eCommerceExamples();
    healthcareExamples();
    financeExamples();
    educationExamples();
  }
}
