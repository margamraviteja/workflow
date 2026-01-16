package com.workflow.examples.interpolation;

import com.workflow.interpolation.JakartaElStringInterpolator;
import java.util.*;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@UtilityClass
public class InterpolationExamples {

  public static final String TOTAL = "total";
  public static final String STATUS = "status";
  public static final String PRICE = "price";
  public static final String PATIENT = "patient";
  public static final String REMAINING = "remaining";
  public static final String CURRENT = "current";
  public static final String SHARES = "shares";
  public static final String STUDENT = "student";
  public static final String COURSE = "course";
  public static final String MEMBERS = "members";
  public static final String REASON = "reason";
  public static final String PROGRESS = "progress";
  public static final String LOCATION = "location";
  public static final String BATTERY = "battery";
  public static final String EMPLOYEE = "employee";

  static void main() {
    runAll();
  }

  public static void runAll() {
    ecommerce();
    healthcare();
    finance();
    education();
    travel();
    socialMedia();
    iot();
    gaming();
    hr();
    logistics();
  }

  private static void demo(int num, String title, Map<String, Object> vars, String template) {
    String result = JakartaElStringInterpolator.forVariables(vars).interpolate(template);
    log.info("{}. {}: {}", num, title, result);
  }

  public static void ecommerce() {
    log.info("\n=== E-COMMERCE (1-12) ===\n");
    demo(
        1,
        "Order Confirmation",
        Map.of("customer", "Alice", "order", "ORD-123", "items", 3, TOTAL, 182.76),
        "Dear ${customer}, Order ${order}: ${items} items, Total: $${total}");
    demo(
        2,
        "Product Discount",
        Map.of("product", "Headphones", "was", 199.99, "now", 149.99),
        "${product}: Was $${was}, Now $${now}!");
    demo(
        3,
        "Stock Alert",
        Map.of("sku", "WH-1000", "stock", 5, "threshold", 10),
        "⚠ SKU ${sku}: ${stock} units ${stock < threshold ? 'CRITICAL!' : 'OK'}");
    demo(
        4,
        "Free Shipping",
        Map.of(TOTAL, 289.95, "freeAt", 300.0),
        "Cart $${total} ${total >= freeAt ? 'FREE SHIPPING!' : '$' += (freeAt - total) += ' to free shipping'}");
    demo(
        5,
        "Loyalty Points",
        Map.of("name", "Bob", "tier", "Gold", "points", 1250),
        "${name} - ${tier}: ${points} points");
    demo(
        6,
        "Flash Sale",
        Map.of("hours", 4, "mins", 37, "claimed", 89, TOTAL, 152),
        "🔥 ${hours}h ${mins}m | ${claimed}/${total} deals");
    demo(
        7,
        "Tracking",
        Map.of("tracking", "1Z999AA", STATUS, "Out for Delivery", "stops", 3),
        "📦 ${tracking}: ${status} ${stops > 0 ? '(' += stops += ' stops)' : ''}");
    demo(
        8,
        "Reviews",
        Map.of("product", "Watch", "rating", 4.7, "reviews", 1523),
        "${product}: ⭐ ${rating}/5.0 (${reviews} reviews)");
    demo(
        9,
        "Price Drop",
        Map.of("item", "Laptop", "now", 1199.99, "save", 300.00),
        "💰 ${item}: $${now} (save $${save})");
    demo(
        10,
        "Return",
        Map.of("returnID", "RET-789", "refund", 79.99, "days", 3),
        "Return ${returnID}: $${refund} in ${days} days");
    demo(
        11,
        "Coupon",
        Map.of("code", "SAVE20", "discount", 20, "min", 100.00, "cart", 150.00, "valid", true),
        "Code ${code}: ${valid && cart >= min ? discount += '% OFF!' : 'Min $' += min}");
    demo(
        12,
        "Subscription",
        Map.of("service", "Premium", PRICE, 9.99, "autoRenew", true),
        "${service}: $${price}/mo ${autoRenew ? '(Auto-renew)' : ''}");
  }

