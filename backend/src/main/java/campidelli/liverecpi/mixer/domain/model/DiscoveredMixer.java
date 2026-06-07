package campidelli.liverecpi.mixer.domain.model;

import java.util.List;

public sealed interface DiscoveredMixer
        permits DiscoveredMixer.AvailableMixer, DiscoveredMixer.UnavailableMixer {

    MixerDescriptor descriptor();

    /**
     * An online mixer that the operator can successfully connect to.
     */
    record AvailableMixer(
            MixerDescriptor descriptor,
            List<String> signatureParts,
            MixerConnection connection) implements DiscoveredMixer {

        public String signature() {
            return String.join("|", signatureParts);
        }
    }

    /**
     * A supported mixer model that was NOT found active on the local network.
     */
    record UnavailableMixer(
            MixerDescriptor descriptor) implements DiscoveredMixer {
    }
}