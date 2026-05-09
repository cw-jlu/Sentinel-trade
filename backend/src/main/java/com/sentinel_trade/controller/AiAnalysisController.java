package com.sentinel_trade.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/analysis")
public class AiAnalysisController {

    private final RestTemplate restTemplate;

    @Value("${ai.qwen.api-key:}")
    private String apiKey;

    public AiAnalysisController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @PostMapping("/qwen")
    public ResponseEntity<Map<String, Object>> analyze(@RequestBody Map<String, Object> requestBody) {
        if (apiKey == null || apiKey.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Qwen API key is not configured."));
        }

        String symbol = (String) requestBody.get("symbol");
        String marketData = (String) requestBody.get("marketData");

        String prompt = "You are a professional quantitative financial analyst. " +
                "Please analyze the following recent market data for " + symbol + " and provide a brief, insightful trading summary " +
                "(focus on trend, volume, VWAP divergence, and potential risks). Keep it under 150 words.\n\n" +
                "Market Data:\n" + marketData;

        String url = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");

        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are a professional quantitative financial analyst. Output your analysis in Markdown format. Keep it concise.");

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);

        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(systemMessage);
        messages.add(userMessage);

        Map<String, Object> body = new HashMap<>();
        body.put("model", "qwen-plus");
        body.put("messages", messages);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, entity, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && responseBody.containsKey("choices")) {
                List<Map<String, Object>> choices = (List<Map<String, Object>>) responseBody.get("choices");
                if (!choices.isEmpty()) {
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String content = (String) message.get("content");
                    return ResponseEntity.ok(Map.of("analysis", content));
                }
            }
            return ResponseEntity.status(500).body(Map.of("error", "Failed to parse response from Qwen."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).body(Map.of("error", "Error calling Qwen API: " + e.getMessage()));
        }
    }
}
