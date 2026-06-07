package campidelli.liverecpi.mixer.ports.inbound;

public record ListChannelsCommand(
        String type,
        String ipAddress,
        int port) {
}