package campidelli.liverecpi;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import campidelli.liverecpi.recording.domain.model.AudioSource;
import campidelli.liverecpi.recording.domain.model.RecordingOptions;
import campidelli.liverecpi.recording.domain.model.StartedRecording;
import campidelli.liverecpi.recording.domain.model.SupportedFormat;
import campidelli.liverecpi.recording.domain.service.RecordingService;
import campidelli.liverecpi.recording.ports.inbound.GetAudioSourcesUseCase;
import campidelli.liverecpi.recording.ports.inbound.GetRecordingOptionsUseCase;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingCommand;
import campidelli.liverecpi.recording.ports.inbound.StartRecordingUseCase;
import campidelli.liverecpi.recording.ports.inbound.StopRecordingUseCase;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@MicronautTest
class RecordingControllerTest {

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    StubRecordingUseCases stubRecordingUseCases;

    @Test
    void returnsRecordingOptions() {
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.GET("/recording/options"),
                String.class
        );
        String body = response.getBody(String.class).orElseThrow();

        assertEquals(200, response.getStatus().getCode());
        assertTrue(body.contains("\"sessionNamePrefix\":\"2026-06-08-\""));
        assertTrue(body.contains("\"extension\":\"wav\""));
    }

    @Test
    void returnsAudioSource() {
        HttpResponse<String> response = client.toBlocking().exchange(
                HttpRequest.GET("/recording/source/XR18"),
                String.class
        );
        String body = response.getBody(String.class).orElseThrow();

        assertEquals(200, response.getStatus().getCode());
        assertTrue(body.contains("\"id\":\"XR18\""));
        assertTrue(body.contains("\"availableInputChannels\":18"));
    }

    @Test
    void startsRecordingSession() {
        String requestBody = """
                {
                  "audioSourceId": "XR18",
                  "outputDir": "/tmp/live-set",
                  "format": "WAV",
                  "tracks": [
                    { "audioSourceIndex": 1, "name": "kick" },
                    { "audioSourceIndex": 4, "name": "snare" }
                  ]
                }
                """;

        HttpResponse<String> response = client.toBlocking().exchange(
            HttpRequest.POST("/recording", requestBody).contentType(MediaType.APPLICATION_JSON_TYPE),
                String.class
        );
        String body = response.getBody(String.class).orElseThrow();

        assertEquals(200, response.getStatus().getCode());
        assertTrue(body.contains("\"audioSourceId\":\"XR18\""));
        assertTrue(body.contains("\"outputDir\":\"/tmp/live-set\""));
        assertTrue(body.contains("\"format\":\"WAV\""));
        assertTrue(body.contains("\"trackCount\":2"));
    }

    @Test
    void stopsRecordingSession() {
        stubRecordingUseCases.markActive("XR18");

        HttpResponse<?> response = client.toBlocking().exchange(
                HttpRequest.DELETE("/recording/XR18")
        );

        assertEquals(204, response.getStatus().getCode());
    }

    @Test
    void returnsNotFoundWhenStoppingUnknownRecording() {
        try {
            client.toBlocking().exchange(HttpRequest.DELETE("/recording/unknown"));
        } catch (HttpClientResponseException exception) {
            assertEquals(404, exception.getStatus().getCode());
            return;
        }

        throw new AssertionError("Expected 404 when stopping an unknown recording");
    }

    @Singleton
    @Replaces(RecordingService.class)
    static class StubRecordingUseCases implements GetRecordingOptionsUseCase, GetAudioSourcesUseCase,
            StartRecordingUseCase, StopRecordingUseCase {

        private final Set<String> activeRecordings = ConcurrentHashMap.newKeySet();

        @Override
        public RecordingOptions execute() {
            return new RecordingOptions(List.of(SupportedFormat.WAV), "2026-06-08-", "/tmp/recordings");
        }

        @Override
        public AudioSource getAudioSource(String mixerType) {
            return new AudioSource("XR18", mixerType, "XR18", "USB multitrack interface", 18);
        }

        @Override
        public StartedRecording execute(StartRecordingCommand command) {
            activeRecordings.add(command.audioSourceId());
            return new StartedRecording(
                    command.audioSourceId(),
                    command.outputDir(),
                    command.format(),
                    command.tracks().size());
        }

        @Override
        public boolean stop(String audioSourceId) {
            return activeRecordings.remove(audioSourceId);
        }

        void markActive(String audioSourceId) {
            activeRecordings.add(audioSourceId);
        }
    }
}