  public static void healthcare() {
    log.info("\n=== HEALTHCARE (13-24) ===\n");
    demo(
        13,
        "Appointment",
        Map.of(PATIENT, "Jane", "doctor", "Dr. Smith", "date", "Jan 21"),
        "Reminder: ${patient} with ${doctor} on ${date}");
    demo(
        14,
        "Prescription",
        Map.of("med", "Lisinopril", "refills", 2),
        "${med}: ${refills} ${refills == 1 ? 'refill' : 'refills'}");
    demo(
        15,
        "Lab Results",
        Map.of("test", "Blood Count", "abnormal", 0),
        "${test}: ${abnormal > 0 ? '⚠ ' += abnormal += ' abnormal' : '✓ Normal'}");
    demo(
        16,
        "Vitals",
        Map.of("systolic", 128, "diastolic", 82, "hr", 72),
        "BP ${systolic}/${diastolic} ${systolic > 140 ? '⚠' : '✓'}, HR ${hr}");
    demo(
        17,
        "Insurance",
        Map.of("type", "PPO", "met", 800.00, TOTAL, 1500.00),
        "${type}: Deductible $${met}/$${total}");
    demo(
        18,
        "Drug Alert",
        Map.of("d1", "Warfarin", "d2", "Aspirin", "sev", "High"),
        "${sev == 'High' ? '🔴' : '🟡'}: ${d1} + ${d2}");
    demo(
        19,
        "Vaccine",
        Map.of("vaccine", "COVID Booster", "given", "Nov 15", "next", "May 15"),
        "${vaccine}: Given ${given}, Next ${next}");
    demo(
        20,
        "Bed Capacity",
        Map.of("dept", "ICU", "avail", 3, TOTAL, 20, "occ", 85.0),
        "${dept}: ${avail}/${total} (${occ}%)${occ >= 90 ? ' ⚠' : ''}");
    demo(
        21,
        "Telehealth",
        Map.of(PATIENT, "John", "doctor", "Dr. Lopez", "time", "3 PM"),
        "${patient} with ${doctor} at ${time}");
    demo(
        22,
        "Billing",
        Map.of("charges", 1250.00, "ins", 875.00, PATIENT, 375.00),
        "Charges $${charges}, Ins $${ins}, You $${patient}");
    demo(
        23,
        "Allergy",
        Map.of(PATIENT, "Sarah", "allergy", "Penicillin", "sev", "Severe"),
        "⚠ ${patient}: ${sev} ${allergy} allergy");
    demo(
        24,
        "Surgery",
        Map.of("proc", "Appendectomy", "surgeon", "Dr. Williams", "date", "Jan 25"),
        "${proc} with ${surgeon} on ${date}");
  }

