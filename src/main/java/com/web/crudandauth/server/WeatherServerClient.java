package com.web.crudandauth.server;

import com.web.crudandauth.exceptionHandler.WeatherServerException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WeatherServerClient {
    private final RestTemplate restTemplate;

    private final String weatherUrl = "http://localhost:8082/api/weather";

    public WeatherResult getWeather(String city) {
        String url = weatherUrl + "?city=" + city;
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<?, ?> body = response.getBody();
                String location = body.get("location") != null ? body.get("location").toString() : "Unknown";
                double temperature = body.get("temperature") != null ? ((Number) body.get("temperature")).doubleValue() : 0.0;
                String time = body.get("time") != null ? body.get("time").toString() : LocalDateTime.now().toString();

                log.info("Weather fetched -> city = {}, location = {} , temperature = {} ", city, location, temperature);
                return new WeatherResult(location, temperature, time);
            }
            throw new WeatherServerException("Unexpected response from weather server");
        } catch (ResourceAccessException ex) {
            log.error("Weather server unreachable at {}", weatherUrl);
            throw new WeatherServerException("Weather server is not reachable. Make sure it is running on port 8082.");
        } catch (WeatherServerException ex) {
            throw ex;
        } catch (Exception ex) {
            log.error("Failed to fetch weather: {}", ex.getMessage());
            throw new WeatherServerException("Failed to fetch weather: " + ex.getMessage());
        }
    }

    public record WeatherResult(String location, double temperature, String time) {
    }
}
