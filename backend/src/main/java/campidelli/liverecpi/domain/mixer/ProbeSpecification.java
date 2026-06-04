package campidelli.liverecpi.domain.mixer;

public record ProbeSpecification(
    int port,
    byte[] payload) {
}