  public static void finance() {
    log.info("\n=== FINANCE (25-36) ===\n");
    demo(
        25,
        "Balance",
        Map.of("acct", "****1234", "bal", 85.47, "thresh", 100.00),
        "Acct ${acct}: $${bal} ${bal < thresh ? '⚠ LOW' : '✓'}");
    demo(
        26,
        "Transaction",
        Map.of("merchant", "Amazon", "amt", 89.99, "bal", 433.48),
        "$${amt} at ${merchant} - Balance: $${bal}");
    demo(
        27,
        "Loan",
        Map.of("payment", 435.50, "due", "Jan 26", REMAINING, 29),
        "Payment $${payment} due ${due} (${remaining} left)");
    demo(
        28,
        "Credit Card",
        Map.of("bal", 2150.75, "min", 65.00, "limit", 10000.00),
        "Balance $${bal}, Min $${min}, Limit $${limit}");
    demo(
        29,
        "Portfolio",
        Map.of("value", 125750.00, "change", -850.00),
        "$${value} ${change >= 0 ? '🟢 +' : '🔴 '}$${change}");
    demo(
        30,
        "Wire",
        Map.of("amt", 5000.00, "recipient", "Acme", "fee", 25.00),
        "Wire $${amt} to ${recipient}, Fee $${fee}");
    demo(
        31,
        "Fraud",
        Map.of("amt", 1500.00, "country", "Romania", "blocked", true),
        "🚨 ${blocked ? 'BLOCKED' : 'APPROVED'}: $${amt} from ${country}");
    demo(
        32,
        "Savings",
        Map.of("goal", "Vacation", CURRENT, 3250.00, "target", 5000.00, "pct", 65.0),
        "${goal}: $${current}/$${target} (${pct}%)${pct >= 80 ? ' 🎯' : ''}");
    demo(
        33,
        "Mortgage",
        Map.of("amt", 350000.00, "rate", 6.75, "payment", 2270.28),
        "$${amt} @ ${rate}% = $${payment}/mo");
    demo(
        34,
        "Trade",
        Map.of("symbol", "AAPL", "action", "BUY", SHARES, 50, PRICE, 185.50),
        "${action} ${shares} ${symbol} @ $${price}");
    demo(
        35,
        "Dividend",
        Map.of("symbol", "MSFT", SHARES, 100, "perShare", 0.68, TOTAL, 68.00),
        "${symbol}: ${shares} × $${perShare} = $${total}");
    demo(
        36,
        "ATM",
        Map.of("amt", 200.00, "fee", 3.00, "bal", 523.47),
        "Withdrawal $${amt} ${fee > 0 ? '+ $' += fee : ''}, Balance $${bal}");
  }

  public static void education() {
    log.info("\n=== EDUCATION (37-48) ===\n");
    demo(
        37,
        "Enrollment",
        Map.of(STUDENT, "Emily", COURSE, "CS-101", "credits", 3),
        "${student} enrolled in ${course} (${credits} cr)");
    demo(
        38,
        "Grades",
        Map.of(COURSE, "Math", "grade", "A-", "gpa", 3.7),
        "${course}: ${grade} (${gpa} GPA)");
    demo(
        39,
        "Assignment",
        Map.of("name", "Paper", "due", "Jan 23", "days", 6),
        "${name} due ${due} ${days <= 2 ? '⚠ SOON!' : ''}");
    demo(
        40,
        "Scholarship",
        Map.of(STUDENT, "Michael", "amt", 10000.00, "renewable", true),
        "${student}: $${amt} ${renewable ? '(Renewable)' : ''}");
    demo(
        41,
        "Attendance",
        Map.of(STUDENT, "Jessica", "attended", 27, TOTAL, 30, "pct", 90.0),
        "${student}: ${attended}/${total} (${pct}%)${pct < 80 ? ' ⚠' : ' ✓'}");
    demo(
        42,
        "Library",
        Map.of("title", "Java Book", "due", "Jan 22", "renewals", 1),
        "'${title}' due ${due}${renewals > 0 ? ' (' += renewals += ' renewal)' : ''}");
    demo(
        43,
        "Exam",
        Map.of(COURSE, "Physics", "date", "Jan 28", "room", "Hall A"),
        "${course} Exam: ${date} in ${room}");
    demo(
        44,
        "Tuition",
        Map.of(TOTAL, 16500.00, "paid", 10000.00, "due", "Feb 1"),
        "Total $${total}, Paid $${paid}, Due $${total - paid} by ${due}");
    demo(
        45,
        "Study Group",
        Map.of(COURSE, "Chem", "date", "Jan 18", MEMBERS, 5),
        "${course} Study Group: ${date} (${members} members)");
    demo(
        46,
        "Cancelled",
        Map.of(COURSE, "History", REASON, "Illness", "makeup", "Jan 24"),
        "CANCELLED: ${course} (${reason}). Makeup: ${makeup}");
    demo(
        47,
        "Dean's List",
        Map.of(STUDENT, "Amanda", "gpa", 3.9),
        "🏆 ${student} - Dean's List (${gpa} GPA)");
    demo(
        48,
        "Office Hours",
        Map.of("prof", "Dr. Anderson", "days", "Mon/Wed", "time", "2-4 PM"),
        "${prof}: ${days} ${time}");
  }

