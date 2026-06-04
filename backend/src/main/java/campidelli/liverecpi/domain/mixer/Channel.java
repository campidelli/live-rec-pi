package campidelli.liverecpi.domain.mixer;

public record Channel(
    int index,
    String name,
    String type,
    ChannelColor color
) {}