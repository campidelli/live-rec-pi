package campidelli.liverecpi;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@MicronautTest
class GreetingControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Test
    void returnsGreetingMessage() {
        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.GET("/hello/world"),
            String.class
        );

        assertEquals(200, response.getStatus().getCode());
        assertEquals("{\"message\":\"Hello, world!\"}", response.body());
    }
}