  public static void travel() {
    log.info("\n=== TRAVEL (49-60) ===\n");
    demo(
        49,
        "Flight",
        Map.of("from", "LAX", "to", "JFK", "date", "Jan 25", "seat", "12A"),
        "${from} → ${to} on ${date}, Seat ${seat}");
    demo(
        50,
        "Hotel",
        Map.of("hotel", "Grand Plaza", "nights", 3, "rate", 199.00),
        "${hotel}: ${nights} nights @ $${rate}/night");
    demo(
        51,
        "Delay",
        Map.of("flight", "UA1234", "scheduled", "10 AM", "revised", "11:30 AM"),
        "Flight ${flight}: Delayed ${scheduled} → ${revised}");
    demo(
        52,
        "Baggage",
        Map.of("tag", "LAX123", "carousel", "3", STATUS, "Arrived"),
        "Bag ${tag}: ${status} at Carousel ${carousel}");
    demo(
        53,
        "Car Rental",
        Map.of("vehicle", "Toyota Camry", "rate", 59.99),
        "${vehicle} @ $${rate}/day");
    demo(
        54,
        "Boarding",
        Map.of("passenger", "John", "flight", "AA456", "gate", "B12", "group", "3"),
        "${passenger} - ${flight} Gate ${gate}, Group ${group}");
    demo(
        55,
        "Insurance",
        Map.of("coverage", 50000.00, "premium", 75.00),
        "Coverage $${coverage}, Premium $${premium}");
    demo(56, "Visa", Map.of("country", "France", STATUS, "Approved"), "${country} visa: ${status}");
    demo(
        57,
        "Lounge",
        Map.of("lounge", "United Club", "guests", 1),
        "${lounge}${guests > 0 ? ' + ' += guests += ' guest(s)' : ''}");
    demo(
        58,
        "Exchange",
        Map.of("from", "USD", "to", "EUR", "amt", 500.00, TOTAL, 460.00),
        "$${amt} ${from} → €${total} ${to}");
    demo(
        59,
        "Tour",
        Map.of("tour", "City Tour", "pax", 2, PRICE, 80.00),
        "${tour}: ${pax} people @ $${price}");
    demo(
        60,
        "Cruise",
        Map.of("ship", "Explorer", "nights", 7, "cabin", "Balcony"),
        "${ship}: ${nights} nights, ${cabin}");
  }

  public static void socialMedia() {
    log.info("\n=== SOCIAL MEDIA (61-72) ===\n");
    demo(
        61,
        "Post Stats",
        Map.of("likes", 1250, "comments", 89, SHARES, 156),
        "${likes} likes, ${comments} comments, ${shares} shares");
    demo(
        62,
        "Follower",
        Map.of("user", "@techguru", "followers", 50125, "verified", true),
        "${user} ${verified ? '✓' : ''} (${followers} followers)");
    demo(
        63,
        "Comment",
        Map.of("user", "@alice", "comment", "Nice!", "time", "5 min ago"),
        "${user}: '${comment}' - ${time}");
    demo(
        64,
        "Live",
        Map.of("streamer", "@gamer", "viewers", 3421, "duration", 45),
        "LIVE: ${streamer} | ${viewers} viewers | ${duration} min");
    demo(
        65,
        "Trending",
        Map.of("hashtag", "#TechNews", "tweets", 125000, "rank", 3),
        "#${rank}: ${hashtag} (${tweets} tweets)");
    demo(
        66,
        "Story",
        Map.of("poster", "@fashion", "views", 8456, "expires", "18h"),
        "Story by ${poster}: ${views} views, expires ${expires}");
    demo(
        67,
        "Video Upload",
        Map.of("title", "Tutorial", PROGRESS, 67),
        "Uploading '${title}' - ${progress}%");
    demo(
        68,
        "DM",
        Map.of("from", "@bob", "preview", "Hey!", "unread", 3),
        "${from}: '${preview}'${unread > 1 ? ' (+' += (unread-1) += ')' : ''}");
    demo(
        69,
        "Mention",
        Map.of("user", "@charlie", "type", "reply"),
        "${user} mentioned you in a ${type}");
    demo(
        70,
        "Group",
        Map.of("group", "Tech Enthusiasts", MEMBERS, 1567),
        "Invited to '${group}' (${members} members)");
    demo(
        71,
        "Profile Views",
        Map.of("today", 45, "week", 312, "change", 15.3),
        "${today} today, ${week} week ${change >= 0 ? '(+' += change += '%)' : ''}");
    demo(
        72,
        "Report",
        Map.of("contentID", "POST-999", REASON, "Spam", STATUS, "Review"),
        "${contentID} reported for ${reason} - ${status}");
  }

