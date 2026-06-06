package campidelli.liverecpi.domain.mixer;

import java.util.List;

public record OnlineDevice(
    List<String> signatureParts,
    String ipAddress,
    Integer port) {

    public String signature() {
        return String.join("|", signatureParts);
    }
}
