package campidelli.liverecpi;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class MixerControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void listsAvailableMixersFromConfiguration() {
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET("/mixers"),
            String.class
        );

        assertEquals(200, response.getStatus().getCode());
        assertTrue(response.body().contains("\"id\":\"xr18\""));
        assertTrue(response.body().contains("\"name\":\"Behringer XR18\""));
    }

    @Test
    void connectsGetsAndDisconnectsMixer() {
        HttpResponse<String> connectResponse = client.toBlocking().exchange(
            HttpRequest.POST("/mixers/xr18", ""),
            String.class
        );
        assertEquals(200, connectResponse.getStatus().getCode());
        assertTrue(connectResponse.body().contains("\"connected\":true"));

        HttpResponse<String> getResponse = client.toBlocking().exchange(
            HttpRequest.GET("/mixers/xr18"),
            String.class
        );
        assertEquals(200, getResponse.getStatus().getCode());
        assertTrue(getResponse.body().contains("\"connected\":true"));

        HttpResponse<?> disconnectResponse = client.toBlocking().exchange(
            HttpRequest.DELETE("/mixers/xr18")
        );
        assertEquals(204, disconnectResponse.getStatus().getCode());

        HttpResponse<String> getAfterDisconnect = client.toBlocking().exchange(
            HttpRequest.GET("/mixers/xr18"),
            String.class
        );
        assertEquals(200, getAfterDisconnect.getStatus().getCode());
        assertTrue(getAfterDisconnect.body().contains("\"connected\":false"));
    }

    @Test
    void returnsNotFoundForUnknownMixer() {
        try {
            client.toBlocking().exchange(HttpRequest.GET("/mixers/unknown"), String.class);
        } catch (HttpClientResponseException e) {
            assertEquals(404, e.getStatus().getCode());
            assertTrue(e.getMessage().contains("Not Found"));
            return;
        }

        throw new AssertionError("Expected 404 for unknown mixer");
    }
}