  public static void iot() {
    log.info("\n=== IOT/SMART HOME (73-84) ===\n");
    demo(
        73,
        "Thermostat",
        Map.of(CURRENT, 72, "target", 70, "humidity", 45),
        "${current}°F ${current > target ? '↓' : '↑'} to ${target}°F | ${humidity}%");
    demo(
        74,
        "Camera",
        Map.of("camera", "Front Door", "event", "Motion", "confidence", 95),
        "📷 ${camera}: ${event} (${confidence}%)");
    demo(
        75,
        "Smart Lock",
        Map.of(LOCATION, "Main", "action", "Unlocked", "user", "John", BATTERY, 45),
        "🔐 ${location}: ${action} by ${user}${battery < 20 ? ' ⚠ ' += battery += '%' : ''}");
    demo(
        76,
        "Lights",
        Map.of("room", "Living Room", "brightness", 75, "energy", 12),
        "💡 ${room}: ${brightness}% | ${energy}W");
    demo(
        77,
        "Garage",
        Map.of(STATUS, "Closed", "openedBy", "Sarah", "autoClose", true),
        "🚗 ${status} by ${openedBy}${autoClose ? ' (Auto)' : ''}");
    demo(
        78,
        "Smart Plug",
        Map.of("device", "Coffee Maker", "power", 1200, "runtime", 15),
        "🔌 ${device}: ${power}W | ${runtime} min");
    demo(
        79,
        "Leak Sensor",
        Map.of(LOCATION, "Basement", STATUS, "Normal", BATTERY, 85),
        "💧 ${location}: ${status} (Battery ${battery}%)");
    demo(
        80,
        "Air Quality",
        Map.of("aqi", 42, "co2", 450, STATUS, "Good"),
        "🌬 AQI ${aqi} (${status}), CO2 ${co2} ppm");
    demo(
        81,
        "Smoke Alarm",
        Map.of(LOCATION, "Kitchen", BATTERY, 90, "lastTest", "Jan 1"),
        "🔥 ${location}: ${battery}% battery, Tested ${lastTest}");
    demo(
        82,
        "Robot Vacuum",
        Map.of(STATUS, "Cleaning", BATTERY, 67, "area", 450),
        "🤖 ${status} | ${battery}% | ${area} sq ft");
    demo(
        83,
        "Doorbell",
        Map.of("visitor", "Package Delivery", "time", "10:30 AM", "recording", true),
        "🔔 ${visitor} at ${time}${recording ? ' (Recorded)' : ''}");
    demo(
        84,
        "Sprinkler",
        Map.of("zone", "Front Lawn", "duration", 30, "schedule", "6 AM daily"),
        "💦 ${zone}: ${duration} min | ${schedule}");
  }

