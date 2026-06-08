package campidelli.liverecpi.mixer.ports.inbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.ChannelMeterLevel;

public interface GetMeterLevelsUseCase {
    List<ChannelMeterLevel> execute(GetMeterLevelsCommand command);
}