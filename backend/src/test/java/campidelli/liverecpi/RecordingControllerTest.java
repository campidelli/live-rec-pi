package campidelli.liverecpi;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class RecordingControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void returnsMixerInfo() {
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET("/recording/mixer"),
            String.class
        );

        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.body().contains("\"name\":\"XR18\""));
    }

    @Test
    void startsRecordingSession() {
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.POST("/recording/start?projectName=live-set", ""),
            String.class
        );

        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.body().contains("\"projectName\":\"live-set\""));
        assertTrue(response.body().contains("\"sessionId\":"));
        assertTrue(response.body().contains("\"storagePath\":"));
    }
}