  public static void gaming() {
    log.info("\n=== GAMING (85-96) ===\n");
    demo(
        85,
        "Achievement",
        Map.of("name", "Dragon Slayer", "points", 100, "rarity", "Rare"),
        "🏆 Achievement: ${name} (+${points} pts) [${rarity}]");
    demo(
        86,
        "Level Up",
        Map.of("player", "Phoenix", "level", 50, "xpNeeded", 12500),
        "⬆ ${player} reached Level ${level}! (${xpNeeded} XP to next)");
    demo(
        87,
        "Match Result",
        Map.of("result", "Victory", "score", 1850, "rank", "Gold III", "rp", 25),
        "${result}! Score ${score} | ${rank} (+${rp} RP)");
    demo(
        88,
        "Loot Drop",
        Map.of("item", "Legendary Sword", "rarity", "Legendary", "stats", "+150 DMG"),
        "✨ Loot: ${item} (${rarity}) | ${stats}");
    demo(
        89,
        "Quest Complete",
        Map.of("quest", "The Lost Temple", "reward", 5000, "bonus", true),
        "✅ Quest: ${quest} | +${reward} gold${bonus ? ' + BONUS!' : ''}");
    demo(
        90,
        "Team Invite",
        Map.of("team", "Elite Squad", "rank", "Diamond", MEMBERS, 4),
        "Invited to ${team} (${rank}, ${members}/5 members)");
    demo(
        91,
        "Tournament",
        Map.of("event", "Summer Championship", "starts", "Jan 20", "prize", 10000.00),
        "🎮 ${event}: Starts ${starts} | Prize $${prize}");
    demo(
        92,
        "Friend Request",
        Map.of("player", "ShadowNinja", "level", 45, "winRate", 68.5),
        "Friend request from ${player} (Lvl ${level}, ${winRate}% WR)");
    demo(
        93,
        "Daily Reward",
        Map.of("day", 7, "reward", "Epic Chest", "streak", 7),
        "Day ${day}: ${reward}${streak >= 7 ? ' 🔥 ' += streak += ' day streak!' : ''}");
    demo(
        94,
        "Battle Pass",
        Map.of("tier", 35, "maxTier", 100, REMAINING, 15),
        "Battle Pass: Tier ${tier}/${maxTier} (${remaining} days left)");
    demo(
        95,
        "PvP Stats",
        Map.of("kills", 1250, "deaths", 890, "kd", 1.40, "rank", "#342"),
        "K/D ${kd} (${kills}/${deaths}) | Rank ${rank}");
    demo(
        96,
        "Guild War",
        Map.of("guild", "Dragon Knights", "victories", 12, "ranking", 3),
        "Guild: ${guild} | ${victories} wins | #${ranking} ranking");
  }

  public static void hr() {
    log.info("\n=== HR/EMPLOYEE (97-108) ===\n");
    demo(
        97,
        "Job Application",
        Map.of("applicant", "Sarah Lee", "position", "Engineer", STATUS, "Interview"),
        "${applicant} - ${position}: ${status}");
    demo(
        98,
        "Interview",
        Map.of("candidate", "Mike Chen", "date", "Jan 22", "interviewer", "Jane Smith"),
        "Interview: ${candidate} with ${interviewer} on ${date}");
    demo(
        99,
        "Offer Letter",
        Map.of("name", "Alex Brown", "position", "Sr Developer", "salary", 120000.00),
        "Offer: ${name} - ${position} @ $${salary}");
    demo(
        100,
        "Onboarding",
        Map.of(EMPLOYEE, "Emma Davis", "startDate", "Feb 1", "tasks", 8, "completed", 5),
        "${employee} starts ${startDate}: ${completed}/${tasks} tasks");
    demo(
        101,
        "PTO Request",
        Map.of(EMPLOYEE, "Tom Wilson", "dates", "Jan 25-27", "days", 3, STATUS, "Approved"),
        "${employee}: ${days} days (${dates}) - ${status}");
    demo(
        102,
        "Performance",
        Map.of(EMPLOYEE, "Lisa Garcia", "rating", 4.5, "bonus", 5000.00),
        "${employee}: ${rating}/5.0 rating | $${bonus} bonus");
    demo(
        103,
        "Training",
        Map.of(COURSE, "Leadership", EMPLOYEE, "John Kim", PROGRESS, 75),
        "${course}: ${employee} - ${progress}% complete");
    demo(
        104,
        "Payroll",
        Map.of(EMPLOYEE, "Amy Zhang", "gross", 5000.00, "net", 3750.00),
        "${employee}: Gross $${gross}, Net $${net}");
    demo(
        105,
        "Promotion",
        Map.of(EMPLOYEE, "Chris Lee", "from", "Jr Dev", "to", "Dev", "raise", 15.0),
        "🎉 ${employee}: ${from} → ${to} (+${raise}%)");
    demo(
        106,
        "Benefits",
        Map.of(EMPLOYEE, "Maria Lopez", "plan", "Premium", "cost", 350.00),
        "${employee}: ${plan} plan @ $${cost}/mo");
    demo(
        107,
        "Time Off",
        Map.of(EMPLOYEE, "David Kim", "pto", 15, "sick", 5, "used", 8),
        "${employee}: PTO ${pto}, Sick ${sick}, Used ${used}");
    demo(
        108,
        "Exit Interview",
        Map.of(EMPLOYEE, "Rachel Green", "lastDay", "Jan 31", REASON, "New opportunity"),
        "${employee}: Last day ${lastDay} (${reason})");
  }

