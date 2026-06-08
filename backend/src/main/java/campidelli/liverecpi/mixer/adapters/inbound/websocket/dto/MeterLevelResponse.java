package campidelli.liverecpi.mixer.adapters.inbound.websocket.dto;

import campidelli.liverecpi.mixer.domain.model.ChannelMeterLevel;
import io.micronaut.serde.annotation.Serdeable;

@Serdeable
public record MeterLevelResponse(
        int channelIndex,
        float level) {

    public static MeterLevelResponse fromDomain(ChannelMeterLevel meterLevel) {
        return new MeterLevelResponse(meterLevel.channelIndex(), meterLevel.level());
    }
}