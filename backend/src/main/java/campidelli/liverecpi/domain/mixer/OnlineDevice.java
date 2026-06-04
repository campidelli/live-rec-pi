package campidelli.liverecpi.domain.mixer;

public record OnlineDevice(
    String modelSignature,
    String ipAddress,
    Integer port) {
}