  public static void logistics() {
    log.info("\n=== LOGISTICS (109-120) ===\n");
    demo(
        109,
        "Shipment",
        Map.of("shipment", "SHP-1234", STATUS, "In Transit", "eta", "Jan 20"),
        "📦 ${shipment}: ${status}, ETA ${eta}");
    demo(
        110,
        "Warehouse",
        Map.of(LOCATION, "Warehouse A", "capacity", 10000, CURRENT, 8500, "util", 85.0),
        "${location}: ${current}/${capacity} (${util}%)${util >= 90 ? ' ⚠' : ''}");
    demo(
        111,
        "Route",
        Map.of("driver", "John", "stops", 12, "completed", 7, REMAINING, 5),
        "${driver}: ${completed}/${stops} stops (${remaining} remaining)");
    demo(
        112,
        "Inventory",
        Map.of("sku", "PROD-789", "onHand", 250, "allocated", 100, "available", 150),
        "SKU ${sku}: ${available} available (${onHand} on hand, ${allocated} allocated)");
    demo(
        113,
        "Load Plan",
        Map.of("truck", "TRK-45", "capacity", 20000, "loaded", 18500, "pct", 92.5),
        "${truck}: ${loaded}/${capacity} lbs (${pct}%)");
    demo(
        114,
        "Dock Door",
        Map.of("door", "Door 5", STATUS, "Loading", "shipment", "SHP-999", PROGRESS, 65),
        "${door}: ${status} ${shipment} (${progress}%)");
    demo(
        115,
        "Carrier",
        Map.of("carrier", "FedEx", "performance", 97.5, "onTime", 142, TOTAL, 146),
        "${carrier}: ${performance}% (${onTime}/${total} on-time)");
    demo(
        116,
        "Returns",
        Map.of("returnID", "RTN-456", REASON, "Damaged", STATUS, "Processing"),
        "Return ${returnID}: ${reason} - ${status}");
    demo(
        117,
        "Cross Dock",
        Map.of("inbound", "SHP-111", "outbound", "SHP-222", "transfer", 45),
        "Cross-dock: ${inbound} → ${outbound} (${transfer} min)");
    demo(
        118,
        "Manifest",
        Map.of("manifest", "MAN-789", "orders", 45, "weight", 850, "pallets", 12),
        "${manifest}: ${orders} orders, ${pallets} pallets, ${weight} lbs");
    demo(
        119,
        "Cycle Count",
        Map.of(LOCATION, "Aisle 12", "expected", 500, "counted", 498, "variance", -2),
        "${location}: ${counted}/${expected} ${variance != 0 ? '(Var: ' += variance += ')' : '✓'}");
    demo(
        120,
        "Labor",
        Map.of("shift", "Day", "scheduled", 20, "present", 18, "productivity", 95.0),
        "${shift} shift: ${present}/${scheduled} workers (${productivity}% productivity)");
  }
}
