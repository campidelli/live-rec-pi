package campidelli.liverecpi.mixer.ports.outbound;

import campidelli.liverecpi.mixer.domain.model.MixerDescriptor;

public interface MixerRegistryPort {
    MixerDescriptor getDescriptor();
}