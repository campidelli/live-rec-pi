package campidelli.liverecpi.mixer.adapters.inbound.websocket;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import campidelli.liverecpi.mixer.adapters.inbound.websocket.dto.MeterLevelResponse;
import campidelli.liverecpi.mixer.adapters.inbound.websocket.dto.MeterSnapshotResponse;
import campidelli.liverecpi.mixer.domain.model.ChannelMeterLevel;
import campidelli.liverecpi.mixer.ports.inbound.GetMeterLevelsCommand;
import campidelli.liverecpi.mixer.ports.inbound.GetMeterLevelsUseCase;
import io.micronaut.websocket.WebSocketSession;
import io.micronaut.websocket.annotation.OnClose;
import io.micronaut.websocket.annotation.OnError;
import io.micronaut.websocket.annotation.OnMessage;
import io.micronaut.websocket.annotation.OnOpen;
import io.micronaut.websocket.annotation.ServerWebSocket;
import io.micronaut.websocket.exceptions.WebSocketSessionException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ServerWebSocket("/mixers/meters/{type}/{ipAddress}/{port}")
public class MeterLevelsWebSocket {

    private static final Logger log = LoggerFactory.getLogger(MeterLevelsWebSocket.class);
    private static final long STREAM_INTERVAL_MS = 250L;

    private final GetMeterLevelsUseCase getMeterLevelsUseCase;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final Map<String, ScheduledFuture<?>> sessionJobs = new ConcurrentHashMap<>();

    public MeterLevelsWebSocket(GetMeterLevelsUseCase getMeterLevelsUseCase) {
        this.getMeterLevelsUseCase = getMeterLevelsUseCase;
    }

    @OnOpen
    public void onOpen(String type, String ipAddress, int port, WebSocketSession session) {
        log.info("Meter WS opened: session={}, mixerType={}, address={}:{}", session.getId(), type, ipAddress, port);
        GetMeterLevelsCommand command = new GetMeterLevelsCommand(type, ipAddress, port);

        ScheduledFuture<?> streamJob = scheduler.scheduleWithFixedDelay(() -> pushSnapshot(session, command),
                0,
                STREAM_INTERVAL_MS,
                TimeUnit.MILLISECONDS);
        sessionJobs.put(session.getId(), streamJob);
    }

    @OnClose
    public void onClose(WebSocketSession session) {
        log.info("Meter WS closed: session={}", session.getId());
        stopStreaming(session.getId());
    }

    @OnMessage
    public void onMessage(String ignoredPayload, WebSocketSession session) {
        // Streaming is server-driven; incoming messages are intentionally ignored.
    }

    @OnError
    public void onError(WebSocketSession session, Throwable error) {
        log.debug("WebSocket meter stream error on session {}", session.getId(), error);
        stopStreaming(session.getId());
    }

    @PreDestroy
    void shutdown() {
        sessionJobs.values().forEach(job -> job.cancel(true));
        scheduler.shutdownNow();
    }

    private void pushSnapshot(WebSocketSession session, GetMeterLevelsCommand command) {
        if (!session.isOpen()) {
            stopStreaming(session.getId());
            return;
        }

        try {
            List<ChannelMeterLevel> levels = getMeterLevelsUseCase.execute(command);
            MeterSnapshotResponse payload = new MeterSnapshotResponse(
                    command.type(),
                    command.ipAddress(),
                    command.port(),
                    Instant.now(),
                    levels.stream().map(MeterLevelResponse::fromDomain).toList());
            session.sendSync(payload);
        } catch (WebSocketSessionException exception) {
            log.info("Meter WS send skipped because session is closed: session={}", session.getId());
            stopStreaming(session.getId());
        } catch (Throwable exception) {
            log.warn("Failed to stream meter levels for {}@{}:{}", command.type(), command.ipAddress(), command.port(),
                    exception);
        }
    }

    private void stopStreaming(String sessionId) {
        ScheduledFuture<?> streamJob = sessionJobs.remove(sessionId);
        if (streamJob != null) {
            streamJob.cancel(true);
        }
    }
}