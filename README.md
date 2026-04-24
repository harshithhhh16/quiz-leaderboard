# Quiz Leaderboard System
### Bajaj Finserv Health — SRM Internship Assignment

---

## Problem Statement

Build an application that:
1. Polls a quiz validator API **10 times** (poll index 0–9)
2. **Deduplicates** events using `roundId + participant` as a composite key
3. Aggregates scores per participant
4. Submits a **single** correct leaderboard (sorted by total score, descending)

---

## Solution Approach

| Step | Description |
|------|-------------|
| **Poll** | `GET /quiz/messages?regNo=<REG>&poll=<0..9>` — 5 s mandatory delay between calls |
| **Deduplicate** | Composite key `roundId|participant` stored in a `HashSet`; any repeat is skipped |
| **Aggregate** | `Map<String, Integer>` accumulates total score per participant |
| **Sort** | Leaderboard sorted descending by `totalScore` |
| **Submit** | Single `POST /quiz/submit` with the final leaderboard |

### Why deduplication matters

The same API response data can appear across multiple polls. Processing duplicates
inflates scores and produces a wrong leaderboard.

```
Poll 0 → Alice R1 +10   ✓ accepted   (key = "R1|Alice", new)
Poll 3 → Alice R1 +10   ✗ ignored    (key = "R1|Alice", already seen)
Correct total for Alice = 10
```

---

## Tech Stack

- **Java 17** (standard library `java.net.http.HttpClient`)
- **Jackson 2.17** (JSON parsing / building)
- **Maven** (build + fat JAR via maven-shade-plugin)

---

## Project Structure

```
quiz-leaderboard/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── com/bajaj/quiz/
                └── QuizLeaderboard.java
```

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### 1. Clone the repo
```bash
git clone https://github.com/your-username/quiz-leaderboard.git
cd quiz-leaderboard
```

### 2. Set your registration number
Open `src/main/java/com/bajaj/quiz/QuizLeaderboard.java` and replace:
```java
private static final String REG_NO = "YOUR_REG_NO_HERE";
```
with your actual SRM registration number (e.g., `"RA2211003010001"`).

### 3. Build
```bash
mvn clean package -q
```

### 4. Run
```bash
java -jar target/quiz-leaderboard.jar
```

The program will:
- Poll 10 times with 5-second intervals (~45 seconds total)
- Print a live leaderboard as events are processed
- Auto-submit once all polls are complete
- Print the API's `isCorrect` / `expectedTotal` response

### Sample Output

```
[Poll 0/9] Fetching...
  ✓ HTTP 200
  + Alice scored 10 in R1
  + Bob scored 20 in R1
  ⏱ Waiting 5 s before next poll...

[Poll 1/9] Fetching...
  ✓ HTTP 200
  ⚠ Duplicate ignored → R1|Alice
  ⚠ Duplicate ignored → R1|Bob
  ⏱ Waiting 5 s before next poll...
...

════════════ LEADERBOARD ════════════
Participant          Total Score
─────────────────────────────────────
Bob                  120
Alice                100
─────────────────────────────────────
GRAND TOTAL          220

Submission response (HTTP 200):
{"isCorrect":true,"isIdempotent":true,"submittedTotal":220,"expectedTotal":220,"message":"Correct!"}
```

---

## Key Design Decisions

1. **`HashSet<String>` for deduplication** — O(1) lookups, simple and reliable.
2. **`Map.merge()`** for score accumulation — concise and correct.
3. **Single HTTP client instance** — reused across all requests for efficiency.
4. **Submit only once** — no retry loop; the submission happens after all polls complete.

---

*Submitted by: [Your Name] — [Your Reg No] — SRM IST, Kattankulathur*
