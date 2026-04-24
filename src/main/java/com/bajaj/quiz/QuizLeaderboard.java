package com.bajaj.quiz;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

public class QuizLeaderboard {

    // ── CONFIG ────────────────────────────────────────────────────────────────
    private static final String BASE_URL  = "https://devapigw.vidalhealthtpa.com/srm-quiz-task";
    private static final String REG_NO    ="RA2311003012361"; // ← replace with your actual reg number
    private static final int    TOTAL_POLLS = 10;
    private static final int    DELAY_MS    = 5_000;            // 5 s mandatory delay

    private static final HttpClient HTTP   = HttpClient.newHttpClient();
    private static final ObjectMapper JSON = new ObjectMapper();

    public static void main(String[] args) throws Exception {

        // Seen-set for deduplication  key = roundId + "|" + participant
        Set<String> seen = new HashSet<>();

        // Score accumulator per participant
        Map<String, Integer> scores = new LinkedHashMap<>();

        // ── STEP 1 : Poll 10 times ─────────────────────────────────────────
        for (int poll = 0; poll < TOTAL_POLLS; poll++) {

            System.out.printf("[Poll %d/%d] Fetching...%n", poll, TOTAL_POLLS - 1);

            String url = String.format("%s/quiz/messages?regNo=%s&poll=%d",
                                       BASE_URL, REG_NO, poll);

            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .header("Accept", "application/json")
                    .build();

            HttpResponse<String> resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString());

            if (resp.statusCode() != 200) {
                System.err.printf("  ✗ HTTP %d — body: %s%n", resp.statusCode(), resp.body());
            } else {
                System.out.printf("  ✓ HTTP 200%n");
                JsonNode root = JSON.readTree(resp.body());

                // ── STEP 2 : Deduplicate & aggregate ──────────────────────
                ArrayNode events = (ArrayNode) root.path("events");
                for (JsonNode event : events) {
                    String roundId     = event.path("roundId").asText();
                    String participant = event.path("participant").asText();
                    int    score       = event.path("score").asInt();

                    String dedupeKey = roundId + "|" + participant;

                    if (seen.contains(dedupeKey)) {
                        System.out.printf("  ⚠ Duplicate ignored → %s%n", dedupeKey);
                    } else {
                        seen.add(dedupeKey);
                        scores.merge(participant, score, Integer::sum);
                        System.out.printf("  + %s scored %d in %s%n", participant, score, roundId);
                    }
                }
            }

            // Mandatory 5-second delay between polls (skip after last poll)
            if (poll < TOTAL_POLLS - 1) {
                System.out.printf("  ⏱ Waiting %d s before next poll...%n%n", DELAY_MS / 1000);
                Thread.sleep(DELAY_MS);
            }
        }

        // ── STEP 3 : Build leaderboard (sorted descending by totalScore) ──
        List<Map.Entry<String, Integer>> leaderboard = new ArrayList<>(scores.entrySet());
        leaderboard.sort((a, b) -> b.getValue() - a.getValue());

        int grandTotal = leaderboard.stream().mapToInt(Map.Entry::getValue).sum();

        System.out.println("\n════════════ LEADERBOARD ════════════");
        System.out.printf("%-20s %s%n", "Participant", "Total Score");
        System.out.println("─────────────────────────────────────");
        for (Map.Entry<String, Integer> entry : leaderboard) {
            System.out.printf("%-20s %d%n", entry.getKey(), entry.getValue());
        }
        System.out.println("─────────────────────────────────────");
        System.out.printf("%-20s %d%n%n", "GRAND TOTAL", grandTotal);

        // ── STEP 4 : Build POST body ───────────────────────────────────────
        ObjectNode payload = JSON.createObjectNode();
        payload.put("regNo", REG_NO);

        ArrayNode lbArray = payload.putArray("leaderboard");
        for (Map.Entry<String, Integer> entry : leaderboard) {
            ObjectNode row = lbArray.addObject();
            row.put("participant", entry.getKey());
            row.put("totalScore",  entry.getValue());
        }

        String postBody = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        System.out.println("Submitting payload:");
        System.out.println(postBody);

        // ── STEP 5 : Submit once ───────────────────────────────────────────
        HttpRequest submitReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/quiz/submit"))
                .POST(HttpRequest.BodyPublishers.ofString(postBody))
                .header("Content-Type", "application/json")
                .header("Accept",       "application/json")
                .build();

        HttpResponse<String> submitResp = HTTP.send(submitReq, HttpResponse.BodyHandlers.ofString());

        System.out.printf("%nSubmission response (HTTP %d):%n%s%n",
                          submitResp.statusCode(), submitResp.body());
    }
}
