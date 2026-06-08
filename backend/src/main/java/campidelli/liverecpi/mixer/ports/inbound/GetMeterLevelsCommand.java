package campidelli.liverecpi.mixer.ports.inbound;

public record GetMeterLevelsCommand(
        String type,
        String ipAddress,
        int port) {
}