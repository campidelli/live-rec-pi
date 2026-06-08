package campidelli.liverecpi.mixer.ports.outbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.ChannelMeterLevel;

public interface FetchMixerMeterLevelsPort {
    String mixerType();

    List<ChannelMeterLevel> fetchMeterLevels(String ipAddress, int port);
}
