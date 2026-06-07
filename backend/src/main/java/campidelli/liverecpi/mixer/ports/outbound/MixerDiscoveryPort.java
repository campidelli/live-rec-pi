package campidelli.liverecpi.mixer.ports.outbound;

import java.util.List;

import campidelli.liverecpi.mixer.domain.model.DiscoveredMixer;

public interface MixerDiscoveryPort {
    List<DiscoveredMixer> discover();
}