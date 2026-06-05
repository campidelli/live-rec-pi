package campidelli.liverecpi.application.mixer;

import io.micronaut.serde.annotation.Serdeable;

@Serdeable
record ChannelDTO(
    int index,
    String name,
    String type,
    String color,
    boolean colorInverted) {
}