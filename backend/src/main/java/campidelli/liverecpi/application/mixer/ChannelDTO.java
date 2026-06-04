package campidelli.liverecpi.application.mixer;

import io.micronaut.core.annotation.Introspected;

@Introspected
record ChannelDTO(
    int index,
    String name,
    String type,
    String color,
    boolean colorInverted) {
}